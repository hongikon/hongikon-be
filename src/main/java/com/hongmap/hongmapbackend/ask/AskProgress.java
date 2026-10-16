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

    /** LLM 호출 한 번의 토큰(기록·비용 확인용). */
    default void usage(long input, long output, long cacheRead) {
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
}
