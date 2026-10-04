package com.hongmap.hongmapbackend.push;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Expo Push API 메시지 하나(https://docs.expo.dev/push-notifications/sending-notifications/#message-request-format).
 * data는 프론트 src/lib/pushNotifications.ts routeForNotification이 읽는다.
 * channelId(Android 알림 채널)·categoryId는 관리자 알림만 쓴다 — null이면 JSON에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpoPushMessage(
        String to,
        String title,
        String body,
        Map<String, Object> data,
        String sound,
        String priority,
        String channelId,
        String categoryId
) {
    public static ExpoPushMessage of(String to, String title, String body, Map<String, Object> data) {
        return new ExpoPushMessage(to, title, body, data, "default", "high", null, null);
    }

    public ExpoPushMessage withChannel(String channelId, String categoryId) {
        return new ExpoPushMessage(to, title, body, data, sound, priority, channelId, categoryId);
    }
}
