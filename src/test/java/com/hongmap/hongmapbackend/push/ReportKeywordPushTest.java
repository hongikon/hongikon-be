package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.ReportKeywordCandidate;
import com.hongmap.hongmapbackend.notification.ReportKeywordSubscription;
import com.hongmap.hongmapbackend.notification.ReportKeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 제보 키워드 알림 — 매칭(공백·대소문자·본문·장소 설명·분류·건물명), 범위 KEYWORDS, 빈도 제한 우회, 재실행 중복 방지, 대상 제외.
 * dispatch()/dispatchStarted()를 직접 불러 동기로 검증한다. Expo API는 ExpoPushClient를 모킹하고, 이 테스트의 토큰(run 접두어)만 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportKeywordPushTest {

    private static final AtomicLong REPORT_IDS = new AtomicLong(950_000);

    @Autowired ReportPushDispatcher dispatcher;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired ReportKeywordSubscriptionRepository reportKeywordRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    User author;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        author = user();
    }

    // ---------- 매칭 ----------

    @Test
    void 공백과_대소문자를_무시하고_제목_본문_장소_분류_건물명에서_찾는다() {
        String spaced = subscriber(NewReportScope.KEYWORDS, "간식 행사");     // 본문 "간식행사"
        String upper = subscriber(NewReportScope.KEYWORDS, "COFFEE");        // 제목 "coffee 트럭"
        String place = subscriber(NewReportScope.KEYWORDS, "라운지");         // 장소 설명
        String category = subscriber(NewReportScope.KEYWORDS, "플리 마켓");   // 직접 입력 분류 "플리마켓"
        String building = subscriber(NewReportScope.KEYWORDS, "홍문관");      // 건물명
        String miss = subscriber(NewReportScope.KEYWORDS, "붕어빵");

        dispatcher.dispatch(event("Coffee 트럭", "학생회 간식행사 같이 해요", "3층 라운지 앞", "플리마켓"));

        assertThat(only(spaced).title()).isEqualTo("[간식 행사] 새 제보 · 홍문관 1층");
        assertThat(only(upper).title()).isEqualTo("[COFFEE] 새 제보 · 홍문관 1층");
        assertThat(only(place).title()).isEqualTo("[라운지] 새 제보 · 홍문관 1층");
        assertThat(only(category).title()).isEqualTo("[플리 마켓] 새 제보 · 홍문관 1층");
        assertThat(only(building).title()).isEqualTo("[홍문관] 새 제보 · 홍문관 1층");
        assertThat(to(miss)).isEmpty();
        ExpoPushMessage m = only(spaced);
        assertThat(m.body()).isEqualTo("Coffee 트럭");
        assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", m.data().get("reportId")));
    }

    @Test
    void 여러_키워드가_걸리면_등록_순서의_첫_키워드에_외_N을_붙인다() {
        String token = subscriber(NewReportScope.KEYWORDS, "커피", "없는말", "트럭", "나눔");

        dispatcher.dispatch(event("커피 트럭 무료 나눔", null, null, null));

        assertThat(only(token).title()).isEqualTo("[커피 외 2] 새 제보 · 홍문관 1층");
    }

    @Test
    void 정규화와_제목_형식() {
        assertThat(ReportPushDispatcher.normalize(" 간식 \t행사 ABC ")).isEqualTo("간식행사abc");
        assertThat(ReportPushDispatcher.keywordTitle(List.of("간식"), "캠퍼스")).isEqualTo("[간식] 새 제보 · 캠퍼스");
        assertThat(ReportPushDispatcher.matchKeywords(
                List.of(new ReportKeywordCandidate(1L, "   "), new ReportKeywordCandidate(1L, "간 식")),
                List.of("오늘간식"))).isEqualTo(Map.of(1L, List.of("간 식")));
    }

    // ---------- 범위·빈도 제한 ----------

    @Test
    void KEYWORDS_범위는_키워드가_안_걸리면_일반_새_제보_알림을_받지_않는다() {
        String keywordsOnly = subscriber(NewReportScope.KEYWORDS, "간식");
        String campus = subscriber(NewReportScope.CAMPUS);

        dispatcher.dispatch(event("붕어빵 트럭", null, null, null));

        assertThat(to(keywordsOnly)).isEmpty();
        assertThat(only(campus).title()).isEqualTo("새 제보 · 홍문관 1층");
    }

    @Test
    void 키워드에_걸린_CAMPUS_유저는_같은_제보로_키워드_알림_하나만_받는다() {
        String token = subscriber(NewReportScope.CAMPUS, "붕어빵");

        dispatcher.dispatch(event("붕어빵 트럭", null, null, null));

        assertThat(only(token).title()).isEqualTo("[붕어빵] 새 제보 · 홍문관 1층");
    }

    @Test
    void 키워드_알림은_빈도_제한을_받지_않고_보낸_뒤_일반_알림의_빈도_제한에는_포함된다() {
        String token = subscriber(NewReportScope.CAMPUS, "간식");
        User owner = ownerOf(token);
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusMinutes(1), owner.getId());

        dispatcher.dispatch(event("간식 나눔", null, null, null));
        dispatcher.dispatch(event("간식 또 나눔", null, null, null));
        dispatcher.dispatch(event("붕어빵 트럭", null, null, null));   // 키워드 없음 → 30분 제한에 걸린다

        assertThat(to(token)).extracting(ExpoPushMessage::title)
                .containsExactly("[간식] 새 제보 · 홍문관 1층", "[간식] 새 제보 · 홍문관 1층");
        assertThat(settingRepository.findById(owner.getId()).orElseThrow().getNewReportLastSentAt())
                .isAfter(LocalDateTime.now().minusMinutes(1));
    }

    // ---------- 중복·제외 ----------

    @Test
    void 같은_제보를_다시_보내도_키워드_알림은_한_번만_간다() {
        String token = subscriber(NewReportScope.KEYWORDS, "간식");
        ReportModeratedEvent started = event("간식 나눔", null, null, null);

        dispatcher.dispatchStarted(started);
        dispatcher.dispatchStarted(started);
        dispatcher.dispatch(started);

        assertThat(to(token)).hasSize(1);
    }

    @Test
    void 같은_제보로_키워드_알림을_받은_유저는_재실행에서_일반_알림도_받지_않는다() {
        String token = subscriber(NewReportScope.CAMPUS, "간식");
        ReportModeratedEvent started = event("간식 나눔", null, null, null);

        dispatcher.dispatchStarted(started);
        // 빈도 제한이 풀린 뒤 스케줄러가 같은 제보를 다시 집은 상황
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = NULL WHERE user_id = ?",
                ownerOf(token).getId());
        dispatcher.dispatchStarted(started);

        assertThat(to(token)).hasSize(1);
    }

    @Test
    void 작성자_정지된_회원_새_제보_알림을_끈_유저는_키워드_알림을_받지_않는다() {
        String authorToken = device(author);
        keywords(author, "간식");
        optIn(author, NewReportScope.KEYWORDS, true);

        String suspended = subscriber(NewReportScope.KEYWORDS, "간식");
        User suspendedUser = ownerOf(suspended);
        suspendedUser.suspend("테스트");
        userRepository.save(suspendedUser);

        User off = user();
        String offToken = device(off);
        keywords(off, "간식");
        optIn(off, NewReportScope.KEYWORDS, false);

        String ok = subscriber(NewReportScope.KEYWORDS, "간식");

        dispatcher.dispatch(event("간식 나눔", null, null, null));

        assertThat(to(authorToken)).extracting(m -> m.data().get("type")).containsOnly("REPORT_STATUS");
        assertThat(to(suspended)).isEmpty();
        assertThat(to(offToken)).isEmpty();
        assertThat(to(ok)).hasSize(1);
    }

    // ---------- 픽스처 ----------

    private ReportModeratedEvent event(String title, String content, String placeLabel, String customCategoryLabel) {
        return new ReportModeratedEvent(REPORT_IDS.incrementAndGet(), author.getId(), title, "홍문관", 1,
                ReportStatus.PENDING, ReportStatus.ACTIVE, null, LocalDateTime.now().plusHours(2), null,
                content, placeLabel, customCategoryLabel);
    }

    /** 새 제보 알림을 켠 유저 + 기기 + 키워드. 기기 토큰을 돌려준다. */
    private String subscriber(NewReportScope scope, String... words) {
        User user = user();
        String token = device(user);
        keywords(user, words);
        optIn(user, scope, true);
        return token;
    }

    private void keywords(User user, String... words) {
        for (String word : words) {
            reportKeywordRepository.save(ReportKeywordSubscription.builder().user(user).keyword(word).build());
        }
    }

    private void optIn(User user, NewReportScope scope, boolean enabled) {
        UserNotificationSetting setting = new UserNotificationSetting(user.getId());
        setting.changeNewReportsEnabled(enabled);
        setting.changeNewReportsScope(scope);
        settingRepository.save(setting);
    }

    private User ownerOf(String token) {
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM user_devices WHERE push_token = ?", Long.class, token);
        return userRepository.findById(userId).orElseThrow();
    }

    private List<ExpoPushMessage> to(String token) {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().equals(token)).toList();
        }
    }

    private ExpoPushMessage only(String token) {
        List<ExpoPushMessage> messages = to(token);
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String device(User user) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(user).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }
}
