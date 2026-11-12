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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /** RAG 검색 후보로 분류에 넘기는 안내 수. */
    static final int RETRIEVED_MAX = 6;
    /** 답 쓰기에 원문을 함께 넘기는 홈페이지 쪽 수(학과 홈페이지 등, 안내 목록 밖). */
    static final int PAGES_MAX = 2;
    /** 분류가 아무 안내도 못 골랐을 때 검색 후보에서 답 쓰기로 넘기는 수. */
    static final int RETRIEVED_FALLBACK = 2;
    /** 이 글자 수 이하의 이어 묻는 질문은 직전 질문을 붙여 검색한다('신청은?' 같은 말만으로는 찾을 게 없다). */
    static final int FOLLOW_UP_CHARS = 15;

    /** 놓친 낱말로 미리 찾아 답 쓰기에 넘기는 공지·안내 수. */
    static final int MISSING_HITS_MAX = 4;

    private final AskCatalog catalog;
    private final AskLlmClient llmClient;
    private final AskLiveData liveData;
    private final AskRetriever retriever;
    private final AskRuleGraph ruleGraph;
    private final Clock clock;

    /** 운영진 확인 안내 최대 개수(질문마다). */
    static final int NOTES_MAX = 2;

    /** 운영진 확인 안내(page:note-…). 스프링이 넣는다 — 테스트처럼 직접 만든 서비스에는 없다. */
    private AskOperatorNotes operatorNotes;

    @Autowired(required = false)
    void setOperatorNotes(AskOperatorNotes operatorNotes) {
        this.operatorNotes = operatorNotes;
    }

    /** 질문에 맞는 운영진 확인 안내를 원문 쪽 맨 앞에 더한다(없으면 그대로). */
    List<String> withOperatorNotes(List<String> pages, String question, float[] queryVector) {
        if (operatorNotes == null) {
            return pages;
        }
        List<String> notes = operatorNotes.match(question, queryVector, NOTES_MAX);
        if (notes.isEmpty()) {
            return pages;
        }
        List<String> merged = new ArrayList<>(notes);
        pages.stream().filter(id -> !merged.contains(id)).forEach(merged::add);
        return List.copyOf(merged);
    }

    @Autowired
    public AskRouteService(AskCatalog catalog, AskLlmClient llmClient, AskLiveData liveData, AskRetriever retriever,
                           AskRuleGraph ruleGraph) {
        this(catalog, llmClient, liveData, retriever, ruleGraph, Clock.systemUTC());
    }

    AskRouteService(AskCatalog catalog, AskLlmClient llmClient, AskLiveData liveData, AskRetriever retriever,
                    AskRuleGraph ruleGraph, Clock clock) {
        this.catalog = catalog;
        this.llmClient = llmClient;
        this.liveData = liveData;
        this.retriever = retriever;
        this.ruleGraph = ruleGraph;
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
        // 여기부터는 Anthropic·임베딩 서버로 나가므로 학번·휴대전화·이메일 형태를 가린 질문만 쓴다.
        request = AskPrivacy.mask(request);
        // ② 찾기(RAG 가 켜져 있으면): 안내 원문 색인에서 뜻·낱말이 가까운 안내 후보. 손으로 적은 낱말(앱 hint) 대신 쓴다(10-28).
        AskRetriever.Found found = retriever.enabled()
                ? retriever.search(retrievalQuery(request), RETRIEVED_MAX, PAGES_MAX) : new AskRetriever.Found(List.of(), List.of());
        if (retriever.enabled()) {
            progress.retrievalTimed(found.embedMs(), found.searchMs());
        }
        List<String> retrieved = found.nodes();
        List<String> pages = withOperatorNotes(departmentPages(request.question(), found.pages()), request.question(), found.query());
        if (!retrieved.isEmpty() || !pages.isEmpty()) {
            List<String> shown = new ArrayList<>(retrieved);
            shown.addAll(pages);
            progress.retrieved(shown);
        }
        // ③ 고르기: 범위를 함께 판정한다. 학교 질문이 아니면 답 쓰기를 부르지 않고 고정 문구로.
        AskLlmClient.Classification classification = llmClient.classify(classifyMessage(request, retrieved), progress);
        if (classification.scope() != AskScope.SCHOOL) {
            return refuse(classification.scope(), null, progress);
        }
        List<String> picked = new ArrayList<>();
        for (String id : classification.ids()) {
            if (catalog.contains(id) && !AskCatalog.NONE.equals(id) && !picked.contains(id)) {
                picked.add(id);
            }
        }
        // 분류가 하나도 못 골랐으면 검색 후보 앞쪽을 근거로 넘긴다 — 답 쓰기가 원문을 보고 맞지 않으면 모른다고 한다.
        if (picked.isEmpty()) {
            for (String id : retrieved.subList(0, Math.min(RETRIEVED_FALLBACK, retrieved.size()))) {
                if (catalog.contains(id)) {
                    picked.add(id);
                }
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
        // 학식 질문이면 그날 메뉴를 미리 꺼낸다 — 모델이 cafeteria_menu 를 안 부르고 식단 안내(홈페이지 링크)만 보고 답하던 것(10-31).
        List<AskLiveData.DayMenu> menus = cafeteriaMenus(request.question());
        menus.forEach(menu -> progress.checkingCafeteria(menu.reference().date()));
        if (answerIds.isEmpty() && !picked.isEmpty() && pages.isEmpty() && menus.isEmpty()) {
            return new AskRouteResponse(primary, List.of(primary), null, null, catalog.version());
        }
        List<String> anchors = anchorsFor(answerIds);
        for (String anchor : anchors) {
            if (!answerIds.contains(anchor)) {
                answerIds.add(0, anchor);
            }
        }
        progress.picked(answerIds);
        // 이웃 안내(RAG 가 켜져 있고 ask.rag.neighbors > 0 일 때): 고른 안내와 같은 흐름·같은 되묻기 묶음·뜻이 가까운 안내 중
        // 질문과 가장 가까운 것의 원문을 함께 넘긴다. 답이 다른 안내에 나눠 적힌 경우(군입대 성적대체 → 등록금 이월 등)를 손으로 적은
        // 규정 선 없이 메운다(10-31 평가 '필요한 안내가 안 넘어감' 유형).
        List<String> neighbors = neighbors(answerIds, found.query());
        if (!neighbors.isEmpty()) {
            answerIds.addAll(neighbors);
            log.info("물어보기 이웃 안내: {}", neighbors);
        }
        // 규정 그래프(켜져 있을 때만): 고른 안내에 이어진 규정(결과·예외 …)을 [함께 볼 규정]으로 붙이고, 이웃 안내 원문도 함께 넘긴다
        // (docs/ask-rule-graph-plan.md). RAG 와 무관하게 고른 안내에서 출발한다.
        AskRuleGraph.Related related = ruleGraph.related(answerIds, request.question());
        if (!related.isEmpty()) {
            related.guides().stream().filter(id -> !answerIds.contains(id)).forEach(answerIds::add);
            progress.ruleGraph(related.edgeIds(), related.guides());
        }
        try {
            String context = answerContext(request) + missingContext(request, answerIds, progress)
                    + retrievedContext(withHint(request, retrieved), answerIds) + related.block();
            AskLlmClient.Answer answer = !menus.isEmpty() ? llmClient.answer(answerIds, pages, context, menus, progress)
                    : pages.isEmpty()
                    ? llmClient.answer(answerIds, context, progress)
                    : llmClient.answer(answerIds, pages, context, progress);
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
                    answer.found(), catalog.version(), links), answer.map())
                    .withSections(answer.found()
                            ? fillConsequences(AskQuestionIntent.filter(request.question(), answer.sections()), answer.text(), related.consequences())
                            : AskQuestionIntent.filter(request.question(), answer.sections()));
        } catch (AskUnavailableException e) {
            log.warn("물어보기 답 쓰기 실패({}) — 고른 안내를 원문 그대로 보낸다: {}", e.getMessage(), answerIds);
            if (answerIds.isEmpty()) {
                return new AskRouteResponse(AskCatalog.NONE, List.of(), null, null, catalog.version());
            }
            return new AskRouteResponse(primary, answerIds, null, null, catalog.version());
        }
    }

    /**
     * 학식 질문({@link AskCafeteriaQuestion})이면 물은 날의 메뉴, 아니면 빈 목록. 하루를 물으면 메뉴가 없을 때 다음 메뉴가 있는 날을 붙이고,
     * 이번 주를 물으면 날마다 그대로. 메뉴를 못 읽으면(DB 오류) 빈 목록 — 답 쓰기는 지금처럼 cafeteria_menu 도구로 볼 수 있다.
     */
    List<AskLiveData.DayMenu> cafeteriaMenus(String question) {
        List<LocalDate> dates = AskCafeteriaQuestion.dates(question, LocalDate.now(clock.withZone(KST)));
        if (dates.isEmpty()) {
            return List.of();
        }
        try {
            List<AskLiveData.DayMenu> menus = new ArrayList<>();
            for (LocalDate date : dates) {
                AskLiveData.DayMenu menu = liveData.cafeteria(date, dates.size() == 1);
                if (menu != null) {
                    menus.add(menu);
                }
            }
            return menus;
        } catch (RuntimeException e) {
            log.warn("물어보기 학식 메뉴를 미리 못 읽었다 — 도구에 맡긴다: {}", e.toString());
            return List.of();
        }
    }

    /** 범위 밖 고정 답 — 근거·버튼 없이 문구만. blockedSchool 은 출구 검사가 막은 대학 이름(기록용). */
    private AskRouteResponse refuse(AskScope scope, String blockedSchool, AskProgress progress) {
        progress.refused(scope, blockedSchool);
        return new AskRouteResponse(AskCatalog.NONE, List.of(), scope.refusal(), false, catalog.version());
    }

    /** 분류용(캐시 지점 뒤에 붙는 부분). 안내 id 는 카탈로그에 있는 것만 적는다(앱이 보낸 아무 글자나 프롬프트에 섞지 않게). */
    String classifyMessage(AskRouteRequest request) {
        return classifyMessage(request, List.of());
    }

    /** retrieved 가 있으면(RAG) 앱 낱말 찾기 hint 대신 검색 후보를 적는다. */
    String classifyMessage(AskRouteRequest request, List<String> retrieved) {
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
        if (!retrieved.isEmpty()) {
            message.append("안내 원문 검색 후보(뜻이 가까운 순, 참고용 — 맞지 않으면 고르지 않는다): ")
                    .append(String.join(", ", retrieved.stream().filter(catalog::contains).toList())).append('\n');
        }
        // 검색 후보가 있어도 앱 낱말 찾기 후보를 함께 준다 — RAG 가 놓친 정답을 낱말 찾기가 잡는 경우가 있다
        // ('기숙사 방배정' → 낱말 찾기 life-dorm-movein, RAG 후보에는 없음 — 11-02 평가).
        if (known(request.hint())) {
            message.append("앱 낱말 찾기 후보: ").append(request.hint()).append('\n');
        }
        return message.append(AskBoards.hint(request.question())).append(AskCourses.aliasLine(request.question()))
                .append(AskClubs.hint(request.question()))
                .append("이번 질문: ").append(oneLine(request.question())).toString();
    }

    /** RAG 검색어 — 이번 질문, 짧은 이어 묻기면 직전 질문을 앞에 붙인다. */
    String retrievalQuery(AskRouteRequest request) {
        String question = oneLine(request.question());
        if (question.replaceAll("\\s+", "").length() <= FOLLOW_UP_CHARS && request.history() != null && !request.history().isEmpty()) {
            AskRouteRequest.Turn last = request.history().get(request.history().size() - 1);
            if (last.question() != null && !last.question().isBlank()) {
                return AskCourses.expand(oneLine(last.question()) + " " + question);
            }
        }
        // 줄임말(중동·컴프 …)은 정식 이름을 붙여 찾는다.
        return AskCourses.expand(question);
    }

    /** 답 쓰기용 — 직전 대화(질문과 그때 답)와 이번 질문. 안내 원문은 LlmClient 가 앞에 붙인다. */
    /**
     * 답 쓰기에 넘길 학과 홈페이지 쪽. RAG 가 고른 쪽(retrieved)에서 출발해 질문 속 학과에 맞춘다.
     * <ul>
     *   <li>학과 이름이 없으면 RAG 가 고른 그대로.</li>
     *   <li>학부 이름('신소재화공 학회')이면 전공마다 낱말로 한 쪽씩 먼저 넣고, 남는 자리(전공 수 × {@link #PAGES_MAX})를 RAG 쪽으로 채운다 —
     *       한 전공 쪽만 넘어가 '신소재 학회'와 같은 답이 되던 것(11-03).</li>
     *   <li>한 전공 이름('신소재 학회')이면 같은 학부 다른 전공 쪽은 뺀다. 남는 게 없으면(RAG 꺼짐·못 고름) 그 학과 쪽을 낱말로 찾는다(10-31 '컴공 학회').</li>
     * </ul>
     */
    List<String> departmentPages(String question, List<String> retrieved) {
        Optional<AskBoards.Match> found = AskBoards.find(question);
        if (found.isEmpty()) {
            return retrieved;
        }
        AskBoards.Match match = found.get();
        String rest = match.strip(question);
        if (match.group()) {
            Set<String> out = new LinkedHashSet<>();
            for (String board : match.boards()) {
                out.addAll(retriever.deptPages(board, rest, 1));
            }
            int limit = PAGES_MAX * match.boards().size();
            for (String id : retrieved) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(id);
            }
            return List.copyOf(out);
        }
        List<String> siblings = match.siblings();
        List<String> kept = retrieved.stream().filter(id -> {
            AskRetriever.Page page = retriever.page(id);
            return page == null || !siblings.contains(page.dept());
        }).toList();
        return kept.isEmpty() ? retriever.deptPages(match.board(), rest, PAGES_MAX) : kept;
    }

    /** 질문 속 학과가 1학년 공통 학부이거나 그 학부의 한 전공이면 답 쓰기에 알려 줄 한 줄(아니면 빈 문자열). */
    static String departmentLine(String question) {
        Optional<AskBoards.Match> found = AskBoards.find(question);
        if (found.isEmpty()) {
            return "";
        }
        AskBoards.Match match = found.get();
        if (match.group()) {
            return "[질문 속 학부] " + match.name() + " — 1학년은 학부로 함께 다니고 2학년부터 " + String.join("·", match.boards())
                    + " 중 하나로 나뉜다. 학회·졸업요건·교과과정처럼 전공마다 따로인 것은 전공별로 나눠 보이고, "
                    + "원문에 학부 전체 것이라고 적힌 것은 그렇다고 밝힌다. 같은 내용을 한 전공 것처럼 답하지 않는다.\n\n";
        }
        List<String> siblings = match.siblings();
        if (siblings.isEmpty()) {
            return "";
        }
        return "[질문 속 전공] " + match.board() + " — 같은 학부의 " + String.join("·", siblings)
                + " 것은 섞지 않는다(원문에 학부 전체 것이라고 적힌 것만 그렇다고 밝혀 함께 보일 수 있다).\n\n";
    }

    static final String NO_DATE_LINE = "[오늘] (질문에 시점이 없어 날짜를 주지 않는다 — 규칙대로 언제·어떻게 하는지 답하고, 기간이 지났는지는 말하지 않는다)\n\n";

    String answerContext(AskRouteRequest request) {
        // 답 쓰기는 캐시하지 않으니 날짜를 넣어도 된다(분류 프롬프트에는 넣지 않는다 — 캐시가 깨진다).
        // 요일·시각까지(11-09) — '지금 열려 있어?', '오늘 몇 시까지'를 운영시간과 견주게.
        // 질문이 시점을 말하지 않으면 날짜를 주지 않는다(11-10 평가: '신청 기간이 지났다' 단정이 손해 오류의 가장 큰 원인).
        StringBuilder message = new StringBuilder();
        if (AskSituation.mentionsTime(request.question())) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now(clock.withZone(KST));
            message.append("[오늘] ").append(now.toLocalDate()).append(" (")
                    .append(now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN)).append(") ")
                    .append(String.format("%02d:%02d", now.getHour(), now.getMinute())).append(" (한국 시간)\n\n");
        } else {
            message.append(NO_DATE_LINE);
        }
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
        return message.append(AskQuestionIntent.contextLine(request.question()))
                .append(departmentLine(request.question()))
                .append(AskCourses.aliasLine(request.question()))
                .append(AskClubs.hint(request.question()))
                .append("[이번 질문]\n").append(oneLine(request.question())).toString();
    }

    /** 앱 낱말 찾기 후보(있으면)를 검색 후보 앞에 — 검색 후보가 없으면(RAG 꺼짐) 그대로 빈 목록이라 지금과 같다. */
    List<String> withHint(AskRouteRequest request, List<String> retrieved) {
        if (retrieved.isEmpty() || !known(request.hint()) || retrieved.contains(request.hint())) {
            return retrieved;
        }
        List<String> merged = new ArrayList<>(retrieved.size() + 1);
        merged.add(request.hint());
        merged.addAll(retrieved);
        return merged;
    }

    /**
     * RAG 검색 후보 중 고르지 않은 안내(id — 한 줄 요약). 고르기가 일반 안내를 고르면 답 쓰기는 다른 후보를 몰라 '확인되지 않아요'로
     * 답했다('기숙사 방배정 어디서 확인해요' → dorm/start 만 받고, 답이 있는 life-dorm-movein 은 후보에만 있었다 — 11-02 평가).
     * RAG 가 꺼져 있으면(후보 없음) 빈 문자열이라 지금과 같다.
     */
    String retrievedContext(List<String> retrieved, List<String> answerIds) {
        List<String> others = retrieved.stream()
                .filter(id -> catalog.contains(id) && !catalog.isQuestion(id) && !answerIds.contains(id))
                .toList();
        if (others.isEmpty()) {
            return "";
        }
        StringBuilder message = new StringBuilder("\n\n[검색 후보 안내] 질문과 뜻이 가까운 안내다(고른 안내 말고). ")
                .append("고른 안내 원문에 답이 없으면 맞는 것을 read_guides 로 읽고 답하고, 맞지 않으면 무시한다.\n");
        for (String id : others) {
            message.append(id).append(" — ").append(catalog.line(id)).append('\n');
        }
        return message.toString().stripTrailing();
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
            List<String> words = AskMissingTerms.words(board.get().strip(request.question()), AskMissingTerms.MAX_TERMS);
            // 학부로 물었으면 전공 게시판마다 나눠(합쳐 최대 MISSING_HITS_MAX) — 한 전공 공지로만 채우지 않게.
            int perBoard = (MISSING_HITS_MAX + board.get().boards().size() - 1) / board.get().boards().size();
            for (String id : board.get().boards()) {
                List<AskLiveData.NoticeHit> boardHits = words.isEmpty() ? List.of() : liveData.searchNotices(String.join(" ", words), id);
                boardHits.stream().limit(Math.min(perBoard, MISSING_HITS_MAX - notices.size())).forEach(notices::add);
                progress.boardSearch(id, words, boardHits.size());
            }
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
        board.ifPresent(match -> message.append("[질문 속 학과·기관] ").append(match.group()
                        ? match.name() + "(" + String.join("·", match.boards()) + ")" : match.board())
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

    /**
     * 답 카드 아래 버튼 — 근거 안내의 첫 학교 페이지(같은 주소는 한 번), 담당 전화(같은 번호는 한 번, 최대 3),
     * 그리고 장소를 물은 질문일 때만(map 이 있을 때) 위치 카드·지도 버튼.
     */
    private AskRouteResponse withButtons(AskRouteResponse response, AskLlmClient.MapTarget map) {
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
        AskLiveData.Reference place = map == null ? null : map.place();
        if (map == null) {
            // 장소를 묻지 않은 질문(운영시간·규정·신청 방법 …)이면 건물·시설 이름이 나와도 위치 카드·지도를 붙이지 않는다(11-09 운영진).
            facilityId = null;
        } else if (place != null) {
            facilityId = place.ref().startsWith("facility:") ? place.ref().substring("facility:".length()) : null;
        }
        return new AskRouteResponse(response.nodeId(), response.nodeIds(), response.answer(), response.found(),
                response.catalogVersion(), response.references(), pages, phones, facilityId)
                .withActions(map == null ? List.of() : withRoute(mapActions(place, facilityId, response.answer()), map.route()));
    }

    static final int MAX_FILLED_CONSEQUENCES = 2;

    /**
     * 그래프가 짚은 결과(1단계 결과 선)가 답에 빠졌으면 '꼭 확인할 점'에 선의 사실 문장을 서버가 붙인다(11-10 평가 — Haiku 실패의 가장 큰 몫이
     * '결과 누락'이었다. 모델을 한 번 더 부르지 않고, 검토된 선 문장만 쓴다). 결과 점 이름(성적경고·제적 …)이 답 어디에도 없을 때만, 최대 2개.
     */
    static List<AskRouteResponse.Section> fillConsequences(List<AskRouteResponse.Section> sections, String answer,
                                                           List<AskRuleGraph.Consequence> consequences) {
        if (consequences == null || consequences.isEmpty()) {
            return sections;
        }
        StringBuilder all = new StringBuilder(answer == null ? "" : answer);
        for (AskRouteResponse.Section section : sections) {
            all.append(section.title()).append(String.join("", section.items()));
            if (section.table() != null) {
                section.table().rows().forEach(row -> all.append(String.join("", row)));
            }
        }
        String text = all.toString().replaceAll("\\s+", "");
        List<String> missing = new ArrayList<>();
        for (AskRuleGraph.Consequence consequence : consequences) {
            String target = consequence.target().replaceAll("\\s+", "");
            if (!target.isEmpty() && !text.contains(target) && !alreadySaid(text, consequence.fact())
                    && !missing.contains(consequence.fact()) && missing.size() < MAX_FILLED_CONSEQUENCES) {
                missing.add(consequence.fact());
            }
        }
        if (missing.isEmpty()) {
            return sections;
        }
        List<AskRouteResponse.Section> out = new ArrayList<>(sections);
        for (int i = 0; i < out.size(); i++) {
            AskRouteResponse.Section section = out.get(i);
            if (AskLlmClient.CAUTION_TITLE.equals(section.title())) {
                List<String> items = new ArrayList<>(section.items());
                items.addAll(missing);
                out.set(i, new AskRouteResponse.Section(section.title(), items, section.ordered(), section.table()));
                return out;
            }
        }
        out.add(new AskRouteResponse.Section(AskLlmClient.CAUTION_TITLE, missing, false, null));
        return out;
    }

    /**
     * 답이 그 결과를 다른 말로 이미 했는지 — 선 문장의 2글자 이상 낱말(앞 3글자로 줄여 조사 차이를 넘긴다) 중 60% 이상이 답에 있으면 했다고 본다.
     * 결과 점 이름(예: '등록금 이연')이 답에 글자 그대로 없어도 '등록금을 더 낼 수 있어요'로 이미 말했는데 같은 문장을 또 붙이던 것(11-10 3회차).
     */
    static boolean alreadySaid(String squashedAnswer, String fact) {
        List<String> words = java.util.Arrays.stream(fact.split("[\\s,·()—.\\-]+"))
                .map(String::strip).filter(word -> word.length() >= 2).map(word -> word.substring(0, Math.min(3, word.length()))).distinct().toList();
        if (words.isEmpty()) {
            return false;
        }
        long hit = words.stream().filter(squashedAnswer::contains).count();
        return hit * 10 >= words.size() * 6L;
    }

    /** 지도 대상이 되는 장소 — 건물·편의시설·제휴. */
    private static final List<String> PLACE_PREFIXES = List.of("building:", "facility:", "partner:");
    static final int ACTION_SUMMARY_MAX = 90;

    /**
     * '지도에서 보기' 버튼(11-07). 모델이 고른 장소(place, 이번에 도구로 찾은 것만), 없으면 근거 안내의 위치(facilityId).
     * 대상 id 는 지도 데이터에 있는 것만(도구 결과·카탈로그) — 모델이 좌표나 id 를 만들지 않는다. 버튼은 하나만.
     * 장소를 묻는 질문일 때만 부른다(11-09).
     */
    static List<AskRouteResponse.Action> mapActions(AskLiveData.Reference place, String facilityId, String answer) {
        String summary = summaryOf(answer);
        if (place != null) {
            String ref = place.ref();
            if (PLACE_PREFIXES.stream().anyMatch(prefix -> ref.startsWith(prefix) && ref.length() > prefix.length())) {
                // 시설 참조 제목은 설명 한 줄(이름·층·시간…)이라 앱이 지도 데이터 이름을 쓴다. 건물·제휴는 이름 그대로.
                String label = ref.startsWith("facility:") || place.title() == null || place.title().isBlank()
                        ? null : place.title();
                return List.of(new AskRouteResponse.Action("map", ref, label, summary));
            }
        }
        if (facilityId != null && !facilityId.isBlank()) {
            return List.of(new AskRouteResponse.Action("map", "facility:" + facilityId, null, summary));
        }
        return List.of();
    }

    /**
     * 가는 길을 물었으면(11-09 'Q동 어떻게 가') 지도 버튼 뒤에 '여기서 가는 길'(route) 버튼을 하나 더 — 건물·시설만(제휴처는 캠퍼스 밖이라 경로망에 없다).
     * 출발(내 위치)은 앱이 휴대폰 안에서 정한다 — 서버는 위치를 받지 않는다. 옛 앱은 모르는 type 이라 그리지 않는다.
     */
    static List<AskRouteResponse.Action> withRoute(List<AskRouteResponse.Action> actions, boolean route) {
        if (!route || actions.isEmpty() || actions.get(0).target().startsWith("partner:")) {
            return actions;
        }
        AskRouteResponse.Action map = actions.get(0);
        return List.of(map, new AskRouteResponse.Action("route", map.target(), map.label(), map.summary()));
    }

    /** 답의 첫 문장(마크다운 굵게 표시는 뗀다), 최대 ACTION_SUMMARY_MAX 자. */
    static String summaryOf(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String text = answer.replace("**", "").strip();
        int end = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || ((c == '.' || c == '?' || c == '!') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))))) {
                end = c == '\n' ? i : i + 1;
                break;
            }
        }
        String first = (end > 0 ? text.substring(0, end) : text).strip();
        return first.length() > ACTION_SUMMARY_MAX ? first.substring(0, ACTION_SUMMARY_MAX - 1).stripTrailing() + "…" : first;
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

    List<String> neighbors(List<String> answerIds, float[] query) {
        int limit = retriever.neighborLimit();
        if (limit <= 0 || query == null || answerIds.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        for (String id : answerIds) {
            candidates.addAll(catalog.family(id));
            retriever.similarGuides(id).stream().filter(other -> !catalog.isQuestion(other)).forEach(candidates::add);
        }
        candidates.removeAll(answerIds);
        candidates.remove(AskCatalog.NONE);
        return retriever.closestToQuery(candidates, query, limit);
    }
}
