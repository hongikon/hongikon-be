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
 * 새 소식 푸시 대상 매칭(학과/카테고리/키워드·중복 제거)과 배치 발송. H2 인메모리 DB(application-test.properties),
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
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired NewsRepository newsRepository;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    Department deptA;
    Department deptB;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        deptA = departmentRepository.save(Department.builder().name("학과A-" + run).college("테스트대학").build());
        deptB = departmentRepository.save(Department.builder().name("학과B-" + run).college("테스트대학").build());
    }

    // ---------- 매칭 ----------

    @Test
    void 학과_게시판_글은_그_학과_구독자의_활성_Expo_기기에만_간다() {
        User deptUser = user();
        subscribeDepartment(deptUser, deptA);
        String token = device(deptUser, TokenType.EXPO, true);

        User otherDeptUser = user();
        subscribeDepartment(otherDeptUser, deptB);
        device(otherDeptUser, TokenType.EXPO, true);

        User inactiveUser = user();
        subscribeDepartment(inactiveUser, deptA);
        device(inactiveUser, TokenType.EXPO, false);

        User fcmUser = user();
        subscribeDepartment(fcmUser, deptA);
        device(fcmUser, TokenType.FCM, true);

        // 학과 글은 카테고리 구독과 무관하다.
        User categoryUser = user();
        subscribeCategory(categoryUser, "공지", true);
        device(categoryUser, TokenType.EXPO, true);

        assertThat(targetTokens(news(deptA, "공지", "중간고사 일정 안내"))).containsExactly(token);
    }

    @Test
    void 대학공지는_그_카테고리를_끈_유저만_빼고_간다() {
        User on = user();
        subscribeCategory(on, "장학", true);
        String onToken = device(on, TokenType.EXPO, true);

        // 한 번도 저장 안 한 카테고리는 화면에서 켜짐으로 보이므로 받는다.
        User neverSet = user();
        String neverSetToken = device(neverSet, TokenType.EXPO, true);

        // 다른 카테고리만 껐으면 이 카테고리는 받는다.
        User offOther = user();
        subscribeCategory(offOther, "행사", false);
        String offOtherToken = device(offOther, TokenType.EXPO, true);

        User off = user();
        subscribeCategory(off, "장학", false);
        device(off, TokenType.EXPO, true);

        assertThat(targetTokens(news(null, "장학", "2026-2 국가장학금 신청 안내")))
                .containsExactlyInAnyOrder(onToken, neverSetToken, offOtherToken);
    }

    @Test
    void 카테고리를_꺼도_키워드에_걸리면_받는다() {
        User off = user();
        subscribeCategory(off, "장학", false);
        subscribeKeyword(off, "국가장학금");
        String token = device(off, TokenType.EXPO, true);

        assertThat(targetTokens(news(null, "장학", "2026-2 국가장학금 신청 안내"))).contains(token);
    }

    @Test
    void 학과_게시판_글은_카테고리_설정과_무관하게_학과_구독자만_받는다() {
        // 카테고리를 끄지 않은 유저라도 학과를 구독하지 않았으면 학과 글은 안 간다.
        User neverSet = user();
        device(neverSet, TokenType.EXPO, true);

        // 학과 구독자는 그 카테고리를 꺼 뒀어도 학과 글을 받는다(학과 글은 학과 구독만 본다).
        User deptUserCategoryOff = user();
        subscribeDepartment(deptUserCategoryOff, deptA);
        subscribeCategory(deptUserCategoryOff, "공지", false);
        String token = device(deptUserCategoryOff, TokenType.EXPO, true);

        assertThat(targetTokens(news(deptA, "공지", "학과 행정실 공지"))).containsExactly(token);
    }

    @Test
    void 키워드가_제목에_들어가면_학과_카테고리_구독과_무관하게_추가로_가고_대소문자는_무시한다() {
        User koreanKeyword = user();
        subscribeKeyword(koreanKeyword, "해커톤");
        String koreanToken = device(koreanKeyword, TokenType.EXPO, true);

        User englishKeyword = user();
        subscribeKeyword(englishKeyword, "hackathon");
        String englishToken = device(englishKeyword, TokenType.EXPO, true);

        User unrelatedKeyword = user();
        subscribeKeyword(unrelatedKeyword, "장학금");
        device(unrelatedKeyword, TokenType.EXPO, true);

        User deptUser = user();
        subscribeDepartment(deptUser, deptB);
        String deptToken = device(deptUser, TokenType.EXPO, true);

        assertThat(targetTokens(news(deptB, "행사", "SW 해커톤(HackAthon) 참가자 모집")))
                .containsExactlyInAnyOrder(koreanToken, englishToken, deptToken);
    }

    @Test
    void 여러_기준에_동시에_걸려도_기기마다_한_번씩만_간다() {
        User both = user();
        subscribeDepartment(both, deptA);
        subscribeKeyword(both, "해커톤");
        subscribeKeyword(both, "SW");
        String phone = device(both, TokenType.EXPO, true);
        String tablet = device(both, TokenType.EXPO, true);

        List<String> tokens = targetTokens(news(deptA, "행사", "SW 해커톤 안내"));
        assertThat(tokens).containsExactlyInAnyOrder(phone, tablet);
    }

    // ---------- 발송 ----------

    @Test
    void 메시지는_100개씩_나눠_보내고_data에_type과_newsId를_담는다() {
        User user = user();
        subscribeDepartment(user, deptA);
        for (int i = 0; i < 150; i++) {
            device(user, TokenType.EXPO, true);
        }
        News news = news(deptA, "공지", "150대 기기 발송");
        when(expoPushClient.send(anyList())).thenAnswer(inv -> okTickets(inv.getArgument(0)));

        int accepted = dispatcher.dispatch(List.of(news));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpoPushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(100, 50);
        assertThat(accepted).isEqualTo(150);

        ExpoPushMessage first = captor.getAllValues().get(0).get(0);
        assertThat(first.title()).isEqualTo(deptA.getName());
        assertThat(first.body()).isEqualTo("150대 기기 발송");
        assertThat(first.data()).isEqualTo(Map.of("type", "NEWS", "newsId", news.getId()));
    }

    @Test
    void DeviceNotRegistered_응답을_받은_기기는_비활성화한다() {
        User user = user();
        subscribeDepartment(user, deptA);
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

        dispatcher.dispatch(List.of(news(deptA, "공지", "기기 정리")));

        assertThat(userDeviceRepository.findByPushToken(dead).orElseThrow().isActive()).isFalse();
        assertThat(userDeviceRepository.findByPushToken(alive).orElseThrow().isActive()).isTrue();
    }

    @Test
    void Expo_호출이_실패해도_예외를_던지지_않고_다음_배치는_계속_보낸다() {
        User user = user();
        subscribeDepartment(user, deptA);
        for (int i = 0; i < 101; i++) {
            device(user, TokenType.EXPO, true);
        }
        when(expoPushClient.send(anyList()))
                .thenThrow(new ResourceAccessException("connect timed out"))
                .thenAnswer(inv -> okTickets(inv.getArgument(0)));

        int accepted = dispatcher.dispatch(List.of(news(deptA, "공지", "네트워크 오류")));

        verify(expoPushClient, times(2)).send(anyList());
        assertThat(accepted).isEqualTo(1);
    }

    @Test
    void 작성일이_오래된_새_소식은_푸시하지_않는다() {
        User user = user();
        subscribeDepartment(user, deptA);
        device(user, TokenType.EXPO, true);
        News old = news(deptA, "공지", "작년 공지", LocalDateTime.now().minusDays(30));

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

    private void subscribeDepartment(User user, Department department) {
        userDepartmentRepository.save(UserDepartment.builder().user(user).department(department).build());
    }

    private void subscribeCategory(User user, String category, boolean enabled) {
        notificationCategoryRepository.save(NotificationCategory.builder().user(user).category(category).enabled(enabled).build());
    }

    private void subscribeKeyword(User user, String keyword) {
        keywordSubscriptionRepository.save(KeywordSubscription.builder().user(user).keyword(keyword).build());
    }

    private News news(Department department, String category, String title) {
        return news(department, category, title, LocalDateTime.now());
    }

    private News news(Department department, String category, String title, LocalDateTime publishedAt) {
        return newsRepository.save(News.builder()
                .title(title)
                .category(category)
                .department(department)
                .sourceUrl("https://example.com/" + run + "/" + UUID.randomUUID())
                .publishedAt(publishedAt)
                .build());
    }
}
