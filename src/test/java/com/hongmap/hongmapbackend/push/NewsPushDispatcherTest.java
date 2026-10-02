package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.department.DepartmentRepository;
import com.hongmap.hongmapbackend.department.UserDepartment;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import com.hongmap.hongmapbackend.notification.KeywordSubscription;
import com.hongmap.hongmapbackend.notification.KeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.NotificationCategory;
import com.hongmap.hongmapbackend.notification.NotificationCategoryRepository;
import com.hongmap.hongmapbackend.notification.UserBoardSubscription;
import com.hongmap.hongmapbackend.notification.UserBoardSubscriptionRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 새 소식 푸시 대상 매칭(게시판 구독·알림 on/off/카테고리/키워드·중복 제거)과 배치 발송. H2 인메모리 DB(application-test.properties),
 * Expo API는 ExpoPushClient를 모킹한다.
 * 다른 테스트가 만든 기기와 섞이지 않게, 이 테스트가 만든 토큰(run 접두어)만 골라 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NewsPushDispatcherTest {

    @Autowired NewsPushDispatcher dispatcher;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired UserDepartmentRepository userDepartmentRepository;
    @Autowired NotificationCategoryRepository notificationCategoryRepository;
    @Autowired KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Autowired UserBoardSubscriptionRepository userBoardSubscriptionRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired NewsRepository newsRepository;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    String boardA;
    String boardB;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        boardA = "학과A-" + run;
        boardB = "학과B-" + run;
    }

    // ---------- 매칭 ----------

    @Test
    void 게시판_구독자_중_알림을_켠_활성_Expo_기기에만_간다() {
        User subscriber = user();
        subscribeBoard(subscriber, boardA, true);
        String token = device(subscriber, TokenType.EXPO, true);

        User alertOff = user();
        subscribeBoard(alertOff, boardA, false);
        device(alertOff, TokenType.EXPO, true);

        User otherBoard = user();
        subscribeBoard(otherBoard, boardB, true);
        device(otherBoard, TokenType.EXPO, true);

        User inactiveDevice = user();
        subscribeBoard(inactiveDevice, boardA, true);
        device(inactiveDevice, TokenType.EXPO, false);

        User fcmUser = user();
        subscribeBoard(fcmUser, boardA, true);
        device(fcmUser, TokenType.FCM, true);

        // 구독하지 않은 유저는 카테고리를 켜 둬도 받지 않는다.
        User categoryOnly = user();
        subscribeCategory(categoryOnly, "공지", true);
        device(categoryOnly, TokenType.EXPO, true);

        assertThat(targetTokens(news(boardA, "공지", "중간고사 일정 안내"))).containsExactly(token);
    }

    @Test
    void 구독한_게시판이어도_그_카테고리를_끄면_받지_않는다() {
        User off = user();
        subscribeBoard(off, boardA, true);
        subscribeCategory(off, "장학", false);
        device(off, TokenType.EXPO, true);

        // 다른 카테고리만 껐거나 한 번도 저장 안 했으면(켜짐) 받는다.
        User offOther = user();
        subscribeBoard(offOther, boardA, true);
        subscribeCategory(offOther, "행사", false);
        String offOtherToken = device(offOther, TokenType.EXPO, true);

        User neverSet = user();
        subscribeBoard(neverSet, boardA, true);
        String neverSetToken = device(neverSet, TokenType.EXPO, true);

        assertThat(targetTokens(news(boardA, "장학", "학과 장학금 안내")))
                .containsExactlyInAnyOrder(offOtherToken, neverSetToken);
    }

    @Test
    void 대학공지도_구독자에게만_간다() {
        User subscriber = user();
        subscribeBoard(subscriber, "장학", true);
        String token = device(subscriber, TokenType.EXPO, true);

        // 예전에는 카테고리를 끄지 않은 전원에게 갔지만, 이제 구독하지 않았으면 받지 않는다.
        User notSubscribed = user();
        subscribeCategory(notSubscribed, "장학", true);
        device(notSubscribed, TokenType.EXPO, true);

        User otherUnivBoard = user();
        subscribeBoard(otherUnivBoard, "학사", true);
        device(otherUnivBoard, TokenType.EXPO, true);

        assertThat(targetTokens(news("장학", "장학", "2026-2 국가장학금 신청 안내 " + run))).containsExactly(token);
    }

    @Test
    void 상위_게시판을_빌려_쓰는_전공_구독자도_상위_게시판_소식을_받는다() {
        // 데이터사이언스전공은 자체 게시판이 없어 산업데이터공학과 게시판으로 저장된다(CrawlerBoards.SOURCE_ALIASES).
        User majorSubscriber = user();
        subscribeBoard(majorSubscriber, "데이터사이언스전공", true);
        String majorToken = device(majorSubscriber, TokenType.EXPO, true);

        User parentSubscriber = user();
        subscribeBoard(parentSubscriber, "산업데이터공학과", true);
        String parentToken = device(parentSubscriber, TokenType.EXPO, true);

        // 같은 사람이 둘 다 구독해도 한 번만 간다.
        User both = user();
        subscribeBoard(both, "데이터사이언스전공", true);
        subscribeBoard(both, "산업데이터공학과", true);
        String bothToken = device(both, TokenType.EXPO, true);

        User otherMajor = user();
        subscribeBoard(otherMajor, "사물인터넷공학전공", true);
        device(otherMajor, TokenType.EXPO, true);

        assertThat(targetTokens(news("산업데이터공학과", "공지", "융합전공 신청 안내 " + run)))
                .containsExactlyInAnyOrder(majorToken, parentToken, bothToken);
    }

    @Test
    void 학과_구독만_있고_게시판_구독이_없으면_받지_않는다() {
        Department dept = departmentRepository.save(Department.builder().name(boardA).college("테스트대학").build());
        User deptOnly = user();
        userDepartmentRepository.save(UserDepartment.builder().user(deptOnly).department(dept).build());
        device(deptOnly, TokenType.EXPO, true);

        News news = newsRepository.save(News.builder()
                .title("학과 행정실 공지").category("공지").sourceId(boardA).department(dept)
                .sourceUrl("https://example.com/" + run + "/" + UUID.randomUUID())
                .publishedAt(LocalDateTime.now())
                .build());
        assertThat(targetTokens(news)).isEmpty();
    }

    @Test
    void 카테고리를_꺼도_알림을_꺼도_구독을_안_해도_키워드에_걸리면_받는다() {
        User categoryOff = user();
        subscribeBoard(categoryOff, "장학", true);
        subscribeCategory(categoryOff, "장학", false);
        subscribeKeyword(categoryOff, "국가장학금");
        String categoryOffToken = device(categoryOff, TokenType.EXPO, true);

        User alertOff = user();
        subscribeBoard(alertOff, "장학", false);
        subscribeKeyword(alertOff, "국가장학금");
        String alertOffToken = device(alertOff, TokenType.EXPO, true);

        User notSubscribed = user();
        subscribeKeyword(notSubscribed, "국가장학금");
        String notSubscribedToken = device(notSubscribed, TokenType.EXPO, true);

        assertThat(targetTokens(news("장학", "장학", "2026-2 국가장학금 신청 안내 " + run)))
                .containsExactlyInAnyOrder(categoryOffToken, alertOffToken, notSubscribedToken);
    }

    @Test
    void 키워드가_제목에_들어가면_게시판_구독과_무관하게_추가로_가고_대소문자는_무시한다() {
        User koreanKeyword = user();
        subscribeKeyword(koreanKeyword, "해커톤");
        String koreanToken = device(koreanKeyword, TokenType.EXPO, true);

        User englishKeyword = user();
        subscribeKeyword(englishKeyword, "hackathon");
        String englishToken = device(englishKeyword, TokenType.EXPO, true);

        User unrelatedKeyword = user();
        subscribeKeyword(unrelatedKeyword, "장학금");
        device(unrelatedKeyword, TokenType.EXPO, true);

        User boardUser = user();
        subscribeBoard(boardUser, boardB, true);
        String boardToken = device(boardUser, TokenType.EXPO, true);

        assertThat(targetTokens(news(boardB, "행사", "SW 해커톤(HackAthon) 참가자 모집")))
                .containsExactlyInAnyOrder(koreanToken, englishToken, boardToken);
    }

    @Test
    void 게시판_출처가_없는_소식은_키워드_구독자에게만_간다() {
        User keywordUser = user();
        subscribeKeyword(keywordUser, "졸업");
        String keywordToken = device(keywordUser, TokenType.EXPO, true);

        User boardUser = user();
        subscribeBoard(boardUser, boardA, true);
        device(boardUser, TokenType.EXPO, true);

        User nothing = user();
        device(nothing, TokenType.EXPO, true);

        assertThat(targetTokens(news(null, "공지", "졸업 사정 안내"))).containsExactly(keywordToken);
    }

    @Test
    void 여러_기준에_동시에_걸려도_기기마다_한_번씩만_간다() {
        User both = user();
        subscribeBoard(both, boardA, true);
        subscribeKeyword(both, "해커톤");
        subscribeKeyword(both, "SW");
        String phone = device(both, TokenType.EXPO, true);
        String tablet = device(both, TokenType.EXPO, true);

        List<String> tokens = targetTokens(news(boardA, "행사", "SW 해커톤 안내"));
        assertThat(tokens).containsExactlyInAnyOrder(phone, tablet);
    }

    // ---------- 발송 ----------

    @Test
    void 메시지는_100개씩_나눠_보내고_data에_type과_newsId를_담는다() {
        User user = user();
        subscribeBoard(user, boardA, true);
        for (int i = 0; i < 150; i++) {
            device(user, TokenType.EXPO, true);
        }
        News news = news(boardA, "공지", "150대 기기 발송");
        when(expoPushClient.send(anyList())).thenAnswer(inv -> okTickets(inv.getArgument(0)));

        int accepted = dispatcher.dispatch(List.of(news));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpoPushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 50);
        assertThat(accepted).isEqualTo(150);

        ExpoPushMessage first = captor.getAllValues().get(0).get(0);
        assertThat(first.title()).isEqualTo(boardA + " 공지");
        assertThat(first.body()).isEqualTo("150대 기기 발송");
        assertThat(first.data()).isEqualTo(Map.of("type", "NEWS", "newsId", news.getId()));
    }

    @Test
    void DeviceNotRegistered_응답을_받은_기기는_비활성화한다() {
        User user = user();
        subscribeBoard(user, boardA, true);
        String alive = device(user, TokenType.EXPO, true);
        String dead = device(user, TokenType.EXPO, true);
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            return batch.stream()
                    .map(m -> m.to().equals(dead)
                            ? new ExpoPushTicket("error", null, "not registered", Map.of("error", "DeviceNotRegistered"))
                            : new ExpoPushTicket("ok", "ticket-id", null, null))
                    .toList();
        });

        dispatcher.dispatch(List.of(news(boardA, "공지", "기기 정리")));

        assertThat(userDeviceRepository.findByPushToken(dead).orElseThrow().isActive()).isFalse();
        assertThat(userDeviceRepository.findByPushToken(alive).orElseThrow().isActive()).isTrue();
    }

    @Test
    void Expo_호출이_실패해도_예외를_던지지_않고_다음_배치는_계속_보낸다() {
        User user = user();
        subscribeBoard(user, boardA, true);
        for (int i = 0; i < 101; i++) {
            device(user, TokenType.EXPO, true);
        }
        when(expoPushClient.send(anyList()))
                .thenThrow(new ResourceAccessException("connect timed out"))
                .thenAnswer(inv -> okTickets(inv.getArgument(0)));

        int accepted = dispatcher.dispatch(List.of(news(boardA, "공지", "네트워크 오류")));

        verify(expoPushClient, times(2)).send(anyList());
        assertThat(accepted).isEqualTo(1);
    }

    @Test
    void 작성일이_오래된_새_소식은_푸시하지_않는다() {
        User user = user();
        subscribeBoard(user, boardA, true);
        device(user, TokenType.EXPO, true);
        News old = news(boardA, "공지", "작년 공지", LocalDateTime.now().minusDays(30));

        assertThat(dispatcher.dispatch(List.of(old))).isZero();
        verify(expoPushClient, never()).send(anyList());
    }

    // ---------- 픽스처 ----------

    private List<String> targetTokens(News news) {
        return dispatcher.findTargets(news).stream()
                .map(UserDevice::getPushToken)
                .filter(token -> token.contains(run))
                .toList();
    }

    private List<ExpoPushTicket> okTickets(List<ExpoPushMessage> batch) {
        return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String device(User user, TokenType tokenType, boolean active) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        UserDevice device = UserDevice.builder().user(user).pushToken(token).tokenType(tokenType).build();
        if (!active) {
            device.deactivate();
        }
        userDeviceRepository.save(device);
        return token;
    }

    private void subscribeBoard(User user, String sourceId, boolean alertEnabled) {
        userBoardSubscriptionRepository.save(UserBoardSubscription.builder()
                .user(user).sourceId(sourceId).alertEnabled(alertEnabled).build());
    }

    private void subscribeCategory(User user, String category, boolean enabled) {
        notificationCategoryRepository.save(NotificationCategory.builder().user(user).category(category).enabled(enabled).build());
    }

    private void subscribeKeyword(User user, String keyword) {
        keywordSubscriptionRepository.save(KeywordSubscription.builder().user(user).keyword(keyword).build());
    }

    private News news(String sourceId, String category, String title) {
        return news(sourceId, category, title, LocalDateTime.now());
    }

    private News news(String sourceId, String category, String title, LocalDateTime publishedAt) {
        return newsRepository.save(News.builder()
                .title(title)
                .category(category)
                .sourceId(sourceId)
                .sourceUrl("https://example.com/" + run + "/" + UUID.randomUUID())
                .publishedAt(publishedAt)
                .build());
    }
}
