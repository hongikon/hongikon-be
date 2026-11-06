package com.hongmap.hongmapbackend.ask.dto;

import java.util.List;
import java.util.Map;

/**
 * GET /admin/ask/feedback/report — 기간(기본 7일) 동안의 물어보기 '해결 안 됨'을 안내별·비슷한 질문별로 묶은 요약(11-06).
 * 서버 안에서만 센다(질문을 밖으로 보내지 않는다).
 *
 * @param questions   서버 답 수(기록 기준)
 * @param resolvedYes '해결됐어요' 수(서버 답 + 앱 안내 흐름 답)
 * @param resolvedNo  '해결 안 됨' 수(서버 답 + 앱 안내 흐름 답)
 * @param reasons     '해결 안 됨' 이유별 개수(NONE = 이유 안 고름)
 * @param groups      많은 순 최대 30개
 */
public record AskFeedbackReportResponse(int days, long questions, long resolvedYes, long resolvedNo,
                                        Map<String, Long> reasons, List<Group> groups) {

    /**
     * @param kind      GUIDE(같은 안내로 답한 것) / UNMATCHED(안내를 못 고른 질문 — 비슷한 질문끼리)
     * @param key       GUIDE 면 안내 id, UNMATCHED 면 대표 질문
     * @param label     GUIDE 면 안내 한 줄 요약, UNMATCHED 면 대표 질문
     * @param open      아직 '확인함'이 아닌 수
     * @param questions 예시 질문(최대 5개, 이미 가린 글)
     */
    public record Group(String kind, String key, String label, long count, long open, Map<String, Long> reasons,
                        List<String> questions) {
    }
}
