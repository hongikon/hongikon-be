package com.hongmap.hongmapbackend.admin;

import jakarta.persistence.Column;
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
 * 운영진이 회원의 가려진 개인정보(지금은 로그인 닉네임 원문 하나)를 열람한 기록 한 줄 — 누가·언제·누구의·무엇을(·왜).
 * 「개인정보의 안전성 확보조치 기준」 제8조(접속기록 보관·점검)에 맞춰 열람할 때마다 남기고, 열람한 값 자체는 남기지 않는다.
 *
 * users 와 FK 를 걸지 않는다(admin_user_id·target_user_id 둘 다): 탈퇴하면 users 행이 지워지는데, 기록은 그 뒤에도
 * 보관 기간 동안 남아야 하고(누가 언제 열람했는지가 기록의 목적), ON DELETE SET NULL 이면 "누구의 것"이 사라진다.
 * 그래서 숫자 id 만 남긴다 — 탈퇴 뒤에는 이 id 로 되짚을 회원 정보가 없으므로 기록 자체는 개인을 알아볼 수 없는 값이다.
 * 보관 기간이 지난 행은 {@link AdminPiiAccessLogPurger} 가 지운다.
 *
 * DB: admin_pii_access_logs (db/create_admin_pii_access_logs_table.sql)
 */
@Entity
@Table(name = "admin_pii_access_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminPiiAccessLog {

    /** 열람 대상 항목. 지금은 로그인 닉네임 하나뿐이다. */
    public static final String FIELD_LOGIN_NICKNAME = "LOGIN_NICKNAME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 열람한 관리자 users.id */
    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    /** 열람 대상 회원 users.id */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(nullable = false, length = 40)
    private String field;

    /** 관리자가 남긴 열람 사유(선택, 100자 이내). 예: "신고 3건 — 동일인 여부 확인" */
    @Column(length = 100)
    private String purpose;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    public AdminPiiAccessLog(Long adminUserId, Long targetUserId, String field, String purpose, LocalDateTime accessedAt) {
        this.adminUserId = adminUserId;
        this.targetUserId = targetUserId;
        this.field = field;
        this.purpose = purpose;
        this.accessedAt = accessedAt;
    }
}
