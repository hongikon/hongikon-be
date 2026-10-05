package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.common.persistence.UniqueConflictRetry;
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
 * 같은 토큰 등록이 거의 동시에 두 번 오면(앱 시작·로그인 직후 연달아 등록) 둘 다 "없음"을 보고 INSERT 해 늦은 쪽이
 * uq_device_token 위반으로 500 이 났다 → UniqueConflictRetry 로 새 트랜잭션에서 한 번 더 돌려, 먼저 들어간 행을
 * 지우고 다시 만든다(나중 요청의 유저가 주인).
 */
@Service
@RequiredArgsConstructor
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;
    private final UniqueConflictRetry uniqueConflictRetry;

    /** 트랜잭션은 UniqueConflictRetry 가 시도마다 새로 연다(여기에 @Transactional 을 붙이지 않는다). */
    public DeviceResponse register(Long userId, DeviceRegisterRequest request) {
        return uniqueConflictRetry.execute("device-register", () -> registerOnce(userId, request));
    }

    private DeviceResponse registerOnce(Long userId, DeviceRegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        userDeviceRepository.findByPushToken(request.pushToken())
                .ifPresent(existing -> {
                    userDeviceRepository.delete(existing);
                    // Flush the DELETE now: with IDENTITY ids, save() below INSERTs immediately,
                    // which would otherwise hit uq_device_token before the deferred DELETE runs.
                    userDeviceRepository.flush();
                });

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
