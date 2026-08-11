package com.hongmap.hongmapbackend.bookmark;

import com.hongmap.hongmapbackend.bookmark.dto.BookmarkListResponse;
import com.hongmap.hongmapbackend.bookmark.dto.BookmarkResponse;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId) {
        var bookmarks = bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(BookmarkResponse::of)
                .toList();
        return new BookmarkListResponse(bookmarks);
    }

    @Transactional
    public BookmarkResponse create(Long userId, Long newsId) {
        if (bookmarkRepository.existsByUser_IdAndNews_Id(userId, newsId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 북마크한 소식입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 소식입니다."));

        Bookmark saved = bookmarkRepository.save(
                Bookmark.builder().user(user).news(news).build()
        );

        return BookmarkResponse.of(saved);
    }

    @Transactional
    public void delete(Long userId, Long newsId) {
        Bookmark bookmark = bookmarkRepository.findByUser_IdAndNews_Id(userId, newsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "북마크하지 않은 소식입니다."));

        bookmarkRepository.delete(bookmark);
    }
}
