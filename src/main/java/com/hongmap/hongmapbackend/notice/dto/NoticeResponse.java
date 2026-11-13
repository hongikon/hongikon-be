package com.hongmap.hongmapbackend.notice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * GET /notices 의 공지 하나. publishedAt 은 게시 시각(UTC, 존 없음 — 다른 API 와 같다).
 * popup 은 지금 앱을 켤 때 띄울 공지에만 붙는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoticeResponse(
        Long id,
        String title,
        String body,
        LocalDateTime publishedAt,
        Popup popup
) {
    /** text 는 팝업에 보일 요약, until 은 이 시각(UTC)까지 띄운다. */
    public record Popup(String text, LocalDateTime until) {
    }
}
