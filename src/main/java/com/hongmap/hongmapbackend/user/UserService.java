package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.apple.AppleRevocationService;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.bookmark.BookmarkRepository;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.notification.NotificationCategoryRepository;
import com.hongmap.hongmapbackend.notification.KeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserBoardSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원탈퇴는 하드 삭제로 처리한다. User는 연관 엔티티를 역참조로 들고 있지 않고
 * DB에도 FK cascade가 없다고 가정하므로, 자식 → 부모 순서로 명시적으로 지운다.
 * 제보(Report)는 다른 유저도 지도에서 보는 콘텐츠지만 ends_at이 지나면 어차피 사라지는
 * 시간 한정 정보라 작성자 탈퇴 시 함께 삭제한다(익명화 대신 삭제로 결정).
 * Apple 로그인 사용자는 커밋 뒤 Apple 토큰도 폐기한다(App Store 가이드라인 5.1.1(v)). 폐기 실패는 탈퇴를 막지 않고
 * 재시도 대기열(apple_pending_revocations)에 들어간다.
 */
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
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UserBoardSubscriptionRepository userBoardSubscriptionRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppleRevocationService appleRevocationService;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 이 유저가 작성한 제보에 달린 신고 먼저, 그다음 제보 본문
        reportFlagRepository.deleteByReport_User_Id(userId);
        reportRepository.deleteByUser_Id(userId);
        // 이 유저가 남의 제보에 남긴 신고
        reportFlagRepository.deleteByUser_Id(userId);

        bookmarkRepository.deleteByUser_Id(userId);
        keywordSubscriptionRepository.deleteByUser_Id(userId);
        userBoardSubscriptionRepository.deleteByUser_Id(userId);
        userNotificationSettingRepository.deleteByUserId(userId);
        userDepartmentRepository.deleteByUser_Id(userId);
        userDeviceRepository.deleteByUserId(userId);
        // 분야 알림 설정(user_id NOT NULL FK). 빠뜨리면 알림 설정을 한 번이라도 바꾼 유저의 탈퇴가 FK 위반으로 실패한다.
        notificationCategoryRepository.deleteByUser_Id(userId);
        refreshTokenRepository.deleteByUser_Id(userId);
        // 문의(feedback)는 지우지 않고 작성자·연락처(contact)만 NULL로 비운다(처리방침: 내용만 남음).
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
