package com.hongmap.hongmapbackend.ask;

/** LLM 분류·임베딩 검색을 못 했다(꺼짐·호출 실패·형식 오류). 컨트롤러가 503 으로 바꾸고, 앱은 실패를 알린다(낱말 찾기로 답하지 않는다, 10-27). */
public class AskUnavailableException extends RuntimeException {

    public AskUnavailableException(String reason) {
        super(reason);
    }

    public AskUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
