package com.hongmap.hongmapbackend.ask;

/** LLM 분류를 못 했다(꺼짐·호출 실패·형식 오류). 컨트롤러가 503 으로 바꾸고, 앱은 낱말 찾기로 돌아간다. */
public class AskUnavailableException extends RuntimeException {

    public AskUnavailableException(String reason) {
        super(reason);
    }

    public AskUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
