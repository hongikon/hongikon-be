package com.hongmap.hongmapbackend.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * status: VISIBLE(복원 — 이미 공개 중이면 "검토 완료(유지)": 검토 시각만 남겨 이전 신고를 더 세지 않는다) / HIDDEN(숨김) / DELETED(삭제)
 *
 * @param reason 숨김·삭제 사유(선택, 200자까지). 작성자 알림(이용약관 제10조)에 그대로 실린다 — 개인정보·신고자 정보는 적지 않는다.
 *               DB 에는 남기지 않는다(관리자 요청 로그 ADMIN_AUDIT 에는 남지 않음, 알림에만 쓰임)
 */
public record CommentModerationRequest(
        @NotBlank(message = "바꿀 상태를 골라 주세요.")
        String status,
        @Size(max = 200, message = "사유는 200자까지 쓸 수 있어요.")
        String reason
) {
    public CommentModerationRequest(String status) {
        this(status, null);
    }
}
