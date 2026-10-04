package com.hongmap.hongmapbackend.common.moderation;

/** {@link ContentFilter} 가 막은 이유. message 는 그대로 400 응답 본문(앱이 serverMessage 로 보여 준다). */
public enum ContentViolation {

    LINK("댓글에는 링크를 쓸 수 없어요."),
    CONTACT("댓글에 연락처나 오픈채팅 주소는 쓸 수 없어요."),
    PROFANITY("부적절한 표현이 있어 댓글을 올릴 수 없어요. 표현을 바꿔 다시 시도해 주세요.");

    private final String message;

    ContentViolation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
