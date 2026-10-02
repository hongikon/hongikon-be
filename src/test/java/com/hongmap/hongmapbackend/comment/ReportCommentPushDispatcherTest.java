package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushClient;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushTicket;
import com.hongmap.hongmapbackend.user.DevicePlatform;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** "내 제보에 댓글이 달렸어요" — 받는 사람, 알림 설정, 같은 제보 10분 묶음. dispatch()를 동기로 부른다. */
@SpringBootTest
@ActiveProfiles("test")
class ReportCommentPushDispatcherTest {

    private static final AtomicLong REPORT_IDS = new AtomicLong(800_000);

    @Autowired ReportCommentPushDispatcher dispatcher;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;

    @MockitoBean ExpoPushClient expoPushClient;

    List<ExpoPushMessage> sent;

    @BeforeEach
    void setUp() {
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        dispatcher.resetForTest();
    }

    @AfterEach
    void tearDown() {
        dispatcher.setClockForTest(Clock.systemUTC());
        dispatcher.resetForTest();
    }

    @Test
    void 제보_작성자의_기기에만_간다_제목_본문_데이터() {
        User author = user();
        String token = device(author);
        User commenter = user();
        device(commenter);

        long reportId = REPORT_IDS.incrementAndGet();
        int accepted = dispatcher.dispatch(event(reportId, author, commenter, "지금   줄이\n짧아요"));

        assertThat(accepted).isEqualTo(1);
        assertThat(sent).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(token);
            assertThat(m.title()).isEqualTo("내 제보에 댓글이 달렸어요");
            assertThat(m.body()).isEqualTo("붕어빵 트럭 · 지금 줄이 짧아요");
            assertThat(m.data()).containsEntry("type", "REPORT_COMMENT").containsEntry("reportId", reportId);
        });
    }

    @Test
    void 내가_단_댓글이나_결과_알림을_끈_작성자는_받지_않는다() {
        User author = user();
        device(author);
        long reportId = REPORT_IDS.incrementAndGet();
        assertThat(dispatcher.dispatch(event(reportId, author, author, "셀프 댓글"))).isZero();

        UserNotificationSetting off = new UserNotificationSetting(author.getId());
        off.changeReportStatusEnabled(false);
        settingRepository.save(off);
        assertThat(dispatcher.dispatch(event(reportId, author, user(), "남의 댓글"))).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void 같은_제보는_10분에_한_번만_다른_제보는_따로() {
        User author = user();
        device(author);
        Instant t0 = Instant.parse("2026-10-02T03:00:00Z");
        dispatcher.setClockForTest(Clock.fixed(t0, ZoneOffset.UTC));
        long reportA = REPORT_IDS.incrementAndGet();
        long reportB = REPORT_IDS.incrementAndGet();

        assertThat(dispatcher.dispatch(event(reportA, author, user(), "첫 댓글"))).isEqualTo(1);
        assertThat(dispatcher.dispatch(event(reportA, author, user(), "두 번째"))).isZero();
        assertThat(dispatcher.dispatch(event(reportB, author, user(), "다른 제보"))).isEqualTo(1);

        dispatcher.setClockForTest(Clock.fixed(t0.plus(Duration.ofMinutes(9)), ZoneOffset.UTC));
        assertThat(dispatcher.dispatch(event(reportA, author, user(), "9분 뒤"))).isZero();
        dispatcher.setClockForTest(Clock.fixed(t0.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertThat(dispatcher.dispatch(event(reportA, author, user(), "10분 뒤"))).isEqualTo(1);
        assertThat(sent).hasSize(3);
    }

    @Test
    void 기기가_없으면_묶음_창을_쓰지_않는다() {
        User author = user();
        long reportId = REPORT_IDS.incrementAndGet();
        assertThat(dispatcher.dispatch(event(reportId, author, user(), "아직 기기 없음"))).isZero();
        device(author);
        assertThat(dispatcher.dispatch(event(reportId, author, user(), "기기 등록 뒤"))).isEqualTo(1);
    }

    @Test
    void 답글은_부모_댓글_작성자와_제보_작성자에게_각각_한_번() {
        User reportAuthor = user();
        String reportToken = device(reportAuthor);
        User parentAuthor = user();
        String parentToken = device(parentAuthor);
        User replier = user();
        long reportId = REPORT_IDS.incrementAndGet();

        int accepted = dispatcher.dispatch(new ReportCommentCreatedEvent(reportId, reportAuthor.getId(), replier.getId(),
                "붕어빵 트럭", "저도 궁금해요", 77L, parentAuthor.getId()));

        assertThat(accepted).isEqualTo(2);
        assertThat(sent).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo(parentToken);
            assertThat(m.title()).isEqualTo("내 댓글에 답글이 달렸어요");
            assertThat(m.data()).containsEntry("commentId", 77L).containsEntry("type", "REPORT_COMMENT");
        });
        assertThat(sent).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo(reportToken);
            assertThat(m.title()).isEqualTo("내 제보에 댓글이 달렸어요");
        });
    }

    @Test
    void 제보_작성자가_부모_댓글_작성자면_답글_알림_한_번만_자기_답글은_없음() {
        User author = user();
        device(author);
        long reportId = REPORT_IDS.incrementAndGet();
        assertThat(dispatcher.dispatch(new ReportCommentCreatedEvent(reportId, author.getId(), user().getId(),
                "붕어빵 트럭", "답글", 78L, author.getId()))).isEqualTo(1);
        assertThat(sent).singleElement().satisfies(m -> assertThat(m.title()).isEqualTo("내 댓글에 답글이 달렸어요"));

        User parentAuthor = user();
        device(parentAuthor);
        sent.clear();
        // 부모 작성자가 자기 댓글에 답글 → 부모 알림 없음, 제보 작성자는 이미 이 제보로 10분 안에 받지 않았으므로 받음
        long other = REPORT_IDS.incrementAndGet();
        assertThat(dispatcher.dispatch(new ReportCommentCreatedEvent(other, author.getId(), parentAuthor.getId(),
                "다른 제보", "셀프 답글", 79L, parentAuthor.getId()))).isEqualTo(1);
        assertThat(sent).singleElement().satisfies(m -> assertThat(m.title()).isEqualTo("내 제보에 댓글이 달렸어요"));
    }

    @Test
    void 긴_본문은_줄인다() {
        assertThat(ReportCommentPushDispatcher.excerpt("가".repeat(100), 60)).hasSize(60).endsWith("…");
        assertThat(ReportCommentPushDispatcher.excerpt(null, 60)).isEmpty();
    }

    private ReportCommentCreatedEvent event(long reportId, User author, User commenter, String content) {
        return new ReportCommentCreatedEvent(reportId, author.getId(), commenter.getId(), "붕어빵 트럭", content);
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String device(User user) {
        String token = "ExponentPushToken[" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder()
                .user(user).pushToken(token).tokenType(TokenType.EXPO).platform(DevicePlatform.IOS).build());
        return token;
    }
}
