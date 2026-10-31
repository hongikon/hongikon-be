package com.hongmap.hongmapbackend.ask;

import java.util.List;

/**
 * 물어보기 서버 답을 만드는 동안의 진행 알림. POST /ask/route/stream 이 이것을 SSE 이벤트로 바꿔 앱이 실시간으로 보인다
 * ("관련 안내 2개를 찾았어요", "'질병휴학 환급'으로 찾아보고 있어요", 답 글자 …). 일반 /ask/route 는 {@link #NONE}.
 * 모두 실제로 한 일만 알린다 — 꾸민 진행 문구를 만들지 않는다.
 */
public interface AskProgress {

    AskProgress NONE = new AskProgress() {
    };

    /** 질문에 필요한 안내를 골랐다(답의 첫 근거들). */
    default void picked(List<String> ids) {
    }

    /** RAG 검색(임베딩 + BM25)으로 안내 후보를 찾았다(뜻이 가까운 순). */
    default void retrieved(List<String> ids) {
    }

    /** 질문 낱말 중 고른 안내 원문에 없는 것으로 공지·안내를 미리 찾았다(hits 는 찾은 후보 수). */
    default void missingTerms(List<String> terms, int hits) {
    }

    /** 질문 속 학과·기관 게시판에서 공지를 미리 찾았다(hits 는 찾은 공지 수). */
    default void boardSearch(String board, List<String> words, int hits) {
    }

    /** 규정 그래프에서 고른 안내에 이어진 선(edgeIds)과 원문을 함께 넘길 이웃 안내(guides)를 찾았다. */
    default void ruleGraph(List<String> edgeIds, List<String> guides) {
    }

    /** LLM 이 다른 안내를 낱말로 찾는다(search_guides). */
    default void searching(String query) {
    }

    /** LLM 이 안내 원문을 더 읽는다(read_guides). */
    default void reading(List<String> ids) {
    }

    /** LLM 이 학교 공지를 낱말로 찾는다(search_notices). */
    default void searchingNotices(String query) {
    }

    /** LLM 이 공지 본문을 읽는다(read_notice). */
    default void readingNotice(String title) {
    }

    /** LLM 이 학교 지도 시설 정보를 찾는다(search_facilities). */
    default void searchingFacilities(String query) {
    }

    /** LLM 이 그날 학식을 본다(cafeteria_menu). */
    default void checkingCafeteria(String date) {
    }

    /** RAG 검색에 걸린 시간 — 임베딩 서버 호출과 색인 검색(ms, 기록용). */
    default void retrievalTimed(long embedMs, long searchMs) {
    }

    /** 고르기(classify) 호출에 걸린 시간(ms, 실패해도 알린다 — 기록용). */
    default void classifyTimed(long ms) {
    }

    /**
     * 답 쓰기에 걸린 시간(ms, 실패해도 그때까지 — 기록용). toolRounds 는 도구를 부른 차례 수, toolMs 는 도구 실행 시간 합,
     * firstTextMs 는 답 쓰기 시작부터 첫 text delta 까지(나오지 않았으면 -1).
     */
    default void answerTimed(int toolRounds, long toolMs, long firstTextMs, long totalMs) {
    }

    /** LLM 호출 한 번의 토큰(기록·비용 확인용). input 은 캐시를 안 쓴 입력, cacheWrite 는 캐시에 새로 쓴 입력. */
    default void usage(long input, long output, long cacheRead, long cacheWrite) {
    }

    /** 답 글자가 나오기 시작했다. */
    default void writing() {
    }

    /** 답 글자 조각. */
    default void delta(String text) {
    }

    /** 지금까지 보낸 답 글자를 지운다(그 차례가 답이 아니라 더 찾아보기로 끝났을 때). */
    default void resetText() {
    }

    /**
     * 범위 밖이라 고정 문구로 답했다(docs/ask-scope-policy.md). blockedSchool 은 출구 검사가 막은 다른 대학 이름 — 입구에서 거절했으면 null.
     */
    default void refused(AskScope scope, String blockedSchool) {
    }
}
