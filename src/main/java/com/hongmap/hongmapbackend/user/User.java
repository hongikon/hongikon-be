package com.hongmap.hongmapbackend.user;

import jakarta.persistence.Column;
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

    /**
     * 사용자가 앱에서 직접 정한 공개용 닉네임(선택). 없으면 남에게는 {@link #getNickname()} 을 가린 이름이 보인다.
     * 규칙은 AppNicknamePolicy. 운영 DB는 utf8mb4_unicode_ci 라 유니크 인덱스가 대소문자를 가리지 않는다.
     */
    @Column(name = "app_nickname", length = 30, unique = true)
    private String appNickname;

    /** 관리자 화면(/admin/**) 접근 권한. 요청마다 DB에서 확인하므로 바꾸면 즉시 반영된다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

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

    /** null 이면 앱 닉네임을 지워 가린 로그인 닉네임으로 돌아간다. */
    public void changeAppNickname(String appNickname) {
        this.appNickname = appNickname;
    }

    /**
     * 다른 사람에게 보여 줄 이름. 앱 닉네임이 있으면 그대로, 없으면 카카오/Apple 닉네임을 첫 글자만 남기고 가린다.
     * 공개 응답에는 반드시 이 값만 싣는다(원래 닉네임은 실명인 경우가 많다).
     */
    public String getDisplayName() {
        return DisplayNames.of(appNickname, nickname);
    }
}
