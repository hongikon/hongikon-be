package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskFeedbackRequest;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 챗봇 "물어보기" 질문·피드백 로그. 비로그인 허용 — SecurityConfig. */
@RestController
@RequiredArgsConstructor
public class AskFeedbackController {

    private final AskFeedbackService askFeedbackService;
    private final AskFeedbackRateLimiter askFeedbackRateLimiter;

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "챗봇 질문·피드백 로그", description = "로그인 선택(사용자는 저장하지 않음). "
            + "question 1~200자(앞뒤 공백 제외), outcome FLOW/OFFICES/NONE, flowId·nodeId 는 FLOW 일 때만 저장(각 100자), "
            + "resolved 는 해결 여부 응답(답변 직후면 생략). 사용자 또는 접속 IP 마다 10분에 60번까지(넘으면 429). 성공 204.")
    @PostMapping("/ask/feedback")
    public ResponseEntity<Void> record(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody AskFeedbackRequest request,
                                       HttpServletRequest httpRequest) {
        // 본문 검증(@Valid)을 통과한 요청만 센다 — 400 받은 시도가 한도를 깎지 않게.
        askFeedbackRateLimiter.acquire(httpRequest.getRemoteAddr(), userId);
        askFeedbackService.record(request);
        return ResponseEntity.noContent().build();
    }
}
