package com.hongmap.hongmapbackend.bookmark;

import com.hongmap.hongmapbackend.bookmark.dto.BookmarkCreateRequest;
import com.hongmap.hongmapbackend.bookmark.dto.BookmarkListResponse;
import com.hongmap.hongmapbackend.bookmark.dto.BookmarkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 북마크 = README 원칙상 로그인 필요 범주. permitAll 불필요(anyRequest().authenticated()에 이미 걸림).
 */
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping("/users/me/bookmarks")
    public BookmarkListResponse getMyBookmarks(@AuthenticationPrincipal Long userId) {
        return bookmarkService.getMyBookmarks(userId);
    }

    @PostMapping("/users/me/bookmarks")
    public ResponseEntity<BookmarkResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BookmarkCreateRequest request
    ) {
        var response = bookmarkService.create(userId, request.newsId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/users/me/bookmarks/{newsId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long newsId
    ) {
        bookmarkService.delete(userId, newsId);
        return ResponseEntity.noContent().build();
    }
}
