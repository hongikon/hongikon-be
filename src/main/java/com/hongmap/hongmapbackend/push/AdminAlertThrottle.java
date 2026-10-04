package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.admin.AdminAlertType;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 관리자 알림 묶음 처리 — 종류(AdminAlertType)마다 window 안에 푸시를 최대 한 번만 보낸다.
 * <ul>
 *   <li>마지막 발송에서 window가 지났고 쌓인 것이 없으면 바로 보낸다(첫 건은 지연 없음).</li>
 *   <li>아니면 쌓아 두고, 처음 쌓일 때 "마지막 발송 + window" 시각에 한 번 묶어 보내도록 예약을 요청한다
 *       ("새 제보 3건 승인 대기").</li>
 * </ul>
 * 서버 메모리 기준이다(인스턴스 하나 전제, 재시작하면 초기화). 시각은 호출하는 쪽이 넘긴다 — 테스트에서 시간을 정할 수 있게.
 */
final class AdminAlertThrottle {

    /**
     * 한 번에 보낼 묶음.
     *
     * @param latest        마지막으로 들어온 이벤트(data의 id·본문에 쓴다)
     * @param excludeUserId 묶인 이벤트가 모두 같은 유저가 만든 것이면 그 유저(자기 행동 알림 제외), 섞였으면 null
     */
    record Batch(AdminAlertType type, int count, AdminAlertEvent latest, Long excludeUserId) {
        static Batch of(AdminAlertEvent event) {
            return new Batch(event.type(), 1, event, event.actorUserId());
        }
    }

    /** offer 결과: sendNow가 있으면 바로 보내고, flushAt이 있으면 그 시각에 drain을 예약한다. 둘 다 없으면 이미 예약돼 있음. */
    record Decision(Batch sendNow, Instant flushAt) {
    }

    private static final class State {
        Instant lastSentAt;
        int pendingCount;
        AdminAlertEvent pendingLatest;
        Long pendingActor;
        boolean pendingMixedActors;
        boolean flushScheduled;
    }

    private final Duration window;
    private final Map<AdminAlertType, State> states = new EnumMap<>(AdminAlertType.class);

    AdminAlertThrottle(Duration window) {
        this.window = window;
    }

    synchronized Decision offer(AdminAlertEvent event, Instant now) {
        State state = states.computeIfAbsent(event.type(), t -> new State());
        boolean windowPassed = state.lastSentAt == null || !now.isBefore(state.lastSentAt.plus(window));
        if (windowPassed && state.pendingCount == 0) {
            state.lastSentAt = now;
            return new Decision(Batch.of(event), null);
        }
        if (state.pendingCount == 0) {
            state.pendingActor = event.actorUserId();
            state.pendingMixedActors = false;
        } else if (!Objects.equals(state.pendingActor, event.actorUserId())) {
            state.pendingMixedActors = true;
        }
        state.pendingCount++;
        state.pendingLatest = event;
        if (state.flushScheduled) {
            return new Decision(null, null);
        }
        state.flushScheduled = true;
        Instant flushAt = state.lastSentAt == null ? now : state.lastSentAt.plus(window);
        return new Decision(null, flushAt.isBefore(now) ? now : flushAt);
    }

    /** 예약된 시각에 부른다. 쌓인 것이 없으면 null. */
    synchronized Batch drain(AdminAlertType type, Instant now) {
        State state = states.get(type);
        if (state == null) {
            return null;
        }
        state.flushScheduled = false;
        if (state.pendingCount == 0) {
            return null;
        }
        Batch batch = new Batch(type, state.pendingCount, state.pendingLatest,
                state.pendingMixedActors ? null : state.pendingActor);
        state.pendingCount = 0;
        state.pendingLatest = null;
        state.pendingActor = null;
        state.pendingMixedActors = false;
        state.lastSentAt = now;
        return batch;
    }

    synchronized void reset() {
        states.clear();
    }
}
