package com.hongmap.hongmapbackend.feedback;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 앱 설정 > 문의하기로 들어온 내용. 비로그인(게스트)도 보낼 수 있어 user 는 비어 있을 수 있다.
 *
 * DB: feedback
 */
@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인한 상태로 보냈으면 작성자. 탈퇴 시 NULL 로 남긴다(FK ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 답변받을 연락처(선택) */
    @Column(length = 100)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 참고 사진 S3 키들(최대 3장, 쉼표로 이음). 로그인해서 보낸 문의만(공식 계정 신청의 확인 자료 등). 제보 사진과 같은 방식으로
     * 올리고 검사·메타데이터 제거를 거친다(ReportImageService). 처리 완료하거나 작성자가 탈퇴하면 지운다 — 확인이 끝나면 남기지 않는다.
     */
    @Column(name = "image_keys", length = 600)
    private String imageKeys;

    public Feedback(User user, String content, String contact) {
        this.user = user;
        this.content = content;
        this.contact = contact;
    }

    public Feedback(User user, String content, String contact, List<String> imageKeys) {
        this(user, content, contact);
        this.imageKeys = imageKeys == null || imageKeys.isEmpty() ? null : String.join(",", imageKeys);
    }

    /** 붙은 사진 키들(없으면 빈 목록). */
    public List<String> getImageKeyList() {
        return imageKeys == null || imageKeys.isBlank() ? List.of() : List.of(imageKeys.split(","));
    }

    /** 사진 기록을 지운다(S3 객체는 부르는 쪽이 커밋 뒤 지운다). */
    public void clearImages() {
        this.imageKeys = null;
    }

    public void changeStatus(FeedbackStatus status, LocalDateTime now) {
        this.status = status;
        this.resolvedAt = status == FeedbackStatus.RESOLVED ? now : null;
    }
}
