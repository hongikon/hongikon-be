package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 실시간 제보. 로그인 유저가 지도의 특정 지점(건물+층)에 올리는 시간 한정 이벤트 정보.
 * ends_at이 지나면 지도에서 자동으로 빠짐 (조회 시 status/ends_at 조건으로 필터링).
 *
 * DB: reports
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 이벤트 정보가 위치한 건물. 층 단위 정보이므로 필수 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** 건물 내 층. 층 단위가 이 기능의 핵심 정보라 필수 */
    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ReportCategory category;

    /** category가 ETC일 때만 사용하는 자유 텍스트 세분화 라벨 */
    @Column(name = "custom_category_label", length = 50)
    private String customCategoryLabel;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", length = 500)
    private String content;

    /** 첨부 사진의 S3 키(reports/{uuid}.jpg). URL 은 응답 때마다 presigned GET 으로 만든다. 사진이 없으면 null */
    @Column(name = "image_key", length = 200)
    private String imageKey;

    /** UTC 저장, 표시 시 KST 변환 */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** 이 시각 이후 지도에서 내려감 */
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    /** 관리자 검토 메모. 반려(REJECTED) 사유 등 */
    @Column(name = "moderation_note", length = 200)
    private String moderationNote;

    /** 관리자가 마지막으로 상태를 바꾼 시각. 값이 있으면 신고 누적 자동 숨김을 하지 않는다(관리자 판단 우선) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 반려·삭제된 제보의 사진을 S3 에서 지운 뒤 연결을 끊는다. */
    public void clearImage() {
        this.imageKey = null;
    }

    /** 관리자 검토 결과 반영. note 는 비우면 기존 메모를 지운다. */
    public void moderate(ReportStatus status, String note, LocalDateTime reviewedAt) {
        this.status = status;
        this.moderationNote = note;
        this.reviewedAt = reviewedAt;
    }
}
