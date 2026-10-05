package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserSuspensionChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이용 정지·해제 알림 푸시(Expo). 관리자 POST /admin/users/{id}/suspend|unsuspend 가 커밋된 뒤 별도 스레드에서 돈다 —
 * 관리자 응답을 늦추지 않고, 발송 실패는 로그만 남긴다(정지·해제 자체는 이미 끝났다).
 * <ul>
 *   <li>법무 검토(공정위 2019 불공정약관 심사 지침): 이용 제한은 사유를 알리고 이의 제기 기회를 줘야 한다 — 정지 알림 본문에 사유와
 *       "14일 안에 hongikonsupport@gmail.com" 안내를 싣는다.</li>
 *   <li>서비스 고지라 알림 설정(user_notification_settings)과 관계없이 보낸다. 등록된 활성 Expo 기기가 없으면 아무것도 가지 않는다
 *       (앱은 GET /users/me 의 status·suspendedReason 으로 배너를 띄우고, 쓰기 요청은 사유가 담긴 403 을 받는다).</li>
 *   <li>data.type: ACCOUNT_SUSPENDED / ACCOUNT_UNSUSPENDED.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountStatusPushDispatcher {

    static final String DATA_TYPE_SUSPENDED = "ACCOUNT_SUSPENDED";
    static final String DATA_TYPE_UNSUSPENDED = "ACCOUNT_UNSUSPENDED";
    static final String TITLE_SUSPENDED = "이용이 제한됐어요";
    static final String TITLE_UNSUSPENDED = "이용 제한이 풀렸어요";
    static final String OBJECTION_NOTICE = "이의가 있으면 14일 안에 hongikonsupport@gmail.com 으로 알려 주세요";
    static final String BODY_UNSUSPENDED = "다시 제보·신고·문의를 할 수 있어요";
    private static final int MAX_REASON_LENGTH = 80;

    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuspensionChanged(UserSuspensionChangedEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.warn("이용 정지 알림 실패 (userId={}, suspended={}): {}", event.userId(), event.suspended(), e.getMessage());
        }
    }

    /** 동기 실행 — 이벤트 리스너와 테스트가 쓴다. Expo가 받아들인 메시지 수를 돌려준다. */
    public int dispatch(UserSuspensionChangedEvent event) {
        if (!properties.isEnabled()) {
            return 0;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : userDeviceRepository.findByUserIdAndActiveTrue(event.userId())) {
            if (device.getTokenType() == TokenType.EXPO) {
                tokens.add(device.getPushToken());
            }
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        String title = event.suspended() ? TITLE_SUSPENDED : TITLE_UNSUSPENDED;
        String body = event.suspended() ? suspendedBody(event.reason()) : BODY_UNSUSPENDED;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.suspended() ? DATA_TYPE_SUSPENDED : DATA_TYPE_UNSUSPENDED);

        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, title, body, data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("이용 정지 알림 푸시: userId={}, suspended={}, 메시지 {}건 중 {}건 접수",
                event.userId(), event.suspended(), messages.size(), result.accepted());
        return result.accepted();
    }

    static String suspendedBody(String reason) {
        String trimmed = reason == null || reason.isBlank() ? "-" : reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            trimmed = trimmed.substring(0, MAX_REASON_LENGTH - 1) + "…";
        }
        return "사유: " + trimmed + "\n" + OBJECTION_NOTICE;
    }
}
