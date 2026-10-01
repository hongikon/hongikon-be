package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 만들어 둔 메시지들을 {@value ExpoPushClient#MAX_BATCH_SIZE}개씩 묶어 보내고, DeviceNotRegistered 기기를 비활성화한다.
 * 새 소식(NewsPushDispatcher)·제보(ReportPushDispatcher) 푸시가 같이 쓴다. 배치 하나가 실패해도 로그만 남기고 다음 배치를 보낸다.
 * 영수증(receipt) 조회는 하지 않는다 — 발송 직후 응답(ticket)에서 바로 알 수 있는 DeviceNotRegistered만 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpoPushSender {

    private final ExpoPushClient expoPushClient;
    private final UserDeviceRepository userDeviceRepository;

    /** @param accepted Expo가 받아들인(status=ok) 메시지 수, @param deactivated 비활성화한 토큰 수 */
    public record Result(int accepted, int deactivated) {
        public static final Result EMPTY = new Result(0, 0);
    }

    /** 예외를 던지지 않는다. */
    public Result sendAll(List<ExpoPushMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Result.EMPTY;
        }
        int accepted = 0;
        Set<String> unregisteredTokens = new LinkedHashSet<>();
        for (int from = 0; from < messages.size(); from += ExpoPushClient.MAX_BATCH_SIZE) {
            List<ExpoPushMessage> batch = messages.subList(from, Math.min(from + ExpoPushClient.MAX_BATCH_SIZE, messages.size()));
            try {
                List<ExpoPushTicket> tickets = expoPushClient.send(batch);
                for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                    ExpoPushTicket ticket = tickets.get(i);
                    if ("ok".equals(ticket.status())) {
                        accepted++;
                    } else if (ticket.isDeviceNotRegistered()) {
                        unregisteredTokens.add(batch.get(i).to());
                    } else {
                        log.warn("Expo 푸시 거부: to={}, message={}, details={}", batch.get(i).to(), ticket.message(), ticket.details());
                    }
                }
            } catch (Exception e) {
                log.warn("Expo 푸시 배치 발송 실패 ({}건): {}", batch.size(), e.getMessage());
            }
        }
        deactivate(unregisteredTokens);
        return new Result(accepted, unregisteredTokens.size());
    }

    private void deactivate(Set<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        try {
            int count = userDeviceRepository.deactivateByPushTokens(tokens);
            log.info("DeviceNotRegistered 기기 비활성화: {}대", count);
        } catch (Exception e) {
            log.warn("푸시 기기 비활성화 실패 ({}개 토큰): {}", tokens.size(), e.getMessage());
        }
    }
}
