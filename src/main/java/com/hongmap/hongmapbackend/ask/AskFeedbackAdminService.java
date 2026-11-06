package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskFeedbackAdminResponse;
import com.hongmap.hongmapbackend.ask.dto.AskFeedbackAdminResponse.Handoff;
import com.hongmap.hongmapbackend.ask.dto.AskFeedbackAdminResponse.Item;
import com.hongmap.hongmapbackend.feedback.Feedback;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 '물어보기 피드백'(11-06): 학생이 '해결 안 됨'을 누른 답을 한곳에 모아 보여 준다.
 * <ul>
 *   <li>서버 답(ask_conversation_logs, source ROUTE): 가린 질문·답·근거 제목.</li>
 *   <li>앱 안내 흐름 답(ask_feedback_logs, source FLOW): 질문·흐름 id.</li>
 *   <li>같은 답에서 '운영진에게 물어보기'로 보낸 문의(feedback.ask_ref)가 있으면 남긴 말·사진을 붙인다.</li>
 * </ul>
 * 저장된 질문·답은 이미 가린 글이다(AskPrivacy). 사람을 가리키는 값(user·IP)은 두 기록 모두 없다.
 */
@Service
@RequiredArgsConstructor
public class AskFeedbackAdminService {

    static final int LIST_LIMIT = 200;
    static final int MAX_DAYS = 365;

    private final AskConversationLogRepository conversationLogs;
    private final AskFeedbackLogRepository feedbackLogs;
    private final FeedbackRepository feedbackRepository;
    private final ReportImageService reportImageService;

    /**
     * @param status OPEN(기본, 아직 확인 안 함) / REVIEWED / ALL
     * @param days   며칠 전까지(기본 30, 최대 365)
     */
    @Transactional(readOnly = true)
    public AskFeedbackAdminResponse list(String status, Integer days) {
        Boolean reviewed = switch (status == null || status.isBlank() ? "OPEN" : status.strip().toUpperCase()) {
            case "OPEN" -> false;
            case "REVIEWED" -> true;
            case "ALL" -> null;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 상태입니다: " + status);
        };
        int window = days == null ? 30 : Math.max(1, Math.min(MAX_DAYS, days));
        LocalDateTime since = LocalDateTime.now().minusDays(window);
        PageRequest page = PageRequest.of(0, LIST_LIMIT);

        List<Item> items = new ArrayList<>();
        for (AskConversationLog row : conversationLogs.findUnresolved(since, reviewed, page)) {
            items.add(new Item("ROUTE", row.getId(), row.getCreatedAt(), row.getQuestion(), row.getAnswer(),
                    row.getOutcome().name(), null, null, lines(row.getReferenceTitles(), "\n"), split(row.getNodeIds()),
                    row.getReason() == null ? null : row.getReason().name(), row.getReviewedAt(), List.of()));
        }
        for (AskFeedbackLog row : feedbackLogs.findUnresolved(since, reviewed, page)) {
            items.add(new Item("FLOW", row.getId(), row.getCreatedAt(), row.getQuestion(), null,
                    row.getOutcome().name(), row.getFlowId(), row.getNodeId(), List.of(), List.of(),
                    row.getReason() == null ? null : row.getReason().name(), row.getReviewedAt(), List.of()));
        }
        items.sort(Comparator.comparing(Item::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > LIST_LIMIT) {
            items = new ArrayList<>(items.subList(0, LIST_LIMIT));
        }
        items = attachHandoffs(items);

        Map<String, Long> reasons = new LinkedHashMap<>();
        for (AskFeedbackReason reason : AskFeedbackReason.values()) {
            reasons.put(reason.name(), 0L);
        }
        reasons.put("NONE", 0L);
        items.forEach(item -> reasons.merge(item.reason() == null ? "NONE" : item.reason(), 1L, Long::sum));
        return new AskFeedbackAdminResponse(window, items, reasons);
    }

    /** 확인 표시(reviewed=true) 또는 되돌리기(false). */
    @Transactional
    public void review(String source, long id, boolean reviewed) {
        LocalDateTime at = reviewed ? LocalDateTime.now() : null;
        switch (source == null ? "" : source.strip().toUpperCase()) {
            case "ROUTE" -> conversationLogs.findById(id).orElseThrow(AskFeedbackAdminService::notFound).markReviewed(at);
            case "FLOW" -> feedbackLogs.findById(id).orElseThrow(AskFeedbackAdminService::notFound).markReviewed(at);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 는 ROUTE 또는 FLOW 예요.");
        }
    }

    private List<Item> attachHandoffs(List<Item> items) {
        Map<String, Item> byRef = new LinkedHashMap<>();
        Map<Long, String> flowClientIds = feedbackLogs.findAllById(items.stream()
                        .filter(item -> item.source().equals("FLOW")).map(Item::id).toList()).stream()
                .filter(row -> row.getClientId() != null)
                .collect(Collectors.toMap(AskFeedbackLog::getId, AskFeedbackLog::getClientId));
        for (Item item : items) {
            String ref = item.source().equals("ROUTE") ? "route:" + item.id()
                    : flowClientIds.containsKey(item.id()) ? "flow:" + flowClientIds.get(item.id()) : null;
            if (ref != null) {
                byRef.put(ref, item);
            }
        }
        if (byRef.isEmpty()) {
            return items;
        }
        Map<String, List<Handoff>> handoffs = feedbackRepository.findByAskRefIn(byRef.keySet()).stream()
                .sorted(Comparator.comparing(Feedback::getId))
                .collect(Collectors.groupingBy(Feedback::getAskRef, LinkedHashMap::new,
                        Collectors.mapping(this::handoff, Collectors.toList())));
        return items.stream().map(item -> {
            String ref = item.source().equals("ROUTE") ? "route:" + item.id()
                    : flowClientIds.containsKey(item.id()) ? "flow:" + flowClientIds.get(item.id()) : null;
            List<Handoff> found = ref == null ? null : handoffs.get(ref);
            return found == null ? item : item.withHandoffs(found);
        }).toList();
    }

    private Handoff handoff(Feedback feedback) {
        return new Handoff(feedback.getId(), feedback.getContent(), feedback.getContact(), feedback.getStatus().name(),
                feedback.getCreatedAt(), reportImageService.viewUrls(feedback.getImageKeyList()));
    }

    private static List<String> lines(String text, String sep) {
        return text == null || text.isBlank() ? List.of()
                : Arrays.stream(text.split(sep)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> split(String csv) {
        return lines(csv, ",");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 기록입니다.");
    }
}
