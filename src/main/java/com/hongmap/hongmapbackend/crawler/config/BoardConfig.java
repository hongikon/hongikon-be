package com.hongmap.hongmapbackend.crawler.config;

import java.util.regex.Pattern;

/**
 * 크롤링 대상 게시판 설정 하나. hongmap(프론트) scripts/crawler/config.mjs 의 게시판 항목과 1:1 대응된다.
 *
 * @param boardKey             게시판 구분자(로그·식별용). 예: ce, univ
 * @param sourceId             학과 게시판이면 department.name 과 동일해야 한다 — NewsLocationMatcher가
 *                             이 값으로 Department를 조회해 news.department_id를 채운다.
 *                             대학공지처럼 학과가 아닌 분류(예: "학사", "장학")는 Department에 없는 라벨이라
 *                             매칭되지 않고 department_id는 null로 남는다. 정상 동작이다.
 * @param source               표시용 출처명. News 엔티티에는 이 값을 담을 별도 컬럼이 없어(department FK로 대체)
 *                             지금은 로그 출력용으로만 쓰인다.
 * @param listUrl              목록 URL
 * @param tableSummary         게시판 {@code <table summary="...">} 값. HongikBoardParser에서만 쓰인다.
 *                             null/blank면 문서 전체에서 행을 찾는다.
 * @param parser               쓰는 CMS(파서 종류)
 * @param excludeTitlePattern  제목이 이 패턴에 걸리면 수집하지 않는다. 없으면 null.
 * @param preferWriterAsSource 상세의 작성 부서를 출처로 우선할지 여부. News에는 저장할 자리가 없어
 *                             지금은 참고용 플래그로만 남겨둔다.
 * @param maxItems             이 게시판에서 가져올 최대 건수
 */
public record BoardConfig(
        String boardKey,
        String sourceId,
        String source,
        String listUrl,
        String tableSummary,
        ParserType parser,
        Pattern excludeTitlePattern,
        boolean preferWriterAsSource,
        int maxItems
) {
}
