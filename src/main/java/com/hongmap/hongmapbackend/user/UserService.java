package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.apple.AppleRevocationService;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.bookmark.BookmarkRepository;
import com.hongmap.hongmapbackend.auth.oauth.KakaoUnlinkClient;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.notification.NotificationCategoryRepository;
import com.hongmap.hongmapbackend.notification.KeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserBoardSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import com.hongmap.hongmapbackend.user.retention.WithdrawRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 회원탈퇴는 하드 삭제로 처리한다. User는 연관 엔티티를 역참조로 들고 있지 않고
 * DB에도 FK cascade가 없다고 가정하므로, 자식 → 부모 순서로 명시적으로 지운다.
 * 제보(Report)는 다른 유저도 지도에서 보는 콘텐츠지만 ends_at이 지나면 어차피 사라지는
 * 시간 한정 정보라 작성자 탈퇴 시 함께 삭제한다(익명화 대신 삭제로 결정).
 * Apple 로그인 사용자는 커밋 뒤 Apple 토큰도 폐기한다(App Store 가이드라인 5.1.1(v)). 폐기 실패는 탈퇴를 막지 않고
 * 재시도 대기열(apple_pending_revocations)에 들어간다.
 * 정지 이력이 있거나 관리자가 삭제한(위반 확정) 제보가 있는 회원은 지우기 전에 부정 이용 방지 기록을 1년 분리 보관한다(WithdrawRetentionService,
 * 개인정보 처리방침). 그 밖의 회원은 아무것도 남지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    // users를 참조하는 테이블을 새로 만들면 withdraw에 정리 코드를 추가하고 UserWithdrawIntegrationTest에 데이터를 넣을 것.
    private final NotificationCategoryRepository notificationCategoryRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final ReportImageService reportImageService;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UserBoardSubscriptionRepository userBoardSubscriptionRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppleRevocationService appleRevocationService;
    // 탈퇴 기록(withdraw_retentions)은 users 를 FK 로 참조하지 않는다(rejoined_user_id 는 일반 컬럼 — 재탈퇴 시 서비스가 비움).
    private final WithdrawRetentionService withdrawRetentionService;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 부정 이용 방지 기록(정지 이력·관리자가 삭제한 위반 제보가 있는 회원만, 1년 분리 보관 — WithdrawRetentionService).
        // 제보를 지우기 전에 스냅숏을 떠야 하므로 맨 앞에서 부른다. 대상이 아니면 아무것도 남기지 않는다. 사진은 보관하지 않는다.
        withdrawRetentionService.retainOnWithdraw(user);

        // 제보 사진(S3)은 DB 에서 제보를 지우기 전에 키를 모아 두고, 커밋된 뒤 지운다(실패는 로그, 수명 주기 규칙이 마저 정리).
        List<String> imageKeys = reportRepository.findImageKeysByUserId(userId);
        imageKeys.forEach(reportImageService::deleteAfterCommit);
        if (!imageKeys.isEmpty()) {
            log.info("withdraw userId={} report images scheduled for deletion: {}", userId, imageKeys.size());
        }

        // 이 유저가 작성한 제보에 달린 신고 먼저, 그다음 제보 본문
        reportFlagRepository.deleteByReport_User_Id(userId);
        reportRepository.deleteByUser_Id(userId);
        // 이 유저가 남의 제보에 남긴 신고
        reportFlagRepository.deleteByUser_Id(userId);

        bookmarkRepository.deleteByUser_Id(userId);
        if (user.getSocialType() == SocialType.KAKAO) {
            kakaoUnlinkClient.unlinkAfterCommit(user.getSocialId()); // 커밋 뒤 카카오 연결 끊기(실패해도 탈퇴는 완료)
        }
        keywordSubscriptionRepository.deleteByUser_Id(userId);
        userBoardSubscriptionRepository.deleteByUser_Id(userId);
        userNotificationSettingRepository.deleteByUserId(userId);
        userDepartmentRepository.deleteByUser_Id(userId);
        userDeviceRepository.deleteByUserId(userId);
        // 분야 알림 설정(user_id NOT NULL FK). 빠뜨리면 알림 설정을 한 번이라도 바꾼 유저의 탈퇴가 FK 위반으로 실패한다.
        notificationCategoryRepository.deleteByUser_Id(userId);
        refreshTokenRepository.deleteByUser_Id(userId);
        // 문의(feedback)는 지우지 않고 작성자·연락처(contact)만 NULL로 비운다(처리방침: 내용만 남음).
        // 문의에 붙인 참고 사진은 탈퇴와 함께 지운다(내용만 남는다).
        feedbackRepository.findImageKeysByUserId(userId).stream()
                .flatMap(joined -> java.util.Arrays.stream(joined.split(",")))
                .filter(key -> !key.isBlank())
                .forEach(reportImageService::deleteAfterCommit);
        feedbackRepository.detachUser(userId);

        userRepository.delete(user);

        if (user.getSocialType() == SocialType.APPLE) {
            revokeAppleTokenAfterCommit(user.getAppleRefreshToken(), user.getAppleClientId());
        }
    }

    /** 삭제가 실제로 커밋된 뒤에만 Apple을 호출한다(롤백되면 Apple 연결은 그대로 둔다). 실패는 재시도 대기열에 넣는다. */
    private void revokeAppleTokenAfterCommit(String appleRefreshToken, String appleClientId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            appleRevocationService.revokeOrQueue(appleRefreshToken, appleClientId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                appleRevocationService.revokeOrQueue(appleRefreshToken, appleClientId);
            }
        });
    }
}
