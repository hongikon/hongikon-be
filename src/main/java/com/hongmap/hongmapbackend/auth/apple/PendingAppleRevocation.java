package com.hongmap.hongmapbackend.auth.apple;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탈퇴 때 Apple 토큰 폐기(revoke)가 실패한 건. {@link AppleRevocationService} 가 주기적으로 다시 시도하고,
 * 성공하거나 시도 한도를 넘으면 지운다. 사용자 행은 이미 지워졌으므로 user_id 는 없다(토큰은 암호화 저장).
 *
 * DB: apple_pending_revocations (db/create_apple_pending_revocations.sql)
 */
@Entity
@Table(name = "apple_pending_revocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingAppleRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = AppleRefreshTokenConverter.class)
    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    public PendingAppleRevocation(String refreshToken, String clientId, LocalDateTime now, LocalDateTime nextAttemptAt) {
        this.refreshToken = refreshToken;
        this.clientId = clientId;
        this.attempts = 1;
        this.createdAt = now;
        this.nextAttemptAt = nextAttemptAt;
    }

    void failedAgain(LocalDateTime nextAttemptAt) {
        this.attempts++;
        this.nextAttemptAt = nextAttemptAt;
    }
}
