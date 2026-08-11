package com.hongmap.hongmapbackend.user.dto;

import com.hongmap.hongmapbackend.user.DevicePlatform;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import lombok.Builder;

@Builder
public record DeviceResponse(
        Long id,
        String pushToken,
        TokenType tokenType,
        DevicePlatform platform,
        boolean isActive
) {
    public static DeviceResponse of(UserDevice d) {
        return DeviceResponse.builder()
                .id(d.getId())
                .pushToken(d.getPushToken())
                .tokenType(d.getTokenType())
                .platform(d.getPlatform())
                .isActive(d.isActive())
                .build();
    }
}
