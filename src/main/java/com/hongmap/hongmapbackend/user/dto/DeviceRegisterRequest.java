package com.hongmap.hongmapbackend.user.dto;

import com.hongmap.hongmapbackend.user.DevicePlatform;
import com.hongmap.hongmapbackend.user.TokenType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceRegisterRequest(
        @NotBlank
        String pushToken,

        @NotNull
        TokenType tokenType,

        DevicePlatform platform
) {
}
