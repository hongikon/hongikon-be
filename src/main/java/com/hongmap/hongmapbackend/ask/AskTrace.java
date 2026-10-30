package com.hongmap.hongmapbackend.ask;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 번의 답을 만드는 동안 실제로 한 일(고른 안내·찾아본 말·읽은 공지·시설·학식)과 토큰을 모은다 — 기록(ask_conversation_logs)용.
 * 진행 알림은 그대로 다음(SSE 등)으로 넘긴다.
 */
final class AskTrace implements AskProgress {

    private final AskProgress next;
    private final List<String> steps = new ArrayList<>();
    private AskUsage usage = AskUsage.ZERO;
    private AskScope refusedScope;
    private String blockedSchool;

    AskTrace(AskProgress next) {
        this.next = next;
    }

    List<String> steps() {
        return steps;
    }

    AskUsage usage() {
        return usage;
    }

    /** 범위 밖으로 거절했으면 그 범위, 아니면 null. */
    AskScope refusedScope() {
        return refusedScope;
    }

    /** 출구 검사가 막은 다른 대학 이름(입구 거절이면 null). */
    String blockedSchool() {
        return blockedSchool;
    }

    @Override
    public void refused(AskScope scope, String blockedSchool) {
        this.refusedScope = scope;
        this.blockedSchool = blockedSchool;
        steps.add(blockedSchool == null ? "거절: " + scope.wire() : "섞임 차단: " + blockedSchool);
        next.refused(scope, blockedSchool);
    }

    @Override
    public void picked(List<String> ids) {
        steps.add("고름: " + String.join(", ", ids));
        next.picked(ids);
    }

    @Override
    public void retrieved(List<String> ids) {
        steps.add("검색 후보: " + String.join(", ", ids));
        next.retrieved(ids);
    }

    @Override
    public void boardSearch(String board, List<String> words, int hits) {
        steps.add("학과 공지 찾기: " + board + " · " + String.join(", ", words) + " (" + hits + ")");
    }

    @Override
    public void missingTerms(List<String> terms, int hits) {
        steps.add("놓친 낱말: " + String.join(", ", terms) + " (후보 " + hits + ")");
    }

    @Override
    public void searching(String query) {
        steps.add("안내 찾기: " + query);
        next.searching(query);
    }

    @Override
    public void reading(List<String> ids) {
        steps.add("안내 읽기: " + String.join(", ", ids));
        next.reading(ids);
    }

    @Override
    public void searchingNotices(String query) {
        steps.add("공지 찾기: " + query);
        next.searchingNotices(query);
    }

    @Override
    public void readingNotice(String title) {
        steps.add("공지 읽기: " + title);
        next.readingNotice(title);
    }

    @Override
    public void searchingFacilities(String query) {
        steps.add("시설 찾기: " + query);
        next.searchingFacilities(query);
    }

    @Override
    public void checkingCafeteria(String date) {
        steps.add("학식: " + date);
        next.checkingCafeteria(date);
    }

    @Override
    public void usage(long input, long output, long cacheRead) {
        usage = usage.plus(input, output, cacheRead);
        next.usage(input, output, cacheRead);
    }

    @Override
    public void writing() {
        next.writing();
    }

    @Override
    public void delta(String text) {
        next.delta(text);
    }

    @Override
    public void resetText() {
        next.resetText();
    }
}
