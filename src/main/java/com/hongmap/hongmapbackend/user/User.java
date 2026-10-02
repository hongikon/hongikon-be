package com.hongmap.hongmapbackend.user;

import jakarta.persistence.Column;
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
