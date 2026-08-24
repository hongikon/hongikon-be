package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.user.dto.DeviceRegisterRequest;
import com.hongmap.hongmapbackend.user.dto.DeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "기기 등록", description = "푸시 알림 수신을 위해 로그인한 사용자의 기기를 등록합니다.")
    @PostMapping("/users/me/devices")
    public ResponseEntity<DeviceResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeviceRegisterRequest request
    ) {
        var response = userDeviceService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "기기 비활성화", description = "등록된 기기를 비활성화하여 더 이상 푸시 알림을 받지 않도록 합니다.")
    @DeleteMapping("/users/me/devices/{deviceId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId
    ) {
        userDeviceService.deactivate(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
