package com.hongmap.hongmapbackend.bookmark.dto;

import java.util.List;

public record BookmarkListResponse(
        List<BookmarkResponse> bookmarks
) {
}
