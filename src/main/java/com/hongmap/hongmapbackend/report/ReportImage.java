package com.hongmap.hongmapbackend.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 제보에 붙은 사진 1장(제보당 최대 {@link Report#MAX_IMAGES}장).
 * S3 객체 키만 저장한다 — 버킷은 비공개이고 응답할 때마다 presigned GET URL 을 새로 만든다.
 *
 * DB: report_images (report_id FK ON DELETE CASCADE, image_key UNIQUE)
 */
@Entity
@Table(name = "report_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 제보가 지워지면 DB 가 함께 지운다(JPQL 일괄 삭제·건물 삭제 등 JPA 를 거치지 않는 경로 포함). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Report report;

    /** 서버가 메타데이터를 지워 저장한 S3 키(reports/{uuid}.jpg|png) */
    @Column(name = "image_key", nullable = false, unique = true, length = 200)
    private String imageKey;

    /** 앱이 보낸 순서(0부터). 응답의 imageUrls 순서이고 0번이 대표 사진(imageUrl) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    ReportImage(Report report, String imageKey, int sortOrder) {
        this.report = report;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }
}
