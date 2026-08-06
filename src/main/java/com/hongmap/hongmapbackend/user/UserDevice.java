package com.hongmap.hongmapbackend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_devices",
        uniqueConstraints = @UniqueConstraint(name = "uq_device_token", columnNames = "push_token"),
        indexes = @Index(name = "ix_device_user", columnList = "user_id, is_active")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "push_token", nullable = false, length = 255)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DevicePlatform platform;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserDevice(User user, String pushToken, TokenType tokenType, DevicePlatform platform) {
        this.user = user;
        this.pushToken = pushToken;
        this.tokenType = tokenType;
        this.platform = platform;
    }

    public void deactivate() {
        this.active = false;
    }

    public void renewToken(String pushToken) {
        this.pushToken = pushToken;
        this.active = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserDevice that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
