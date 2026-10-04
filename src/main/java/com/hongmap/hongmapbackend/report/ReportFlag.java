package com.hongmap.hongmapbackend.report;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 제보 신고. (report_id, user_id) 유니크 — 동일 유저의 중복 신고 방지.
 * 운영 DB 에는 db/create_report_flags_table.sql 의 UNIQUE KEY uq_flag 가 이미 있다. 엔티티에도 같은 이름으로 적어
 * H2 테스트 스키마(create-drop)에도 걸리게 한다(ddl-auto=validate 는 유니크 키를 비교하지 않아 운영엔 영향 없음).
 *
 * DB: report_flags
 */
@Entity
@Table(name = "report_flags",
        uniqueConstraints = @UniqueConstraint(name = "uq_flag", columnNames = {"report_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReportFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** FALSE_INFO / SPAM / INAPPROPRIATE / PRIVACY / ETC */
    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
