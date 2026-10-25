package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteFeedbackRequest;
import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 물어보기 LLM 분류. 개발 중이라 웹 /dev/ask 에서만 쓴다 — 운영 앱 화면에는 붙이지 않는다.
 *
 * 로그인 대신 /dev 페이지 암호키로 막는다. 앱은 게이트(DevPathGate)에 입력한 암호키를 X-Ask-Dev-Key 로 보내고,
 * 서버는 그 SHA-256 이 ask.dev-key-sha256 과 같은지 본다 — 게이트 확인은 브라우저 안에서만 하므로 서버가 따로 확인해야 하고,
 * 평문 키는 서버 설정에도 두지 않는다(게이트 코드의 해시와 같은 값). 설정이 비어 있으면(운영 기본값) 엔드포인트가 없는 것처럼 404.
 * 키가 틀리면 403 — 401 은 앱이 로그인 만료로 보고 토큰을 다시 받으려 한다.
 */
@Slf4j
@RestController
public class AskRouteController {

    /** 흘려보내기 한 번의 최대 시간. 답 쓰기는 도구를 여러 번 쓰면 수십 초가 걸린다. */
    static final long STREAM_TIMEOUT_MS = 120_000;
    /** 동시에 흘려보내는 답 수. 넘으면 줄을 서서 기다린다(개발 중 /dev/ask 만 쓴다). */
    static final int STREAM_THREADS = 4;

    static final String DEV_KEY_HEADER = "X-Ask-Dev-Key";
    static final String UNAVAILABLE_MESSAGE = "지금은 질문을 분류할 수 없어요.";

