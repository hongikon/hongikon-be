package com.hongmap.hongmapbackend.ask.dto;

import java.util.List;

/**
 * @param nodeId         대표 안내(흐름/단계) 또는 "none"(맞는 안내 없음 — 앱은 부서 찾기로). 되묻기 단계면 앱이 버튼을 보인다.
 *                       앱이 모르는 id 면(카탈로그가 앱보다 새것) 앱은 낱말 찾기로 돌아간다.
 * @param nodeIds        답의 근거 안내들(대표 포함, 앱이 '근거 안내 보기'로 원문을 펼친다).
 * @param answer         안내 원문 안에서만 쓴 답. 되묻기·none·답 쓰기 실패면 null — 앱은 원문 안내를 그대로 보인다.
 * @param found          원문에서 확인한 답이면 true, 확인되지 않아 담당 부서를 안내한 답이면 false. answer 가 null 이면 null.
 * @param catalogVersion 서버가 쓴 카탈로그 버전(scripts/ask/export-catalog.mjs 가 매긴 해시).
 * @param references     답의 근거로 쓴 학교 공지·학식(제목·원문 링크·날짜). 앱이 근거 목록에 링크로 붙인다.
 */
public record AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion,
                               List<Link> references) {

    public AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion) {
        this(nodeId, nodeIds, answer, found, catalogVersion, List.of());
    }

    /** @param date 공지 게시일 또는 학식 날짜(yyyy-MM-dd) */
    public record Link(String title, String url, String date) {
    }
}
