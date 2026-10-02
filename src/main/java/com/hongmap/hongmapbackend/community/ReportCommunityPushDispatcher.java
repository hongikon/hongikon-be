package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.comment.ReportCommentCreatedEvent;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushSender;
import com.hongmap.hongmapbackend.push.PushProperties;
import com.hongmap.hongmapbackend.user.UserDevice;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 커뮤니티 푸시(Expo).
 *
 * <ul>
 *   <li>🔥 이정표: 작성자에게 "내 제보에 🔥가 N개 모였어요"(data.type = REPORT_FIRE, reportId, milestone).
 *       작성자의 "이 제보 알림"을 끄면 보내지 않는다.</li>
 *   <li>관심 제보(data.type = REPORT_FOLLOW, reportId, kind = START·ENDING·COMMENT):
 *       시작했어요 / 곧 끝나요(스케줄러가 부른다) / 새 댓글(제보마다·사람마다 push.report-follow-comment-coalesce-minutes,
 *       기본 30분에 한 번). 댓글 쓴 사람·제보 작성자·답글 받은 사람은 이미 다른 알림을 받으니 뺀다.</li>
 *   <li>모두 설정의 "내 제보 결과 알림"(report_status_enabled)을 끈 사람에게는 보내지 않는다.</li>
 * </ul>
 * 묶음 기록은 서버 메모리(서버 한 대 기준, 재시작하면 초기화 — 최악이 알림 한 번 더).
 */
@Slf4j
@Service
public class ReportCommunityPushDispatcher {

    static final String TYPE_FIRE = "REPORT_FIRE";
    static final String TYPE_FOLLOW = "REPORT_FOLLOW";
    static final String TITLE_START = "관심 제보가 시작됐어요";
    static final String TITLE_ENDING = "관심 제보가 곧 끝나요";
    static final String TITLE_COMMENT = "관심 제보에 새 댓글이 달렸어요";
    private static final int MAX_TRACKED_KEYS = 50_000;

    public enum FollowKind { START, ENDING, COMMENT }

    private final ReportFollowRepository followRepository;
    private final ReportCommunityService communityService;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;
    private final Duration commentCoalesce;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public ReportCommunityPushDispatcher(ReportFollowRepository followRepository,
                                         ReportCommunityService communityService,
                                         ExpoPushSender expoPushSender,
                                         PushProperties properties,
                                         @Value("${push.report-follow-comment-coalesce-minutes:30}") long coalesceMinutes) {
        this.followRepository = followRepository;
        this.communityService = communityService;
        this.expoPushSender = expoPushSender;
        this.properties = properties;
        this.commentCoalesce = Duration.ofMinutes(coalesceMinutes);
    }

    // ---------- 🔥 이정표 ----------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFireMilestone(ReportFireMilestoneEvent event) {
        try {
            sendFireMilestone(event);
        } catch (Exception e) {
            log.warn("🔥 이정표 푸시 실패 (reportId={}): {}", event.reportId(), e.getMessage());
        }
    }

    public int sendFireMilestone(ReportFireMilestoneEvent event) {
        if (!properties.isEnabled() || !communityService.isAuthorNotifyEnabled(event.reportId())) {
            return 0;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", TYPE_FIRE);
        data.put("reportId", event.reportId());
        data.put("milestone", event.milestone());
        return send(List.of(event.authorId()), "내 제보에 🔥가 " + event.milestone() + "개 모였어요",
                excerpt(event.reportTitle(), 60), data, "🔥 이정표");
    }

    // ---------- 관심: 새 댓글 ----------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(ReportCommentCreatedEvent event) {
        try {
            dispatchFollowComment(event);
        } catch (Exception e) {
            log.warn("관심 제보 댓글 푸시 실패 (reportId={}): {}", event.reportId(), e.getMessage());
        }
    }

    public int dispatchFollowComment(ReportCommentCreatedEvent event) {
        if (!properties.isEnabled()) {
            return 0;
        }
        Set<Long> recipients = new LinkedHashSet<>(followRepository.findFollowerIds(event.reportId()));
        recipients.remove(event.commenterId());
        recipients.remove(event.reportAuthorId());
        recipients.remove(event.parentAuthorId());
        recipients.removeIf(userId -> !claim("follow-comment:" + event.reportId() + ":" + userId));
        if (recipients.isEmpty()) {
            return 0;
        }
        String body = excerpt(event.reportTitle(), 30) + " · " + excerpt(event.content(), 60);
        return send(recipients, TITLE_COMMENT, body, followData(event.reportId(), FollowKind.COMMENT), "관심 댓글");
    }

    // ---------- 관심: 시작·곧 끝남(스케줄러) ----------

    public int sendFollowAlert(Long reportId, String reportTitle, Collection<Long> userIds, FollowKind kind,
                               long endingMinutes) {
        if (!properties.isEnabled() || userIds.isEmpty()) {
            return 0;
        }
        String title = kind == FollowKind.START ? TITLE_START : TITLE_ENDING;
        String body = kind == FollowKind.START
                ? excerpt(reportTitle, 60) + " · 지금 지도에서 볼 수 있어요"
                : excerpt(reportTitle, 60) + " · " + endingMinutes + "분 뒤에 끝나요";
        return send(userIds, title, body, followData(reportId, kind), "관심 " + kind);
    }

    // ---------- 내부 ----------

    private static Map<String, Object> followData(Long reportId, FollowKind kind) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", TYPE_FOLLOW);
        data.put("reportId", reportId);
        data.put("kind", kind.name());
        return data;
    }

    private int send(Collection<Long> userIds, String title, String body, Map<String, Object> data, String label) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return 0;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : followRepository.findAlertDevices(ids)) {
            tokens.add(device.getPushToken());
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, title, body, data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("{} 푸시: reportId={}, 메시지 {}건 중 {}건 접수", label, data.get("reportId"), messages.size(), result.accepted());
        return result.accepted();
    }

    /** 이 키에 창 안에서 처음이면 true(그리고 기록). */
    boolean claim(String key) {
        Instant now = clock.instant();
        if (lastSent.size() > MAX_TRACKED_KEYS) {
            lastSent.values().removeIf(t -> t.isBefore(now.minus(commentCoalesce)));
        }
        boolean[] claimed = {false};
        lastSent.compute(key, (k, last) -> {
            if (last == null || !last.isAfter(now.minus(commentCoalesce))) {
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
        lastSent.clear();
    }

    static String excerpt(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 1) + "…";
    }
}
