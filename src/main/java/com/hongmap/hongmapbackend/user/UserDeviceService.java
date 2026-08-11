package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.user.dto.DeviceRegisterRequest;
import com.hongmap.hongmapbackend.user.dto.DeviceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * push_token이 UNIQUE라서, 같은 토큰으로 다시 등록 요청이 오면(재로그인 등) 기존 행을
 * 지우고 새로 만듦 — 다른 유저가 같은 기기(토큰)로 로그인해도 소유권이 깔끔하게 넘어감.
 */
@Service
@RequiredArgsConstructor
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;

    @Transactional
    public DeviceResponse register(Long userId, DeviceRegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        userDeviceRepository.findByPushToken(request.pushToken())
                .ifPresent(userDeviceRepository::delete);

        UserDevice device = UserDevice.builder()
                .user(user)
                .pushToken(request.pushToken())
                .tokenType(request.tokenType())
                .platform(request.platform())
                .build();

        UserDevice saved = userDeviceRepository.save(device);
        return DeviceResponse.of(saved);
    }

    @Transactional
    public void deactivate(Long userId, Long deviceId) {
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 기기입니다."));

        if (!device.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 기기만 해제할 수 있습니다.");
        }

        device.deactivate();
        userDeviceRepository.save(device);
    }
}
