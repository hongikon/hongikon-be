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
import java.util.Optional;

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

    /** 놓친 낱말로 미리 찾아 답 쓰기에 넘기는 공지·안내 수. */
    static final int MISSING_HITS_MAX = 4;

    private final AskCatalog catalog;
    private final AskLlmClient llmClient;
    private final AskLiveData liveData;
    private final Clock clock;

    @Autowired
    public AskRouteService(AskCatalog catalog, AskLlmClient llmClient, AskLiveData liveData) {
        this(catalog, llmClient, liveData, Clock.systemUTC());
    }

    AskRouteService(AskCatalog catalog, AskLlmClient llmClient, AskLiveData liveData, Clock clock) {
        this.catalog = catalog;
        this.llmClient = llmClient;
        this.liveData = liveData;
        this.clock = clock;
    }

    public AskRouteResponse route(AskRouteRequest request) {
        return route(request, AskProgress.NONE);
    }

    public AskRouteResponse route(AskRouteRequest request, AskProgress progress) {
        // ① 입구: 명백한 악용은 API 를 부르기 전에 막는다(docs/ask-scope-policy.md).
        Optional<AskScope> blocked = AskScope.preCheck(request.question());
        if (blocked.isPresent()) {
            return refuse(blocked.get(), null, progress);
        }
        // ② 고르기: 범위를 함께 판정한다. 학교 질문이 아니면 답 쓰기를 부르지 않고 고정 문구로.
        AskLlmClient.Classification classification = llmClient.classify(classifyMessage(request), progress);
        if (classification.scope() != AskScope.SCHOOL) {
            return refuse(classification.scope(), null, progress);
        }
        List<String> picked = new ArrayList<>();
        for (String id : classification.ids()) {
            if (catalog.contains(id) && !AskCatalog.NONE.equals(id) && !picked.contains(id)) {
                picked.add(id);
            }
        }
        // 맞는 안내를 못 골라도(none) 바로 끝내지 않는다 — 지도 시설·공지에 답이 있을 수 있다('K동 공동학습실', '프레젠테이션룸 예약').
        // 답 쓰기가 도구로 찾아보고, 그래도 없으면 모른다고 한다.
        String primary = picked.isEmpty() ? AskCatalog.NONE : picked.get(0);
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
        if (answerIds.isEmpty() && !picked.isEmpty()) {
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
            AskLlmClient.Answer answer = llmClient.answer(answerIds, answerContext(request) + missingContext(request, answerIds, progress), progress);
            // ③ 출구: 근거 없는 다른 대학 이름이 있어 답을 버렸으면 고정 문구로(AskLlmClient 가 화면으로 나가기 전에 막았다).
            if (answer.blockedSchool() != null) {
                return refuse(AskScope.OTHER_SCHOOL, answer.blockedSchool(), progress);
            }
            // 모델이 근거를 짚지 않았으면 처음 고른 안내 하나만 — 고른 것 전부(되묻기 버튼 안내 포함)를 근거로 붙이면 쓰지도 않은 안내가 줄줄이 나온다.
            List<String> sources = new ArrayList<>(anchors);
            for (String id : answer.sourceIds().isEmpty() ? answerIds.subList(0, Math.min(1, answerIds.size())) : answer.sourceIds()) {
                if (!sources.contains(id)) {
                    sources.add(id);
                }
            }
            List<AskRouteResponse.Link> links = answer.references().stream()
                    .filter(reference -> !reference.ref().startsWith("facility:") && !reference.ref().startsWith("building:")
                            && !reference.ref().startsWith("partner:"))
                    .map(reference -> new AskRouteResponse.Link(reference.title(), reference.url(), reference.date()))
                    .toList();
            return withButtons(new AskRouteResponse(sources.isEmpty() ? AskCatalog.NONE : sources.get(0), sources, answer.text(),
                    answer.found(), catalog.version(), links), answer.references()).withSections(answer.sections());
        } catch (AskUnavailableException e) {
            log.warn("물어보기 답 쓰기 실패({}) — 고른 안내를 원문 그대로 보낸다: {}", e.getMessage(), answerIds);
            if (answerIds.isEmpty()) {
                return new AskRouteResponse(AskCatalog.NONE, List.of(), null, null, catalog.version());
            }
            return new AskRouteResponse(primary, answerIds, null, null, catalog.version());
        }
    }

    /** 범위 밖 고정 답 — 근거·버튼 없이 문구만. blockedSchool 은 출구 검사가 막은 대학 이름(기록용). */
    private AskRouteResponse refuse(AskScope scope, String blockedSchool, AskProgress progress) {
        progress.refused(scope, blockedSchool);
        return new AskRouteResponse(AskCatalog.NONE, List.of(), scope.refusal(), false, catalog.version());
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

    /**
     * 질문 낱말 중 고른 안내 원문에 없는 것으로 공지·안내를 미리 찾아 후보로 넘긴다({@link AskMissingTerms}).
     * 고른 안내가 비슷한 학교 전체 안내일 때 학과 공지·다른 안내에만 있는 답을 놓치지 않게 한다. 놓친 낱말은 기록에도 남는다.
     */
    String missingContext(AskRouteRequest request, List<String> answerIds, AskProgress progress) {
        StringBuilder sources = new StringBuilder();
        for (String id : answerIds) {
            String source = catalog.source(id);
            if (source != null) {
                sources.append(source).append('\n');
            }
        }
        List<String> terms = AskMissingTerms.find(request.question(), sources.toString());
        // 학과·기관 이름이 있으면 그 게시판 공지를 먼저 — 학교 전체 안내를 골랐어도 학과 장학금·행사는 학과 공지에 있다.
        Optional<AskBoards.Match> board = AskBoards.find(request.question());
        List<AskLiveData.NoticeHit> notices = new ArrayList<>();
        if (board.isPresent()) {
            List<String> words = AskMissingTerms.words(request.question().replace(board.get().alias(), " "), AskMissingTerms.MAX_TERMS);
            List<AskLiveData.NoticeHit> boardHits = words.isEmpty() ? List.of() : liveData.searchNotices(String.join(" ", words), board.get().board());
            boardHits.stream().limit(MISSING_HITS_MAX).forEach(notices::add);
            progress.boardSearch(board.get().board(), words, boardHits.size());
        }
        if (terms.isEmpty() && notices.isEmpty()) {
            return "";
        }
        String query = String.join(" ", terms);
        if (!terms.isEmpty()) {
            // 학과 게시판 후보(최대 4) 뒤에 전체 공지 후보(최대 4)를 겹치지 않게 붙인다.
            liveData.searchNotices(query).stream()
                    .filter(hit -> notices.stream().noneMatch(n -> n.reference().ref().equals(hit.reference().ref())))
                    .limit(MISSING_HITS_MAX)
                    .toList()
                    .forEach(notices::add);
        }
        List<String> guides = terms.isEmpty() ? List.of()
                : catalog.search(query, MISSING_HITS_MAX).stream().filter(id -> !answerIds.contains(id)).toList();
        if (!terms.isEmpty()) {
            progress.missingTerms(terms, notices.size() + guides.size());
        }
        if (notices.isEmpty() && guides.isEmpty()) {
            return "";
        }
        StringBuilder message = new StringBuilder("\n\n");
        board.ifPresent(match -> message.append("[질문 속 학과·기관] ").append(match.board())
                .append(" — 그 게시판 공지를 먼저 보고, 학교 전체 안내와 다르면 학과 공지 내용을 그 학과 것으로 밝혀 답한다.\n"));
        if (!terms.isEmpty()) {
            message.append("[고른 안내 원문에 없는 질문 낱말] ").append(String.join(", ", terms)).append('\n');
        }
        message.append("이 낱말로 미리 찾은 후보다. 질문에 맞으면 read_notice·read_guides 로 읽고 답하고, 맞지 않으면 무시한다.\n");
        for (AskLiveData.NoticeHit hit : notices) {
            AskLiveData.Reference ref = hit.reference();
            message.append(ref.ref()).append(" · ").append(ref.date()).append(" · ").append(AskLlmClient.boardLabel(hit.board()))
                    .append(" · ").append(ref.title()).append('\n');
        }
        for (String id : guides) {
            message.append(id).append(" — ").append(catalog.line(id)).append('\n');
        }
        return message.toString().stripTrailing();
    }

    /** 답 카드 아래 버튼 — 근거 안내의 첫 학교 페이지(같은 주소는 한 번), 담당 전화(같은 번호는 한 번, 최대 3), 처음 나온 위치. */
    private AskRouteResponse withButtons(AskRouteResponse response, List<AskLiveData.Reference> references) {
        List<AskRouteResponse.Link> pages = new ArrayList<>();
        List<AskRouteResponse.Phone> phones = new ArrayList<>();
        String facilityId = null;
        for (String id : response.nodeIds()) {
            AskCatalog.Meta meta = catalog.meta(id);
            if (meta == null) {
                continue;
            }
            if (meta.links() != null && !meta.links().isEmpty()) {
                AskCatalog.Link link = meta.links().get(0);
                if (pages.stream().noneMatch(page -> page.url().equals(link.url()))) {
                    pages.add(new AskRouteResponse.Link(link.label(), link.url(), null));
                }
            }
            if (meta.phones() != null) {
                for (AskCatalog.Phone phone : meta.phones()) {
                    if (phones.size() < 3 && phones.stream().noneMatch(p -> p.phone().equals(phone.phone()))) {
                        phones.add(new AskRouteResponse.Phone(phone.label(), phone.phone()));
                    }
                }
            }
            if (facilityId == null) {
                facilityId = meta.facilityId();
            }
        }
        if (facilityId == null) {
            // 안내에 위치가 없으면 답에 쓴 지도 시설의 위치(facility:{id}).
            facilityId = references.stream().map(AskLiveData.Reference::ref).filter(ref -> ref.startsWith("facility:"))
                    .map(ref -> ref.substring("facility:".length())).findFirst().orElse(null);
        }
        return new AskRouteResponse(response.nodeId(), response.nodeIds(), response.answer(), response.found(),
                response.catalogVersion(), response.references(), pages, phones, facilityId);
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
