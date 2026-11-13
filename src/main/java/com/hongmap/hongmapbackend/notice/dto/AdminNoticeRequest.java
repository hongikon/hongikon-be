package com.hongmap.hongmapbackend.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 공지 추가·수정 본문. 시각은 제보와 같이 ISO-8601(앱은 toISOString() 으로 보낸다, UTC).
 * publishAt 이 지금보다 앞이면 바로 보인다. popupUntil 을 비우면 팝업 없이 목록에만 둔다(popupText 는 무시).
 */
public record AdminNoticeRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해 주세요.") String title,
        @NotBlank(message = "본문을 입력해 주세요.")
        @Size(max = 5000, message = "본문은 5000자 이하로 입력해 주세요.") String body,
        @NotNull(message = "게시 시각을 입력해 주세요.") LocalDateTime publishAt,
        LocalDateTime popupUntil,
        @Size(max = 300, message = "팝업 문구는 300자 이하로 입력해 주세요.") String popupText
) {
}
