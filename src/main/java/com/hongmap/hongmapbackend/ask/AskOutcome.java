package com.hongmap.hongmapbackend.ask;

/** 챗봇 "물어보기"가 질문에 어떻게 답했는지. */
public enum AskOutcome {
    /** 안내 흐름(flowId·nodeId)으로 답함 */
    FLOW,
    /** 부서 검색 결과로 답함 */
    OFFICES,
    /** 맞는 답을 찾지 못함 */
    NONE
}