    private final AskRouteService askRouteService;
    private final AskRateLimiter askRateLimiter;
    private final AskTranscript askTranscript;
    private final AskCatalog askCatalog;
    private final AskConversationRecorder recorder;
    private final byte[] devKeySha256;
    private final ExecutorService streams = Executors.newFixedThreadPool(STREAM_THREADS, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ask-stream-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public AskRouteController(AskRouteService askRouteService, AskRateLimiter askRateLimiter, AskTranscript askTranscript,
                              AskCatalog askCatalog, AskConversationRecorder recorder,
                              @Value("${ask.dev-key-sha256:}") String devKeySha256) {
        this.askCatalog = askCatalog;
        this.recorder = recorder;
        this.askRouteService = askRouteService;
        this.askRateLimiter = askRateLimiter;
        this.askTranscript = askTranscript;
        // 잘못된 hex 면 기동에서 실패시킨다(조용히 꺼지지 않게).
        this.devKeySha256 = devKeySha256 == null || devKeySha256.isBlank() ? null : HexFormat.of().parseHex(devKeySha256.trim());
    }

    @Operation(summary = "물어보기 질문 분류(개발 중)", description = "질문(+직전 대화 최대 3턴)에 필요한 안내를 고르고(최대 3개) 그 안내 원문 안에서만 답을 쓴다. "
            + "되묻기 단계면 답 없이 id 만(앱이 버튼으로 되묻는다). X-Ask-Dev-Key 필요. 접속 IP 마다 60분에 30건(넘으면 429). "
            + "LLM 이 꺼졌거나 실패하면 503 — 앱은 낱말 찾기로 돌아간다.")
    @PostMapping("/ask/route")
    public AskRouteResponse route(@RequestHeader(value = DEV_KEY_HEADER, required = false) String key,
                                  @Valid @RequestBody AskRouteRequest request,
                                  HttpServletRequest httpRequest) {
        authorize(key, httpRequest);
        long startedAt = System.nanoTime();
        AskTrace trace = new AskTrace(AskProgress.NONE);
        try {
            AskRouteResponse response = askRouteService.route(request, trace);
            record(request, response);
            return response.withLogId(recorder.record(request, response, outcome(response, trace), trace.steps(), trace.usage(), elapsed(startedAt)));
        } catch (AskUnavailableException e) {
            askTranscript.record(request, "실패(503 " + e.getMessage() + ")");
            recorder.record(request, null, AskConversationLog.Outcome.FAILED, trace.steps(), trace.usage(), elapsed(startedAt));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    @Operation(summary = "물어보기 답에 '해결됐나요' 응답 붙이기(개발 중)", description = "logId 는 /ask/route 응답의 logId. "
            + "같은 대화(conversationId)의 기록만 바꾼다. X-Ask-Dev-Key 필요(질문 한도는 쓰지 않는다). 없는 기록이면 404.")
    @PostMapping("/ask/route/feedback")
    public void feedback(@RequestHeader(value = DEV_KEY_HEADER, required = false) String key,
                         @Valid @RequestBody AskRouteFeedbackRequest request) {
        checkKey(key);
        if (!recorder.resolve(request.logId(), request.conversationId(), request.resolved())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private AskConversationLog.Outcome outcome(AskRouteResponse response, AskTrace trace) {
        if (trace.refusedScope() != null) {
            return trace.blockedSchool() != null ? AskConversationLog.Outcome.MIX_BLOCKED : AskConversationLog.Outcome.REFUSED;
        }
        if (response.answer() != null) {
            return Boolean.FALSE.equals(response.found()) ? AskConversationLog.Outcome.NOT_FOUND : AskConversationLog.Outcome.ANSWERED;
        }
        return askCatalog.isQuestion(response.nodeId()) ? AskConversationLog.Outcome.QUESTION : AskConversationLog.Outcome.NO_ANSWER;
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    @Operation(summary = "물어보기 답 흘려받기(개발 중, SSE)", description = "POST /ask/route 와 같은 요청·인증·한도. 진행을 이벤트로 흘려보낸다: "
            + "picked{titles} · search{query} · read{titles} · noticeSearch{query} · noticeRead{title} · cafeteria{date} · "
            + "writing · delta{text}(답 글자) · reset(지금까지 글자 지우기) · 마지막에 done(/ask/route 응답과 같은 모양) 또는 error{message}.")
    @PostMapping(value = "/ask/route/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = DEV_KEY_HEADER, required = false) String key,
                             @Valid @RequestBody AskRouteRequest request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authorize(key, httpRequest);
        // nginx 가 SSE 를 모아 두지 않고 바로 흘려보내게(운영 배포 시 deploy/nginx 설정과 함께).
        httpResponse.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        long startedAt = System.nanoTime();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        streams.execute(() -> {
            AskTrace trace = new AskTrace(new StreamProgress(emitter, closed));
            try {
                AskRouteResponse response = askRouteService.route(request, trace);
                record(request, response);
                // 학생이 멈춤을 눌러 연결이 끊겼으면 '멈춤'으로 남긴다(끊긴 뒤에도 답은 끝까지 만들어져 있다).
                AskConversationLog.Outcome outcome = closed.get() ? AskConversationLog.Outcome.STOPPED : outcome(response, trace);
                Long logId = recorder.record(request, response, outcome, trace.steps(), trace.usage(), elapsed(startedAt));
                send(emitter, closed, "done", response.withLogId(logId));
            } catch (AskUnavailableException e) {
                askTranscript.record(request, "실패(503 " + e.getMessage() + ")");
                recorder.record(request, null, closed.get() ? AskConversationLog.Outcome.STOPPED : AskConversationLog.Outcome.FAILED,
                        trace.steps(), trace.usage(), elapsed(startedAt));
                send(emitter, closed, "error", Map.of("message", UNAVAILABLE_MESSAGE));
            } catch (RuntimeException e) {
                log.warn("물어보기 흘려보내기 실패: {}", e.toString());
                send(emitter, closed, "error", Map.of("message", UNAVAILABLE_MESSAGE));
            } finally {
                if (!closed.get()) {
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        streams.shutdownNow();
    }

    private void authorize(String key, HttpServletRequest httpRequest) {
        checkKey(key);
        askRateLimiter.acquire(httpRequest.getRemoteAddr());
    }

    private void checkKey(String key) {
        if (devKeySha256 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (key == null || !MessageDigest.isEqual(devKeySha256, sha256(key))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void record(AskRouteRequest request, AskRouteResponse response) {
        askTranscript.record(request, String.join(", ", response.nodeIds().isEmpty() ? List.of(response.nodeId()) : response.nodeIds())
                + (response.answer() == null ? "" : (Boolean.TRUE.equals(response.found()) ? " [답]" : " [확인 안 됨]") + " " + response.answer()));
    }

    /** 이벤트 하나. 앱이 창을 닫아 연결이 끊겼으면 더 보내지 않는다(답 만들기는 끝까지 간다 — 기록·비용은 같다). */
    private static void send(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            closed.set(true);
        }
    }

    /** 안내 id 를 앱에 보일 짧은 제목으로(카탈로그 요약 앞부분). */
    private String title(String id) {
        String line = askCatalog.line(id);
        int cut = line.indexOf(" #");
        String text = (cut >= 0 ? line.substring(0, cut) : line).replaceAll(" \\(되묻기:.*$", "").trim();
        return text.length() > 30 ? text.substring(0, 30) + "…" : text;
    }

    /** 진행 알림을 SSE 이벤트로. 실제로 한 일만 보낸다. */
    private final class StreamProgress implements AskProgress {
        private final SseEmitter emitter;
        private final AtomicBoolean closed;

        private StreamProgress(SseEmitter emitter, AtomicBoolean closed) {
            this.emitter = emitter;
            this.closed = closed;
        }

        @Override
        public void picked(List<String> ids) {
            send(emitter, closed, "picked", Map.of("titles", ids.stream().map(AskRouteController.this::title).toList()));
        }

        @Override
        public void searching(String query) {
            send(emitter, closed, "search", Map.of("query", query));
        }

        @Override
        public void reading(List<String> ids) {
            send(emitter, closed, "read", Map.of("titles", ids.stream().map(AskRouteController.this::title).toList()));
        }

        @Override
        public void searchingNotices(String query) {
            send(emitter, closed, "noticeSearch", Map.of("query", query));
        }

        @Override
        public void readingNotice(String title) {
            send(emitter, closed, "noticeRead", Map.of("title", title));
        }

        @Override
        public void searchingFacilities(String query) {
            send(emitter, closed, "facilitySearch", Map.of("query", query));
        }

        @Override
        public void checkingCafeteria(String date) {
            send(emitter, closed, "cafeteria", Map.of("date", date));
        }

        @Override
        public void writing() {
            send(emitter, closed, "writing", Map.of());
        }

        @Override
        public void delta(String text) {
            send(emitter, closed, "delta", Map.of("text", text));
        }

        @Override
        public void resetText() {
            send(emitter, closed, "reset", Map.of());
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
