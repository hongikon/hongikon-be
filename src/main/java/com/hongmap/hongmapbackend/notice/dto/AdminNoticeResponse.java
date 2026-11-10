package com.hongmap.hongmapbackend.notice.dto;

import java.time.LocalDateTime;

/**
 * 관리자 공지 한 건. status 는 SCHEDULED(게시 전)·PUBLISHED(앱에 보이는 중). 시각은 UTC(존 없음).
 */
public record AdminNoticeResponse(
        Long id,
        String title,
        String body,
        LocalDateTime publishAt,
        LocalDateTime popupUntil,
        String popupText,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
