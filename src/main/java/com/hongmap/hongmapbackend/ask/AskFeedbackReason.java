package com.hongmap.hongmapbackend.ask;

import java.util.Locale;

/**
 * '해결 안 됨'을 고른 학생이 한 번 눌러 고르는 이유(11-06). 앱 버튼 글자는 FE AskChat 이 정한다.
 * DB 에는 이름(varchar 20)으로 남는다 — 이름을 바꾸면 옛 행을 읽지 못하니 더하기만 한다.
 */
public enum AskFeedbackReason {
    /** 틀린 정보예요 */
    WRONG,
    /** 지난 정보예요(날짜·기간이 지남) */
    OUTDATED,
    /** 물어본 것과 다른 답이에요 */
    OFF_TOPIC,
    /** 내용이 부족해요 */
    NOT_ENOUGH;

    /** 앱이 보낸 값 → 이유. 비었거나 모르는 값이면 null(옛 앱·새 이유를 아는 앱 모두 400 없이 받는다). */
    public static AskFeedbackReason parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
