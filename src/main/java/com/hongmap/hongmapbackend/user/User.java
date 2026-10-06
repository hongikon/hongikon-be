package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.apple.AppleRefreshTokenConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@EntityListeners(MemberCodeAssigner.class)
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

    /** 이용 정지 여부(약관 제10조). 정지돼도 로그인은 되고 쓰기만 막힌다. UserStatus 참고 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** 정지 사유(관리자 메모). 해제하면 비운다. */
    @Column(name = "suspended_reason", length = 200)
    private String suspendedReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /**
     * 공개용 회원 번호(영문 대문자·숫자 10자리, 예: K7Q2M9XA4D). 앱 설정 화면·관리자 콘솔에 보여 주는 값으로, 순번인 id 대신 쓴다
     * (id 는 가입자 수가 드러나고 추측하기 쉽다). id 는 그대로 PK·JWT sub 로 쓴다.
     * 가입할 때 MemberCodeAssigner 가 채운다. 유니크 인덱스 uq_users_member_code (db/alter_users_add_member_code.sql).
     */
    @Column(name = "member_code", nullable = false, unique = true, length = 10)
    private String memberCode;

    /**
     * 운영진이 인증해 붙인 공식 이름(예: "경영대학 학생회"). 있으면 앱 닉네임 대신 이 이름이 보이고 공식 배지가 붙는다.
     * 학생회 등이 문의로 신청하면 관리자가 지정한다(PUT /admin/users/{id}/official). 일반 회원은 '학생회'·'공식'을 닉네임에 못 쓴다
     * (AppNicknamePolicy) — 공식 계정을 흉내 낼 수 없게. db/alter_users_add_official_name.sql
     */
    @Column(name = "official_name", length = 30)
    private String officialName;

    @Builder
    public User(String socialId, SocialType socialType, String email, String nickname) {
        this.socialId = socialId;
        this.socialType = socialType;
        this.email = email;
        this.nickname = nickname;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedReason = reason;
        this.suspendedAt = LocalDateTime.now();
    }

    /** 관리자 지정·해제. 관리자 콘솔(AdminUserService)에서만 부른다. */
    public void changeRole(UserRole role) {
        this.role = role;
    }

    public void unsuspend() {
        this.status = UserStatus.ACTIVE;
        this.suspendedReason = null;
        this.suspendedAt = null;
    }

    /** 가입 시 한 번만 정한다(MemberCodeAssigner). 이미 있으면 바꾸지 않는다. */
    void assignMemberCode(String memberCode) {
        if (this.memberCode == null) {
            this.memberCode = memberCode;
        }
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

    /** null 이면 앱 닉네임을 지워 가린 로그인 닉네임으로 돌아간다. */
    public void changeAppNickname(String appNickname) {
        this.appNickname = appNickname;
    }

    /**
     * 다른 사람에게 보여 줄 이름. 앱 닉네임이 있으면 그대로, 없으면 카카오/Apple 닉네임을 첫 글자만 남기고 가린다.
     * 공개 응답에는 반드시 이 값만 싣는다(원래 닉네임은 실명인 경우가 많다).
     */
    public String getDisplayName() {
        return officialName != null ? officialName : DisplayNames.of(appNickname, nickname);
    }

    /** 운영진이 인증한 공식 계정인지(공식 배지). */
    public boolean isOfficial() {
        return officialName != null;
    }

    /** 공식 이름을 붙인다. null 이면 떼어 원래 이름(앱 닉네임·가린 닉네임)으로 돌아간다. */
    public void changeOfficialName(String officialName) {
        this.officialName = officialName;
    }
}
