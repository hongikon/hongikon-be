package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.apple.AppleRefreshTokenConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_users_social",
                columnNames = {"social_type", "social_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "social_id", nullable = false, length = 100)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false, length = 20)
    private SocialType socialType;

    @Column(length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** 관리자 화면(/admin/**) 접근 권한. 요청마다 DB에서 확인하므로 바꾸면 즉시 반영된다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    /**
     * Sign in with Apple 사용자만: 탈퇴 시 Apple에 폐기(revoke) 요청할 refresh 토큰과, 그 토큰을 받은 client_id(번들 ID).
     * Apple 키 설정이 없을 때 가입한 사용자는 비어 있다(다음 로그인 때 채워진다).
     */
    @Convert(converter = AppleRefreshTokenConverter.class) // AES-GCM 암호화 저장(APPLE_TOKEN_ENC_KEY)
    @Column(name = "apple_refresh_token", length = 512)
    private String appleRefreshToken;

    @Column(name = "apple_client_id", length = 100)
    private String appleClientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String socialId, SocialType socialType, String email, String nickname) {
        this.socialId = socialId;
        this.socialType = socialType;
        this.email = email;
        this.nickname = nickname;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 로그인할 때마다 Apple이 새로 준 refresh 토큰으로 바꿔 둔다(가장 최근 것만 있으면 폐기할 수 있다). */
    public void linkAppleCredential(String appleRefreshToken, String appleClientId) {
        this.appleRefreshToken = appleRefreshToken;
        this.appleClientId = appleClientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
