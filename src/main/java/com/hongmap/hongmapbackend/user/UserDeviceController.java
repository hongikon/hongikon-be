package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.user.dto.DeviceRegisterRequest;
import com.hongmap.hongmapbackend.user.dto.DeviceResponse;
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

    @PostMapping("/users/me/devices")
    public ResponseEntity<DeviceResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeviceRegisterRequest request
    ) {
        var response = userDeviceService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/users/me/devices/{deviceId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId
    ) {
        userDeviceService.deactivate(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
