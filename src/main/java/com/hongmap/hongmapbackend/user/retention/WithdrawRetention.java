package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.news.converter.StringListJsonConverter;
import com.hongmap.hongmapbackend.user.SocialType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 탈퇴 회원의 부정 이용 방지 기록(개인정보 처리방침: 탈퇴 후 1년 분리 보관). 정지 이력이 있거나 신고받은 제보를 쓴 회원만 남는다
 * — 대상 판단과 생성·갱신·만료 삭제는 {@link WithdrawRetentionService}.
 * <ul>
 *   <li>소셜 id 원문은 없다. {@link #socialIdHash} 는 서버 비밀키로 만든 HMAC 이라 재가입 대조에만 쓰이고 거꾸로 풀 수 없다.</li>
 *   <li>users 행은 지워지므로 FK 가 없다. {@link #rejoinedUserId} 도 일반 컬럼이다 — 그 회원이 다시 탈퇴하면 서비스가 비운다.</li>
 *   <li>(social_type, social_id_hash) 유니크 — 같은 계정이 재가입·재탈퇴하면 새 행을 만들지 않고 이 행을 갱신한다.</li>
 * </ul>
 *
 * DB: withdraw_retentions (db/create_withdraw_retentions_table.sql)
 */
@Entity
@Table(
        name = "withdraw_retentions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_withdraw_retentions_social",
                columnNames = {"social_type", "social_id_hash"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawRetention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false, length = 20)
    private SocialType socialType;

    /** HMAC-SHA256("<social_type>:<social_id>") hex 64자. WithdrawRetentionKeys */
    @Column(name = "social_id_hash", nullable = false, length = 64)
    private String socialIdHash;

    /** 탈퇴 시점(여러 번이면 그중 한 번이라도)에 이용 정지(SUSPENDED) 상태였는지 */
    @Column(name = "was_suspended", nullable = false)
    private boolean wasSuspended;

    /** 가장 최근에 남은 정지 사유·시각(정지된 적이 없으면 null) */
    @Column(name = "suspended_reason", length = 200)
    private String suspendedReason;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /** 스냅숏에 담긴 제보 수(탈퇴가 여러 번이면 합계) */
    @Column(name = "report_count", nullable = false)
    private int reportCount;

    /** 그중 신고를 1건 이상 받은 제보 수 */
    @Column(name = "flagged_report_count", nullable = false)
    private int flaggedReportCount;

    /** RetentionSnapshot JSON. 닉네임·이메일·Apple 토큰은 넣지 않는다. */
    @Column(name = "snapshot", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshot;

    /** S3 에 복사해 둔 제보 사진 키(retained/...). 만료 삭제 때 함께 지운다. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "retained_image_keys", columnDefinition = "TEXT")
    private List<String> retainedImageKeys = new ArrayList<>();

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    /** 이 시각이 지나면 행과 사진 사본을 지운다(마지막 탈퇴 + 1년). */
    @Column(name = "retain_until", nullable = false)
    private LocalDateTime retainUntil;

    /** 같은 소셜 계정으로 다시 가입한 회원 id(FK 아님). 그 회원이 다시 탈퇴하면 비운다. */
    @Column(name = "rejoined_user_id")
    private Long rejoinedUserId;

    @Column(name = "rejoined_at")
    private LocalDateTime rejoinedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    WithdrawRetention(SocialType socialType, String socialIdHash) {
        this.socialType = socialType;
        this.socialIdHash = socialIdHash;
    }

    boolean isExpired(LocalDateTime now) {
        return retainUntil != null && !retainUntil.isAfter(now);
    }

    /**
     * 이번 탈퇴를 기록한다. 기존 기록에 이어 붙이는 경우(재가입 후 재탈퇴) 이력은 합치고 보관 기한은 이번 탈퇴부터 다시 센다.
     *
     * @param snapshotJson 지금까지의 탈퇴를 모두 담은 스냅숏(서비스가 합쳐서 넘긴다)
     */
    void recordWithdrawal(boolean suspendedNow, String suspendedReason, LocalDateTime suspendedAt,
                          int reportCount, int flaggedReportCount, String snapshotJson, List<String> newImageKeys,
                          LocalDateTime withdrawnAt, LocalDateTime retainUntil) {
        this.wasSuspended = this.wasSuspended || suspendedNow;
        if (suspendedAt != null) { // 이번에 정지 이력이 없으면 이전 기록의 정지 정보를 남긴다
            this.suspendedReason = suspendedReason;
            this.suspendedAt = suspendedAt;
        }
        this.reportCount += reportCount;
        this.flaggedReportCount += flaggedReportCount;
        this.snapshot = snapshotJson;
        LinkedHashSet<String> keys = new LinkedHashSet<>(retainedImageKeys == null ? List.of() : retainedImageKeys);
        keys.addAll(newImageKeys);
        this.retainedImageKeys = new ArrayList<>(keys);
        this.withdrawnAt = withdrawnAt;
        this.retainUntil = retainUntil;
        // 재가입했던 회원이 다시 탈퇴했으니 연결을 끊는다(users 행이 곧 지워진다).
        this.rejoinedUserId = null;
        this.rejoinedAt = null;
    }

    /** 만료됐지만 아직 정리되지 않은 행을 새 탈퇴에 다시 쓸 때 — 이전 이력을 모두 버린다(사진 사본은 서비스가 지운다). */
    void resetExpired() {
        this.wasSuspended = false;
        this.suspendedReason = null;
        this.suspendedAt = null;
        this.reportCount = 0;
        this.flaggedReportCount = 0;
        this.snapshot = null;
        this.retainedImageKeys = new ArrayList<>();
        this.rejoinedUserId = null;
        this.rejoinedAt = null;
    }

    void linkRejoinedUser(Long userId, LocalDateTime now) {
        this.rejoinedUserId = userId;
        this.rejoinedAt = now;
    }
}
