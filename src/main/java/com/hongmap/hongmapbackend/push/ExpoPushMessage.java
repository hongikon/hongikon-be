package com.hongmap.hongmapbackend.push;

import java.util.Map;

/**
 * Expo Push API 메시지 하나(https://docs.expo.dev/push-notifications/sending-notifications/#message-request-format).
 * data는 프론트 src/lib/pushNotifications.ts routeForNotification이 읽는다.
 */
public record ExpoPushMessage(
        String to,
        String title,
        String body,
        Map<String, Object> data,
        String sound,
        String priority
) {
    public static ExpoPushMessage of(String to, String title, String body, Map<String, Object> data) {
        return new ExpoPushMessage(to, title, body, data, "default", "high");
    }
}
