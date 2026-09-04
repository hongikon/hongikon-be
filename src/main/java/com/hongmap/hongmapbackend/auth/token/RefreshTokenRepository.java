package com.hongmap.hongmapbackend.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);
}
