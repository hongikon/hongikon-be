package com.hongmap.hongmapbackend.push;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Expo Push API 응답의 data[i] — 보낸 메시지와 같은 순서로 하나씩 온다.
 * 성공이면 status="ok", 실패면 status="error"에 details.error(예: DeviceNotRegistered)가 붙는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushTicket(
        String status,
        String id,
        String message,
        Map<String, Object> details
) {
    public static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    public boolean isDeviceNotRegistered() {
        return "error".equals(status)
                && details != null
                && DEVICE_NOT_REGISTERED.equals(details.get("error"));
    }
}
