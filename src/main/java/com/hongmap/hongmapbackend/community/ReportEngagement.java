package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.report.Report;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 제보별 커뮤니티 상태 한 행(행이 없으면 기본값: 조회 0, 작성자 알림 켜짐, 이정표 0).
 * 쓰기는 경쟁을 피하려고 ReportEngagementRepository 의 조건부·upsert 네이티브 쿼리로만 한다. reports 에 컬럼을 더하지 않은 건
 * 다른 PR(#14 등)과 Report 엔티티·reports 테이블 충돌을 피하려는 것.
 *
 * <ul>
 *   <li>view_count: 조회 수(익명 집계). 하루 1회 중복 방지는 report_view_marks.</li>
 *   <li>author_notify_enabled: 작성자의 "이 제보 알림"(댓글·답글·🔥 이정표). 기본 켜짐.</li>
 *   <li>fire_milestone_sent: 마지막으로 보낸 🔥 이정표(10·50·100). 끄고 다시 눌러도 같은 이정표는 다시 보내지 않는다.</li>
 * </ul>
 *
 * DB: report_engagement (PK report_id, FK reports ON DELETE CASCADE)
 */
@Entity
@Table(name = "report_engagement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEngagement {

    public static final boolean DEFAULT_AUTHOR_NOTIFY_ENABLED = true;

    @Id
    @Column(name = "report_id")
    private Long reportId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Report report;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "author_notify_enabled", nullable = false)
    private boolean authorNotifyEnabled = DEFAULT_AUTHOR_NOTIFY_ENABLED;

    @Column(name = "fire_milestone_sent", nullable = false)
    private int fireMilestoneSent;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
