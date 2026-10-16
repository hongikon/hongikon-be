package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 물어보기 서버 답. ① 질문(+직전 대화)에 필요한 안내를 고르고(최대 3개) ② 그 안내 원문 안에서만 답을 쓴다.
 * 서버는 카탈로그 밖의 id 를 내보내지 않는다(출력 스키마로 막혀 있지만 한 번 더 거른다).
 * 되묻기 단계를 골랐으면 그 버튼이 가리키는 안내 원문으로 답한다. 답 쓰기만 실패하면 고른 안내를 앱이 원문 그대로 보인다.
 */
@Slf4j
@Service
public class AskRouteService {

    /** 직전 대화의 답을 프롬프트에 넣을 때 최대 글자 수. */
    static final int HISTORY_ANSWER_MAX = 600;
    /** 답 쓰기에 처음 넘기는 안내 수(되묻기 단계면 버튼이 가리키는 안내로 펼친 뒤). */
    static final int QUESTION_OPTIONS_MAX = 4;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AskCatalog catalog;
    private final AskLlmClient llmClient;
    private final Clock clock;

    @Autowired
    public AskRouteService(AskCatalog catalog, AskLlmClient llmClient) {
        this(catalog, llmClient, Clock.systemUTC());
    }

    AskRouteService(AskCatalog catalog, AskLlmClient llmClient, Clock clock) {
        this.catalog = catalog;
        this.llmClient = llmClient;
        this.clock = clock;
    }

    public AskRouteResponse route(AskRouteRequest request) {
        return route(request, AskProgress.NONE);
    }

    public AskRouteResponse route(AskRouteRequest request, AskProgress progress) {
        List<String> picked = new ArrayList<>();
        for (String id : llmClient.classify(classifyMessage(request))) {
            if (catalog.contains(id) && !AskCatalog.NONE.equals(id) && !picked.contains(id)) {
                picked.add(id);
            }
        }
        if (picked.isEmpty()) {
            return new AskRouteResponse(AskCatalog.NONE, List.of(), null, null, catalog.version());
        }
        String primary = picked.get(0);
        // 되묻기 단계를 골랐으면 버튼으로 되묻지 않고 버튼이 가리키는 안내 원문까지 읽혀 답을 쓴다 — '학생증 재발급'처럼 이미 분명한
        // 질문에 "어떤 점이 궁금하세요?"를 되풀이하지 않게(10-15 사용자 지적). 정말 모호하면 답 쓰기가 글로 되묻는다.
        List<String> answerIds = new ArrayList<>();
        for (String id : picked) {
            List<String> candidates = catalog.isQuestion(id) ? catalog.next(id) : List.of(id);
            for (String candidate : candidates) {
                if (!catalog.isQuestion(candidate) && !answerIds.contains(candidate) && answerIds.size() < QUESTION_OPTIONS_MAX) {
                    answerIds.add(candidate);
                }
            }
        }
        if (answerIds.isEmpty()) {
            return new AskRouteResponse(primary, List.of(primary), null, null, catalog.version());
        }
        List<String> anchors = anchorsFor(answerIds);
        for (String anchor : anchors) {
            if (!answerIds.contains(anchor)) {
                answerIds.add(0, anchor);
            }
        }
        progress.picked(answerIds);
        try {
            AskLlmClient.Answer answer = llmClient.answer(answerIds, answerContext(request), progress);
            // 모델이 근거를 짚지 않았으면 처음 고른 안내 하나만 — 고른 것 전부(되묻기 버튼 안내 포함)를 근거로 붙이면 쓰지도 않은 안내가 줄줄이 나온다.
            List<String> sources = new ArrayList<>(anchors);
            for (String id : answer.sourceIds().isEmpty() ? answerIds.subList(0, 1) : answer.sourceIds()) {
                if (!sources.contains(id)) {
                    sources.add(id);
                }
            }
            List<AskRouteResponse.Link> links = answer.references().stream()
                    .map(reference -> new AskRouteResponse.Link(reference.title(), reference.url(), reference.date()))
                    .toList();
            return new AskRouteResponse(sources.get(0), sources, answer.text(), answer.found(), catalog.version(), links);
        } catch (AskUnavailableException e) {
            log.warn("물어보기 답 쓰기 실패({}) — 고른 안내를 원문 그대로 보낸다: {}", e.getMessage(), answerIds);
            return new AskRouteResponse(primary, answerIds, null, null, catalog.version());
        }
    }

    /** 분류용(캐시 지점 뒤에 붙는 부분). 안내 id 는 카탈로그에 있는 것만 적는다(앱이 보낸 아무 글자나 프롬프트에 섞지 않게). */
    String classifyMessage(AskRouteRequest request) {
        StringBuilder message = new StringBuilder();
        if (request.history() != null && !request.history().isEmpty()) {
            message.append("직전 대화:\n");
            for (AskRouteRequest.Turn turn : request.history()) {
                message.append("- 질문: ").append(oneLine(turn.question()));
                if (known(turn.nodeId())) {
                    message.append(" → 열린 안내: ").append(turn.nodeId());
                }
                message.append('\n');
            }
        }
        if (known(request.hint())) {
            message.append("앱 낱말 찾기 후보: ").append(request.hint()).append('\n');
        }
        return message.append("이번 질문: ").append(oneLine(request.question())).toString();
    }

    /** 답 쓰기용 — 직전 대화(질문과 그때 답)와 이번 질문. 안내 원문은 LlmClient 가 앞에 붙인다. */
    String answerContext(AskRouteRequest request) {
        // 답 쓰기는 캐시하지 않으니 날짜를 넣어도 된다(분류 프롬프트에는 넣지 않는다 — 캐시가 깨진다).
        StringBuilder message = new StringBuilder("[오늘] ").append(LocalDate.now(clock.withZone(KST))).append(" (한국 시간)\n\n");
        if (request.history() != null && !request.history().isEmpty()) {
            message.append("[직전 대화]\n");
            for (AskRouteRequest.Turn turn : request.history()) {
                message.append("학생: ").append(oneLine(turn.question())).append('\n');
                if (turn.answer() != null && !turn.answer().isBlank()) {
                    String answer = oneLine(turn.answer());
                    message.append("홍익온: ")
                            .append(answer.length() > HISTORY_ANSWER_MAX ? answer.substring(0, HISTORY_ANSWER_MAX) + "…" : answer)
                            .append('\n');
                } else if (known(turn.nodeId())) {
                    message.append("홍익온: (안내 ").append(turn.nodeId()).append(" 를 보여 줌)\n");
                }
            }
            message.append('\n');
        }
        return message.append("[이번 질문]\n").append(oneLine(request.question())).toString();
    }

    /** 고른 안내가 걸리는 주제의 고정 근거(카탈로그에 있는 것만). */
    private List<String> anchorsFor(List<String> ids) {
        List<String> anchors = new ArrayList<>();
        for (AskGlossary.Anchor anchor : AskGlossary.ANCHORS) {
            if (catalog.contains(anchor.anchorId()) && ids.stream().anyMatch(anchor::matches) && !anchors.contains(anchor.anchorId())) {
                anchors.add(anchor.anchorId());
            }
        }
        return anchors;
    }

    private boolean known(String id) {
        return catalog.contains(id) && !AskCatalog.NONE.equals(id);
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
