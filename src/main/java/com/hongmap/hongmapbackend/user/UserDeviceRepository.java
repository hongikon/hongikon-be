package com.hongmap.hongmapbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPushToken(String pushToken);

    List<UserDevice> findByUserIdAndActiveTrue(Long userId);

    void deleteByUserId(Long userId);
}
