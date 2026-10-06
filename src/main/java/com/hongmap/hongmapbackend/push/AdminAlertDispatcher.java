package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.admin.AdminAlertType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 관리자 알림 푸시(Expo) — 새 제보 승인 대기, 새 문의, 신고 누적 자동 숨김, 정지·신고 이력 회원 재가입, 댓글 신고.
 * 이벤트를 발행한 트랜잭션이 커밋된 뒤 별도 스레드에서 돈다(작성자 응답을 늦추지 않고, 롤백되면 보내지 않는다). 실패는 로그만 남긴다.
 *
 * <ul>
 *   <li>대상: role=ADMIN 유저의 활성 Expo 기기. 그 일을 만든 본인(actor)은 빼고, user_notification_settings.admin_alerts_enabled
 *       = false로 끈 관리자도 뺀다(행이 없으면 켜짐).</li>
 *   <li>묶음: 종류마다 push.admin-alert-window-seconds(기본 120초)에 한 번까지. 첫 건은 바로 보내고, 그 사이에 들어온 건은
 *       window가 끝날 때 "새 제보 3건 승인 대기"처럼 한 번에 보낸다(AdminAlertThrottle).</li>
 *   <li>구분: 제목 "[관리]" 접두어, Android 채널 "admin"(앱이 시작할 때 만든다 — 없는 구버전 앱은 기본 채널로 떨어짐),
 *       categoryId "admin", data.type ADMIN_REPORT_PENDING / ADMIN_FEEDBACK / ADMIN_REPORT_FLAGGED / ADMIN_MEMBER_REJOINED /
 *       ADMIN_COMMENT_FLAGGED(reportId·commentId).</li>
 *   <li>본문: 제보 제목·건물·층만. 작성자·문의 내용·연락처는 담지 않는다("새 문의가 도착했어요").
 *       댓글 신고도 제보 제목만 — 댓글 내용·신고자는 담지 않는다.</li>
 * </ul>
 */
@Slf4j
@Service
public class AdminAlertDispatcher implements DisposableBean {

    static final String TITLE_PREFIX = "[관리] ";
    static final String ANDROID_CHANNEL = "admin";
    static final String CATEGORY = "admin";

    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;
    private final AdminAlertThrottle throttle;
    private final ScheduledExecutorService flusher;

    public AdminAlertDispatcher(UserDeviceRepository userDeviceRepository, ExpoPushSender expoPushSender,
                                PushProperties properties) {
        this.userDeviceRepository = userDeviceRepository;
        this.expoPushSender = expoPushSender;
        this.properties = properties;
        this.throttle = new AdminAlertThrottle(Duration.ofSeconds(properties.getAdminAlertWindowSeconds()));
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "admin-alert-flush");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminAlert(AdminAlertEvent event) {
        try {
            offer(event);
        } catch (Exception e) {
            log.warn("관리자 알림 실패 (type={}, id={}): {}", event.type(), event.targetId(), e.getMessage());
        }
    }

    /** 바로 보내거나(보낸 Expo 접수 수를 돌려줌) 묶음으로 미룬다(0). */
    int offer(AdminAlertEvent event) {
        if (!properties.isEnabled()) {
            return 0;
        }
        AdminAlertThrottle.Decision decision = throttle.offer(event, Instant.now());
        if (decision.sendNow() != null) {
            return send(decision.sendNow());
        }
        if (decision.flushAt() != null) {
            long delayMs = Math.max(0, Duration.between(Instant.now(), decision.flushAt()).toMillis());
            flusher.schedule(() -> flush(event.type()), delayMs, TimeUnit.MILLISECONDS);
        }
        return 0;
    }

    private void flush(AdminAlertType type) {
        try {
            AdminAlertThrottle.Batch batch = throttle.drain(type, Instant.now());
            if (batch != null) {
                send(batch);
            }
        } catch (Exception e) {
            log.warn("관리자 알림 묶음 발송 실패 (type={}): {}", type, e.getMessage());
        }
    }

    int send(AdminAlertThrottle.Batch batch) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : userDeviceRepository.findAdminAlertTargets(TokenType.EXPO, UserRole.ADMIN, batch.excludeUserId())) {
            tokens.add(device.getPushToken());
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        String title = title(batch);
        String body = body(batch);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", batch.type().dataType());
        data.put(batch.type().idKey(), batch.latest().targetId());
        if (batch.latest().commentId() != null) {
            data.put("commentId", batch.latest().commentId());
        }
        data.put("count", batch.count());

        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, title, body, data).withChannel(ANDROID_CHANNEL, CATEGORY));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("관리자 알림 푸시: type={}, {}건 묶음, 메시지 {}건 중 {}건 접수",
                batch.type(), batch.count(), messages.size(), result.accepted());
        return result.accepted();
    }

    static String title(AdminAlertThrottle.Batch batch) {
        int n = batch.count();
        return TITLE_PREFIX + switch (batch.type()) {
            case REPORT_PENDING -> n == 1 ? "새 제보 승인 대기" : "새 제보 " + n + "건 승인 대기";
            case FEEDBACK -> n == 1 ? "새 문의" : "새 문의 " + n + "건";
            case REPORT_FLAGGED -> n == 1 ? "신고 누적으로 자동 숨김" : "제보 " + n + "건 신고 누적으로 자동 숨김";
            case MEMBER_REJOINED -> n == 1 ? "이력 있는 회원 재가입" : "이력 있는 회원 " + n + "명 재가입";
            case COMMENT_FLAGGED -> n > 1 ? "신고된 댓글 " + n + "건 검토 필요"
                    : batch.latest().autoHidden() ? "신고 누적으로 댓글 자동 숨김" : "신고된 댓글 검토 필요";
        };
    }

    static String body(AdminAlertThrottle.Batch batch) {
        if (batch.type() == AdminAlertType.FEEDBACK) {
            return batch.count() == 1 ? "새 문의가 도착했어요" : "새 문의 " + batch.count() + "건이 도착했어요";
        }
        if (batch.type() == AdminAlertType.MEMBER_REJOINED) {
            // 회원 카드(priorHistory)에서 확인하도록 안내만 한다. 닉네임·정지 사유 같은 내용은 싣지 않는다.
            return batch.count() == 1
                    ? "정지·신고 이력이 있는 탈퇴 회원이 다시 가입했어요 (회원 #" + batch.latest().targetId() + ")"
                    : "정지·신고 이력이 있는 탈퇴 회원 " + batch.count() + "명이 다시 가입했어요";
        }
        if (batch.type() == AdminAlertType.COMMENT_FLAGGED) {
            // 제보 제목(이미 지도에 공개된 값)만 쓴다. 댓글 내용·신고자·신고 사유는 싣지 않는다 — 관리 탭에서 확인.
            String line = "'" + excerpt(batch.latest().reportTitle()) + "' 제보의 댓글";
            return batch.count() == 1 ? line : "최근: " + line;
        }
        AdminAlertEvent latest = batch.latest();
        String line = latest.reportTitle() + " · " + ReportPushDispatcher.place(latest.buildingName(), latest.floor());
        return batch.count() == 1 ? line : "최근: " + line;
    }

    private static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String trimmed = text.strip();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 29) + "…";
    }

    /** 테스트용 — 묶음 상태를 비운다. */
    void resetThrottle() {
        throttle.reset();
    }

    @Override
    public void destroy() {
        flusher.shutdownNow();
    }
}
