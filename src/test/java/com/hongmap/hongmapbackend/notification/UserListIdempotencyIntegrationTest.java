package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.bookmark.BookmarkRepository;
import com.hongmap.hongmapbackend.bookmark.BookmarkService;
import com.hongmap.hongmapbackend.bookmark.dto.BookmarkResponse;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.department.DepartmentRepository;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.department.UserDepartmentService;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentResponse;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionCreateRequest;
import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsUpdateRequest;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

/**
 * "확인 → INSERT" 경합 회귀 테스트 (2026-10-05 버그 점검) — 북마크·학과 구독(멱등, 기존 행 반환), 키워드 구독(공백 정리·
 * 대소문자 무시 중복 409·30개 상한·경합 409), 알림 설정 첫 저장·게시판 구독 동시 요청(둘 다 성공).
 * 경합은 리포지토리 스파이로 "확인은 없음, INSERT 는 유니크 위반" 상황을 결정적으로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserListIdempotencyIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired NewsRepository newsRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired BookmarkService bookmarkService;
    @MockitoSpyBean BookmarkRepository bookmarkRepository;
    @Autowired UserDepartmentService userDepartmentService;
    @MockitoSpyBean UserDepartmentRepository userDepartmentRepository;
    @Autowired KeywordSubscriptionService keywordSubscriptionService;
    @MockitoSpyBean KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Autowired NotificationSettingService notificationSettingService;
    @Autowired BoardSubscriptionService boardSubscriptionService;

    // ---------- 북마크 ----------

    @Test
    void 북마크는_다시_만들어도_기존_것을_돌려주고_경합에도_한_행만_남는다() {
        User me = newUser();
        News news = newNews();
        BookmarkResponse first = bookmarkService.create(me.getId(), news.getId());
        assertThat(bookmarkService.create(me.getId(), news.getId()).id()).isEqualTo(first.id());

        // 경합 재현: 확인은 "없음" → INSERT 가 uq_bookmark_user_news 위반 → 새 트랜잭션에서 다시 → 기존 행 반환
        doReturn(Optional.empty()).doAnswer(real(bookmarkRepository))
                .when(bookmarkRepository).findFirstByUser_IdAndNews_IdOrderByIdAsc(me.getId(), news.getId());
        assertThat(bookmarkService.create(me.getId(), news.getId()).id()).isEqualTo(first.id());
        assertThat(count("bookmarks", me)).isEqualTo(1L);

        bookmarkService.delete(me.getId(), news.getId());
        assertThat(count("bookmarks", me)).isZero();
        assertThatThrownBy(() -> bookmarkService.delete(me.getId(), news.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- 학과 ----------

    @Test
    void 학과_구독은_멱등이고_다시_주학과로_요청하면_주학과가_바뀐다() {
        User me = newUser();
        Department a = newDepartment();
        Department b = newDepartment();
        userDepartmentService.add(me.getId(), a.getId(), true);
        UserDepartmentResponse second = userDepartmentService.add(me.getId(), b.getId(), false);

        // 경합 재현
        doReturn(Optional.empty()).doAnswer(real(userDepartmentRepository))
                .when(userDepartmentRepository).findFirstByUser_IdAndDepartment_IdOrderByIdAsc(me.getId(), b.getId());
        UserDepartmentResponse again = userDepartmentService.add(me.getId(), b.getId(), true);

        assertThat(again.id()).isEqualTo(second.id());
        assertThat(again.isPrimary()).isTrue();
        assertThat(count("user_departments", me)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_departments WHERE user_id = ? AND is_primary = TRUE", Long.class, me.getId()))
                .isEqualTo(1L);
    }

    // ---------- 키워드 ----------

    @Test
    void 키워드는_공백을_지워_저장하고_대소문자만_다른_중복은_409() {
        User me = newUser();
        assertThat(keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("  AI 장학 ")).keyword())
                .isEqualTo("AI 장학");
        assertThatThrownBy(() -> keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("ai 장학")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 키워드_경합의_늦은_쪽은_500이_아니라_409() {
        User me = newUser();
        keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("장학"));
        doReturn(false).doAnswer(real(keywordSubscriptionRepository))
                .when(keywordSubscriptionRepository).existsByUser_IdAndKeywordIgnoreCase(anyLong(), anyString());

        assertThatThrownBy(() -> keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("장학")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo(KeywordSubscriptionService.DUPLICATE_MESSAGE);
                });
        assertThat(count("keyword_subscriptions", me)).isEqualTo(1L);
    }

    @Test
    void 키워드는_30개까지만_등록된다() {
        User me = newUser();
        for (int i = 0; i < KeywordSubscriptionService.MAX_KEYWORDS_PER_USER; i++) {
            keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("키워드" + i));
        }
        assertThatThrownBy(() -> keywordSubscriptionService.create(me.getId(), new KeywordSubscriptionCreateRequest("하나 더")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---------- 알림 설정·게시판 구독 (실제 두 스레드) ----------

    @Test
    void 알림_설정_첫_저장과_게시판_구독이_동시에_와도_둘_다_성공한다() throws Exception {
        String sourceId = CrawlerBoards.ALL.get(0).sourceId();
        for (int round = 0; round < 5; round++) {
            User me = newUser();
            runConcurrently(
                    () -> notificationSettingService.update(me.getId(),
                            new NotificationSettingsUpdateRequest(null, true, null, null)),
                    () -> notificationSettingService.update(me.getId(),
                            new NotificationSettingsUpdateRequest(false, null, null, null)));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_notification_settings WHERE user_id = ?", Long.class, me.getId()))
                    .isEqualTo(1L);

            runConcurrently(
                    () -> boardSubscriptionService.upsert(me.getId(), sourceId, true),
                    () -> boardSubscriptionService.upsert(me.getId(), sourceId, true));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_board_subscriptions WHERE user_id = ?", Long.class, me.getId()))
                    .isEqualTo(1L);
        }
    }

    // ---------- helpers ----------

    /** 스파이의 기본 Answer(리포지토리 프록시에 위임). 리포지토리 스파이는 callRealMethod 를 쓸 수 없다. */
    private static Answer<?> real(Object spy) {
        return mockingDetails(spy).getMockCreationSettings().getDefaultAnswer();
    }

    private void runConcurrently(Callable<?> a, Callable<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS); // 예외면 ExecutionException
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private long count(String table, User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, user.getId());
    }

    private User newUser() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("목록").build());
    }

    private News newNews() {
        return newsRepository.save(News.builder()
                .title("테스트 공지").category("공지")
                .sourceUrl("https://example.com/" + UUID.randomUUID())
                .publishedAt(LocalDateTime.now())
                .build());
    }

    private Department newDepartment() {
        return departmentRepository.save(Department.builder()
                .name("학과-" + UUID.randomUUID()).college("테스트대학").build());
    }
}
