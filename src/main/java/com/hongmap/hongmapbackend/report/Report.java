package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /** 제보 1건에 붙일 수 있는 사진 수 */
    public static final int MAX_IMAGES = 3;

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

    /**
     * 장소 설명(최대 60자). 앱이 지도 핀 근처 건물로 "제4공학관(T동) 근처"처럼 채워 주고, 작성자가 "T동 1층 로비 앞"처럼 고칠 수 있다.
     * 없으면(예전 앱) 앱이 좌표로 가까운 건물을 찾아 보여 준다. db/alter_reports_add_place_label.sql
     */
    @Column(name = "place_label", length = 60)
    private String placeLabel;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", length = 2000)
    private String content;

    /**
     * 첨부 사진(최대 {@link #MAX_IMAGES}장, sort_order 순). URL 은 응답 때마다 presigned GET 으로 만든다.
     * 목록 화면에서 제보마다 따로 읽지 않게 IN 절로 묶어 읽는다(@BatchSize).
     */
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    @Builder.Default
    private List<ReportImage> images = new ArrayList<>();

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

    /**
     * "새 제보"로 처음 지도에 뜬 시각(UTC) — 승인(PENDING → ACTIVE) 때 이미 시작했으면 승인 시각, 예정 제보면 시작 알림을 보낸 시각.
     * 새 제보 알림 다이제스트(NewReportDigestScheduler)가 이 값으로 "마지막 알림 뒤 새로 뜬 제보"를 고른다.
     * reviewed_at 과 달리 숨김 해제·재승인으로 바뀌지 않는다. null 이면 아직 새 제보로 공개되지 않았다(또는 이 컬럼 전 제보).
     * db/alter_reports_add_published_at.sql
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * 승인 대기(PENDING) 리마인드 푸시(AdminReportReminder)에 이 제보가 들어간 횟수. 최대 2(30분·2시간 기준).
     * 스케줄러가 조건부 UPDATE로 올려 선점하므로 서버가 여러 대이거나 재시작해도 같은 단계를 두 번 보내지 않는다.
     */
    @Column(name = "admin_reminder_count", nullable = false, columnDefinition = "TINYINT")
    @Builder.Default
    private int adminReminderCount = 0;

    /** 마지막으로 리마인드에 들어간 시각(UTC). 한 번도 안 들어갔으면 null */
    @Column(name = "admin_reminded_at")
    private LocalDateTime adminRemindedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 사진 키를 순서대로(없으면 빈 목록). */
    public List<String> getImageKeys() {
        return images.stream().map(ReportImage::getImageKey).toList();
    }

    /** 검증·정리를 마친 키를 순서대로 붙인다. 기존 사진은 그대로 두고 뒤에 이어 붙인다. */
    public void addImages(List<String> imageKeys) {
        if (images.size() + imageKeys.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("사진은 최대 " + MAX_IMAGES + "장까지 붙일 수 있습니다.");
        }
        for (String key : imageKeys) {
            images.add(new ReportImage(this, key, images.size()));
        }
    }

    /** 반려·삭제된 제보의 사진 연결을 끊고(행 삭제) 지운 키를 돌려준다 — 호출한 쪽이 S3 에서 지운다. */
    public List<String> clearImages() {
        List<String> removed = getImageKeys();
        images.clear();
        return removed;
    }

    /** 새 제보로 처음 공개된 시각을 남긴다. 이미 있으면 그대로 둔다(숨김 해제·재승인은 "새" 제보가 아니다). */
    public void markPublished(LocalDateTime publishedAt) {
        if (this.publishedAt == null) {
            this.publishedAt = publishedAt;
        }
    }

    /** 관리자 검토 결과 반영. note 는 비우면 기존 메모를 지운다. */
    public void moderate(ReportStatus status, String note, LocalDateTime reviewedAt) {
        this.status = status;
        this.moderationNote = note;
        this.reviewedAt = reviewedAt;
    }
}
