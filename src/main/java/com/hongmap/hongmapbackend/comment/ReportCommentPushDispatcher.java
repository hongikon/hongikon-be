package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushSender;
import com.hongmap.hongmapbackend.push.PushProperties;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "내 제보에 댓글이 달렸어요" 푸시(Expo, data.type = REPORT_COMMENT). 댓글이 커밋된 뒤 별도 스레드에서 돈다.
 *
 * <ul>
 *   <li>받는 사람: 제보 작성자 한 명뿐. 본인이 단 댓글은 알리지 않는다.</li>
 *   <li>설정: 기존 "내 제보 결과 알림"(report_status_enabled)을 따른다 — 끈 사람은 받지 않는다(행이 없으면 켜짐).</li>
 *   <li>묶음: 같은 제보에는 push.report-comment-coalesce-minutes(기본 10분)에 한 번만. 그 사이 댓글은 앱에서 보면 된다.
 *       서버 한 대(EC2) 기준 메모리 기록이라 재시작하면 초기화된다(최악이 알림 한 번 더).</li>
 * </ul>
 */
@Slf4j
@Service
public class ReportCommentPushDispatcher {

    static final String DATA_TYPE = "REPORT_COMMENT";
    static final String TITLE = "내 제보에 댓글이 달렸어요";
    private static final int MAX_EXCERPT = 60;
    private static final int MAX_TRACKED_REPORTS = 10_000;

    private final UserDeviceRepository userDeviceRepository;
    private final UserNotificationSettingRepository settingRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;
    private final Duration coalesceWindow;
    private final Map<Long, Instant> lastSentByReport = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public ReportCommentPushDispatcher(UserDeviceRepository userDeviceRepository,
                                       UserNotificationSettingRepository settingRepository,
                                       ExpoPushSender expoPushSender,
                                       PushProperties properties,
                                       @Value("${push.report-comment-coalesce-minutes:10}") long coalesceMinutes) {
        this.userDeviceRepository = userDeviceRepository;
        this.settingRepository = settingRepository;
        this.expoPushSender = expoPushSender;
        this.properties = properties;
        this.coalesceWindow = Duration.ofMinutes(coalesceMinutes);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(ReportCommentCreatedEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.warn("댓글 푸시 실패 (reportId={}): {}", event.reportId(), e.getMessage());
        }
    }

    /** 동기 실행 — 리스너와 테스트가 쓴다. Expo 가 받아들인 메시지 수. */
    public int dispatch(ReportCommentCreatedEvent event) {
        if (!properties.isEnabled() || event.reportAuthorId() == null
                || event.reportAuthorId().equals(event.commenterId())) {
            return 0;
        }
        boolean enabled = settingRepository.findById(event.reportAuthorId())
                .map(UserNotificationSetting::isReportStatusEnabled)
                .orElse(UserNotificationSetting.DEFAULT_REPORT_STATUS_ENABLED);
        if (!enabled) {
            return 0;
        }
        Set<String> tokens = expoTokens(userDeviceRepository.findByUserIdAndActiveTrue(event.reportAuthorId()));
        if (tokens.isEmpty() || !claim(event.reportId())) {
            return 0;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", DATA_TYPE);
        data.put("reportId", event.reportId());
        String body = excerpt(event.reportTitle(), 30) + " · " + excerpt(event.content(), MAX_EXCERPT);
        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, TITLE, body, data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("댓글 푸시: reportId={}, 메시지 {}건 중 {}건 접수", event.reportId(), messages.size(), result.accepted());
        return result.accepted();
    }

    /** 이 제보에 창 안에서 처음이면 true(그리고 기록). 동시에 두 댓글이 와도 한 번만 true. */
    boolean claim(Long reportId) {
        Instant now = clock.instant();
        if (lastSentByReport.size() > MAX_TRACKED_REPORTS) {
            lastSentByReport.values().removeIf(t -> t.isBefore(now.minus(coalesceWindow)));
        }
        boolean[] claimed = {false};
        lastSentByReport.compute(reportId, (id, last) -> {
            if (last == null || !last.isAfter(now.minus(coalesceWindow))) {
                claimed[0] = true;
                return now;
            }
            return last;
        });
        return claimed[0];
    }

    void setClockForTest(Clock clock) {
        this.clock = clock;
    }

    void resetForTest() {
        lastSentByReport.clear();
    }

    private static Set<String> expoTokens(List<UserDevice> devices) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : devices) {
            if (device.isActive() && device.getTokenType() == TokenType.EXPO) {
                tokens.add(device.getPushToken());
            }
        }
        return tokens;
    }

    static String excerpt(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 1) + "…";
    }
}
