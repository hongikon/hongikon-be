package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 물어보기 서버 답 기록(ask_conversation_logs) — 남기기, '해결됐나요' 응답 붙이기, 보관 기간(기본 365일) 지난 기록 지우기.
 * 기록 실패는 답에 영향을 주지 않는다(경고만). ask.log.enabled=false 면 남기지 않는다.
 */
@Slf4j
@Component
public class AskConversationRecorder {

    private final AskConversationLogRepository repository;
    private final boolean enabled;
    private final int retentionDays;
    private final Clock clock;

    public AskConversationRecorder(AskConversationLogRepository repository,
                                   @Value("${ask.log.enabled:true}") boolean enabled,
                                   @Value("${ask.log.retention-days:365}") int retentionDays) {
        this.repository = repository;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.clock = Clock.systemUTC();
    }

    /** 한 번의 답을 남기고 id 를 돌려준다(꺼졌거나 실패하면 null). */
    public Long record(AskRouteRequest request, AskRouteResponse response, AskConversationLog.Outcome outcome,
                       List<String> steps, AskUsage usage, long latencyMs) {
        if (!enabled) {
            return null;
        }
        try {
            AskConversationLog saved = repository.save(AskConversationLog.builder()
                    .conversationId(request.conversationId())
                    .turn((request.history() == null ? 0 : request.history().size()) + 1)
                    .question(clip(AskPrivacy.mask(request.question().trim()), 200))
                    .answer(response == null ? null : AskPrivacy.mask(response.answer()))
                    .outcome(outcome)
                    .nodeIds(response == null ? null : clip(String.join(",", response.nodeIds()), 1000))
                    .referenceTitles(response == null || response.references().isEmpty() ? null
                            : response.references().stream().map(AskRouteResponse.Link::title).filter(Objects::nonNull)
                                    .collect(Collectors.joining("\n")))
                    .steps(steps.isEmpty() ? null : AskPrivacy.mask(String.join("\n", steps)))
                    .latencyMs(latencyMs)
                    .inputTokens(usage.input())
                    .outputTokens(usage.output())
                    .cacheReadTokens(usage.cacheRead())
                    .catalogVersion(response == null ? null : response.catalogVersion())
                    .build());
            return saved.getId();
        } catch (RuntimeException e) {
            log.warn("물어보기 기록 실패: {}", e.toString());
            return null;
        }
    }

    /** '해결됐나요' 응답을 붙인다. 같은 대화(conversation_id)의 기록일 때만 — 남의 기록을 바꾸지 못하게. */
    @Transactional
    public boolean resolve(long logId, String conversationId, boolean resolved) {
        Optional<AskConversationLog> found = repository.findById(logId)
                .filter(row -> row.getConversationId() != null && row.getConversationId().equals(conversationId));
        found.ifPresent(row -> row.resolve(resolved));
        return found.isPresent();
    }

    @Scheduled(cron = "${ask.log.purge-cron:0 50 3 * * *}")
    @Transactional
    public void purgeExpired() {
        // created_at 은 서버 JVM 시간대(UTC)로 남으므로 같은 시계로 비교한다.
        int deleted = repository.deleteCreatedBefore(LocalDateTime.now(clock).minusDays(retentionDays));
        if (deleted > 0) {
            log.info("보관 기간이 지난 물어보기 기록 {}건 삭제", deleted);
        }
    }

    private static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
