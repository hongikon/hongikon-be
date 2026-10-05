package com.hongmap.hongmapbackend.bookmark;

import com.hongmap.hongmapbackend.bookmark.dto.BookmarkListResponse;
import com.hongmap.hongmapbackend.common.persistence.UniqueConflictRetry;
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
    private final UniqueConflictRetry uniqueConflictRetry;

    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId) {
        var bookmarks = bookmarkRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(BookmarkResponse::of)
                .toList();
        return new BookmarkListResponse(bookmarks);
    }

    /**
     * 멱등: 이미 북마크했으면 그 북마크를 그대로 돌려준다(전엔 409). 같은 요청이 동시에 두 번 와 uq_bookmark_user_news 에
     * 걸리면 새 트랜잭션에서 한 번 더 시도해 먼저 들어간 행을 돌려준다(UniqueConflictRetry). 트랜잭션은 그쪽이 연다.
     */
    public BookmarkResponse create(Long userId, Long newsId) {
        return uniqueConflictRetry.execute("bookmark-create", () -> createOnce(userId, newsId));
    }

    private BookmarkResponse createOnce(Long userId, Long newsId) {
        var existing = bookmarkRepository.findFirstByUser_IdAndNews_IdOrderByIdAsc(userId, newsId);
        if (existing.isPresent()) {
            return BookmarkResponse.of(existing.get());
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

    /**
     * 유니크 키가 생기기 전에 중복 행이 들어간 유저도 있을 수 있어, 한 건만 찾는 조회(중복이면 IncorrectResultSize 500) 대신
     * 그 조합을 한 번에 전부 지운다. 지운 게 없으면 404.
     */
    @Transactional
    public void delete(Long userId, Long newsId) {
        if (bookmarkRepository.deleteAllByUserIdAndNewsId(userId, newsId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "북마크하지 않은 소식입니다.");
        }
    }
}
