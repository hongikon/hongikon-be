package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushSender;
import com.hongmap.hongmapbackend.push.PushProperties;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 댓글 조치 알림 푸시(Expo, data.type = COMMENT_MODERATED, data.reportId·commentId·status).
 * 관리자 숨김·삭제(PATCH /admin/comments/{id})와 신고 누적 자동 숨김이 커밋된 뒤 별도 스레드에서 돈다 — 관리자·신고자 응답을
 * 늦추지 않고, 발송 실패는 로그만 남긴다(조치 자체는 이미 끝났다).
 * <ul>
 *   <li>이용약관 제10조: 게시물 숨김·삭제는 사유를 알리고 14일 안에 이의를 제기할 수 있게 한다 — 본문에 사유(없으면
 *       "운영 정책 위반")와 이의 제기 안내를 싣는다.</li>
 *   <li>설정과 관계없이 보낸다 — 위 사유·이의 제기 안내는 약관상 알려야 하는 것이라 "내 제보 결과 알림"(report_status_enabled)을
 *       꺼도 받는다. 푸시 자체가 꺼져 있으면(push.enabled=false) 보내지 않는다.</li>
 *   <li>본문에는 제보 제목만 — 댓글 내용은 싣지 않는다(잠금 화면에 그대로 보이고, 숨긴 내용을 다시 퍼뜨리지 않게).
 *       신고자 정보도 싣지 않는다(보복 방지).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentModerationPushDispatcher {

    static final String DATA_TYPE = "COMMENT_MODERATED";
    static final String TITLE_HIDDEN = "댓글이 운영 정책에 따라 숨겨졌어요";
    static final String TITLE_DELETED = "댓글이 운영 정책에 따라 삭제됐어요";
    static final String AUTO_HIDDEN_NOTE = "신고가 여러 건 접수돼 운영진 확인 전까지 숨겨졌어요";
    static final String DEFAULT_REASON = "운영 정책 위반";
    static final String OBJECTION_NOTICE = "이의가 있으면 14일 안에 문의하기나 hongikonsupport@gmail.com 으로 알려 주세요";
    private static final int MAX_REASON_LENGTH = 80;
    private static final int MAX_TITLE_EXCERPT = 30;

    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentModerated(ReportCommentModeratedEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.warn("댓글 조치 알림 실패 (commentId={}, status={}): {}", event.commentId(), event.status(), e.getMessage());
        }
    }

    /** 동기 실행 — 리스너와 테스트가 쓴다. Expo 가 받아들인 메시지 수. */
    public int dispatch(ReportCommentModeratedEvent event) {
        if (!properties.isEnabled() || event.status() == ReportCommentStatus.VISIBLE) {
            return 0;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : userDeviceRepository.findByUserIdAndActiveTrue(event.authorId())) {
            if (device.getTokenType() == TokenType.EXPO) {
                tokens.add(device.getPushToken());
            }
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        String title = event.status() == ReportCommentStatus.DELETED ? TITLE_DELETED : TITLE_HIDDEN;
        String body = body(event);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", DATA_TYPE);
        data.put("reportId", event.reportId());
        data.put("commentId", event.commentId());
        data.put("status", event.status().name());

        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, title, body, data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("댓글 조치 알림 푸시: commentId={}, status={}, automatic={}, 메시지 {}건 중 {}건 접수",
                event.commentId(), event.status(), event.automatic(), messages.size(), result.accepted());
        return result.accepted();
    }

    /** "'붕어빵 트럭' 제보에 남긴 댓글\n사유: …\n이의가 있으면 …" */
    static String body(ReportCommentModeratedEvent event) {
        StringBuilder body = new StringBuilder()
                .append("'").append(truncate(event.reportTitle(), MAX_TITLE_EXCERPT)).append("' 제보에 남긴 댓글\n");
        if (event.automatic()) {
            body.append(AUTO_HIDDEN_NOTE);
        } else {
            body.append("사유: ").append(event.reason() == null || event.reason().isBlank()
                    ? DEFAULT_REASON : truncate(event.reason(), MAX_REASON_LENGTH));
        }
        return body.append("\n").append(OBJECTION_NOTICE).toString();
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }
}
