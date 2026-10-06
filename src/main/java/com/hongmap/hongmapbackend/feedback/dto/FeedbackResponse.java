package com.hongmap.hongmapbackend.feedback.dto;

import com.hongmap.hongmapbackend.feedback.Feedback;
import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 문의 목록의 한 줄. 작성자는 앱에 보이는 이름과 회원 번호로만 가리키고 로그인 닉네임 원문은 싣지 않는다
 * (개인정보 보호법 제3조 최소 처리 — 원문 열람은 GET /admin/users/{id}/login-name).
 * 비로그인 문의·탈퇴한 작성자의 문의는 userId 부터 모두 null.
 *
 * @param userNickname    구버전 관리자 화면 호환용. 예전엔 로그인 닉네임 원문이었지만 이제 userDisplayName 과 같은 값
 * @param userDisplayName 앱 닉네임, 없으면 가린 로그인 닉네임("홍**")
 * @param userMemberCode  공개 회원 번호(K7Q2M9XA4D)
 */
public record FeedbackResponse(
        Long id,
        String content,
        String contact,
        Long userId,
        String userNickname,
        String status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        String userDisplayName,
        String userMemberCode,
        /** 참고 사진 보기 URL(presigned GET, 1시간 유효). 없으면 빈 배열 */
        List<String> imageUrls
) {
    public static FeedbackResponse of(Feedback feedback) {
        return of(feedback, List.of());
    }

    public static FeedbackResponse of(Feedback feedback, List<String> imageUrls) {
        User user = feedback.getUser();
        String displayName = user != null ? user.getDisplayName() : null;
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getContent(),
                feedback.getContact(),
                user != null ? user.getId() : null,
                displayName,
                feedback.getStatus().name(),
                feedback.getCreatedAt(),
                feedback.getResolvedAt(),
                displayName,
                user != null ? user.getMemberCode() : null,
                imageUrls == null ? List.of() : imageUrls);
    }
}
