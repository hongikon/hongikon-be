package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.bookmark.BookmarkRepository;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.notification.KeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원탈퇴는 하드 삭제로 처리한다. User는 연관 엔티티를 역참조로 들고 있지 않고
 * DB에도 FK cascade가 없다고 가정하므로, 자식 → 부모 순서로 명시적으로 지운다.
 * 제보(Report)는 다른 유저도 지도에서 보는 콘텐츠지만 ends_at이 지나면 어차피 사라지는
 * 시간 한정 정보라 작성자 탈퇴 시 함께 삭제한다(익명화 대신 삭제로 결정).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;

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
        userDepartmentRepository.deleteByUser_Id(userId);
        userDeviceRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUser_Id(userId);

        userRepository.delete(user);
    }
}
