package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminOverviewResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.crawler.CrawlerRunTracker;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.feedback.FeedbackStatus;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequiredArgsConstructor
public class AdminOverviewController {

    private final Optional<BuildProperties> buildProperties;
    private final ReportRepository reportRepository;
    private final FeedbackRepository feedbackRepository;
    private final CrawlerRunTracker crawlerRunTracker;
    private final EntityManager entityManager;

    @Operation(summary = "관리자 대시보드 요약", description = "서버 빌드, 제보·문의 대기 건수, 소식 학과 매칭 현황, 마지막 크롤링 결과")
    @GetMapping("/admin/overview")
    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        AdminOverviewResponse.Server server = buildProperties
                .map(p -> new AdminOverviewResponse.Server(p.getVersion(), p.getTime().toString()))
                .orElseGet(() -> new AdminOverviewResponse.Server("unknown", "unknown"));

        AdminOverviewResponse.Reports reports = new AdminOverviewResponse.Reports(
                reportRepository.countByStatus(ReportStatus.PENDING),
                // 승인(ACTIVE)이어도 끝났거나 아직 시작 전(예정)이면 지도에 없다 — 실제로 지도에 보이는 것만 '노출 중'으로 센다.
                reportRepository.countLive(ReportStatus.ACTIVE, LocalDateTime.now()),
                reportRepository.countByStatus(ReportStatus.HIDDEN),
                reportRepository.countByStatus(ReportStatus.REJECTED));

        // NewsRepository 는 크롤러 쪽에서 자주 바뀌는 파일이라, 대시보드 전용 집계는 여기서 직접 센다.
        long newsTotal = entityManager.createQuery("SELECT COUNT(n) FROM News n", Long.class).getSingleResult();
        long newsMissingDepartment = entityManager
                .createQuery("SELECT COUNT(n) FROM News n WHERE n.department IS NULL", Long.class)
                .getSingleResult();

        return new AdminOverviewResponse(
                server,
                reports,
                new AdminOverviewResponse.Feedback(feedbackRepository.countByStatus(FeedbackStatus.OPEN)),
                new AdminOverviewResponse.News(newsTotal, newsMissingDepartment),
                AdminOverviewResponse.Crawler.of(crawlerRunTracker.snapshot()));
    }
}
