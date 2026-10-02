package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.report.Report;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

/**
 * 조회 수 하루 1회 중복 방지 표식. viewer_key 는 HMAC(서버 비밀키, 날짜 + 사용자 id 또는 설치 id) 앞 16바이트(hex) —
 * 원래 id 를 저장하지 않고, 날짜가 섞여 날마다 값이 달라 하루를 넘겨 같은 사람을 이어 붙일 수 없다. IP 는 쓰지 않는다.
 * 2일 지난 행은 ReportFollowScheduler 가 지운다. 사람(users)과 FK 로 잇지 않는다.
 *
 * DB: report_view_marks (db/create_report_community_tables.sql) — 쓰기는 ReportEngagementRepository.insertViewMark(INSERT IGNORE)
 */
@Entity
@Table(name = "report_view_marks",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_view_marks", columnNames = {"report_id", "viewer_key", "view_date"}),
        indexes = @Index(name = "ix_report_view_marks_date", columnList = "view_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportViewMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Report report;

    @Column(name = "viewer_key", nullable = false, length = 32)
    private String viewerKey;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;
}
