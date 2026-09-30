package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.crawler.dto.CrawlerTriggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정기 크롤링(CrawlerScheduler)과 별개로 운영자가 즉시 전체 크롤링을 실행하기 위한 관리용 엔드포인트.
 * SecurityConfig에 permitAll을 추가하지 않았으므로 anyRequest().authenticated()에 걸려 로그인이 필요하다.
 */
@Slf4j
@RestController
@RequestMapping("/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerRunTracker crawlerRunTracker;

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "뉴스 크롤링 수동 실행", description = "등록된 전체 게시판을 즉시 크롤링한다. ADMIN 전용. 이미 도는 중이면 409.")
    @PostMapping("/trigger")
    public ResponseEntity<CrawlerTriggerResponse> trigger(@AuthenticationPrincipal Long userId) {
        log.info("수동 크롤링 트리거: userId={}", userId);
        int savedCount;
        try {
            savedCount = crawlerRunTracker.run(CrawlerRunTracker.Trigger.MANUAL);
        } catch (CrawlerRunTracker.AlreadyRunningException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.ok(new CrawlerTriggerResponse(savedCount));
    }
}
