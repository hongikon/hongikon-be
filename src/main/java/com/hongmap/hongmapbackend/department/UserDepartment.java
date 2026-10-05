package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저-학과(소속) 정보. is_primary로 주/부학과 구분.
 * 새 소식 푸시 대상에는 더 이상 쓰지 않는다 — 푸시는 게시판 구독(notification.UserBoardSubscription) 기준.
 * 여러 개가 동시에 primary=true인 것을 DB가 막지 못하므로, 서비스 레이어에서
 * "새 primary 지정 시 기존 것 해제"를 같은 트랜잭션에서 처리해야 함.
 */
@Entity
@Table(name = "user_departments",
        // 같은 학과 중복 구독 방지. 운영 DB 에는 없을 수 있어 db/alter_add_unique_user_lists.sql 로 (중복 정리 후) 추가한다.
        uniqueConstraints = @UniqueConstraint(name = "uq_user_department", columnNames = {"user_id", "department_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    /** 주 학과로 지정. 같은 유저의 다른 주 학과 해제(clearPrimaryForUser)는 호출하는 쪽이 먼저 한다. */
    public void markPrimary() {
        this.isPrimary = true;
    }
}
