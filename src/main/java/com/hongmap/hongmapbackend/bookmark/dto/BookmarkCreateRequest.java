package com.hongmap.hongmapbackend.bookmark.dto;

import jakarta.validation.constraints.NotNull;

public record BookmarkCreateRequest(
        @NotNull
        Long newsId
) {
}
