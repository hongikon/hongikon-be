package com.hongmap.hongmapbackend.auth.token;

import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 로그인 세션(기기·브라우저) 하나 = row 하나. 그 세션의 현재 refresh 토큰을 해시로 저장한다.
 * 예전엔 유저당 1 row(user_id UNIQUE)라 다른 기기에서 로그인하거나 재발급이 겹치면 다른 세션이 로그아웃됐다.
 * reissue 시 이 row를 로테이션하고(직전 해시는 previous_token_hash 에 잠깐 남김 — 동시 재발급 유예),
 * logout 시 이 row만 삭제한다(access 토큰은 만료 전까지 그대로 유효). 탈퇴 시엔 유저의 모든 row 삭제.
 * 스키마: db/create_refresh_tokens_table.sql + db/alter_refresh_tokens_multi_session.sql
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_token_user", columnList = "user_id"),
                @Index(name = "idx_refresh_token_prev_hash", columnList = "previous_token_hash")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** 마지막 로테이션 직전의 해시. rotated_at 부터 유예 시간 안에만 의미가 있다(동시 재발급·응답 유실 재시도). */
    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막 로테이션(=마지막 사용) 시각. 세션 수 상한을 넘으면 이 값이 가장 오래된 세션부터 지운다. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 현재 토큰을 새 토큰으로 바꾸고, 바뀌기 전 해시는 유예용으로 previous 에 남긴다. */
    public void rotate(String newTokenHash, LocalDateTime expiresAt, LocalDateTime now) {
        this.previousTokenHash = this.tokenHash;
        this.rotatedAt = now;
        this.tokenHash = newTokenHash;
        this.expiresAt = expiresAt;
    }

    /** previous 해시로 들어온 요청이 유예 시간 안인지. rotated_at 이 없으면(로테이션 전 row) false. */
    public boolean isWithinReuseGrace(LocalDateTime now, long graceSeconds) {
        return rotatedAt != null && graceSeconds > 0 && !now.isAfter(rotatedAt.plusSeconds(graceSeconds));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshToken that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
