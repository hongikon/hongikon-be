package com.hongmap.hongmapbackend.ask.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GET /admin/ask/feedback — '해결 안 됨' 답 목록(최신순, 최대 200건).
 *
 * @param days    며칠 전까지 모았는지
 * @param reasons 이 목록의 이유별 개수(WRONG·OUTDATED·OFF_TOPIC·NOT_ENOUGH·NONE=이유 안 고름)
 */
public record AskFeedbackAdminResponse(int days, List<Item> items, Map<String, Long> reasons) {

    /**
     * @param source     ROUTE(서버 답) / FLOW(앱 안내 흐름 답)
     * @param answer     서버 답 글(가린 글). FLOW 는 null — flowId·nodeId 로 앱 안내를 찾는다
     * @param references 서버 답의 근거 링크 제목
     * @param nodeIds    서버 답이 고른 안내 id
     * @param reason     WRONG·OUTDATED·OFF_TOPIC·NOT_ENOUGH, 안 골랐으면 null
     * @param handoffs   같은 답에서 '운영진에게 물어보기'로 보낸 문의
     */
    public record Item(String source, Long id, LocalDateTime createdAt, String question, String answer, String outcome,
                       String flowId, String nodeId, List<String> references, List<String> nodeIds, String reason,
                       LocalDateTime reviewedAt, List<Handoff> handoffs) {
        public Item withHandoffs(List<Handoff> next) {
            return new Item(source, id, createdAt, question, answer, outcome, flowId, nodeId, references, nodeIds, reason,
                    reviewedAt, List.copyOf(next));
        }
    }

    /** @param imageUrls 참고 사진 보기 URL(presigned GET, 1시간). 문의를 처리 완료하면 사진은 지워져 빈 배열 */
    public record Handoff(Long feedbackId, String content, String contact, String status, LocalDateTime createdAt,
                          List<String> imageUrls) {
    }
}
