package com.hongmap.hongmapbackend.auth.apple;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PendingAppleRevocationRepository extends JpaRepository<PendingAppleRevocation, Long> {

    List<PendingAppleRevocation> findTop50ByNextAttemptAtLessThanEqualOrderByIdAsc(LocalDateTime now);
}
