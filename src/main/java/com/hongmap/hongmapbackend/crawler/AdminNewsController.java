package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.crawler.dto.NewsLocationBackfillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * department_id 또는 building_id가 비어 있는 기존 News를 재매칭하는 1회성 관리용 엔드포인트.
 * SecurityConfig 의 /admin/** 규칙으로 ADMIN 만 호출할 수 있다(전체 뉴스를 다시 훑는 무거운 작업이라).
 */
@Slf4j
@RestController
@RequestMapping("/admin/news")
@RequiredArgsConstructor
public class AdminNewsController {

    private final NewsLocationBackfillService backfillService;

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(
            summary = "뉴스 위치정보(학과/건물) 백필",
            description = "department_id 또는 building_id가 NULL인 기존 News를 NewsLocationMatcher로 재매칭한다. "
                    + "이미 채워진 값은 건드리지 않고, 매칭 실패한 필드는 NULL로 남긴다. ADMIN 전용."
    )
    @PostMapping("/backfill-location")
    public ResponseEntity<NewsLocationBackfillResponse> backfillLocation(@AuthenticationPrincipal Long userId) {
        log.info("뉴스 위치정보 백필 트리거: userId={}", userId);
        int updatedCount = backfillService.backfill();
        return ResponseEntity.ok(new NewsLocationBackfillResponse(updatedCount));
    }
}
