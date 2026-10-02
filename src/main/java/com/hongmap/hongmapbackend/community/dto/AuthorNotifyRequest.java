package com.hongmap.hongmapbackend.community.dto;

import jakarta.validation.constraints.NotNull;

/** 작성자의 "이 제보 알림" 켜기·끄기. */
public record AuthorNotifyRequest(@NotNull Boolean enabled) {
}
