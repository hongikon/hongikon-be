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
 * @param pages          근거 안내의 학교 페이지 버튼(안내마다 첫 링크, 같은 주소는 한 번, 근거 순서).
 * @param phones         근거 안내의 담당 부서 전화 버튼(같은 번호는 한 번, 최대 3).
 * @param facilityId     근거 안내의 위치(지도 편의시설 id, 처음 나온 것). 없으면 null.
 *                       버튼은 서버가 정해 보낸다 — 링크 순서·전화를 바꿀 때 앱 배포 없이 서버만 고치면 된다.
 * @param sections       답 아래 소제목별 목록·표(서버가 구조로 준다 — 앱이 글을 잘라 추측하지 않게). 없으면 빈 목록.
 * @param logId          이 답의 기록 id(ask_conversation_logs). 앱이 '해결됐나요' 응답을 붙일 때 쓴다. 기록이 꺼졌으면 null.
 */
public record AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion,
                               List<Link> references, List<Link> pages, List<Phone> phones, String facilityId, Long logId,
                               List<Section> sections) {

    public AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion) {
        this(nodeId, nodeIds, answer, found, catalogVersion, List.of(), List.of(), List.of(), null, null, List.of());
    }

    public AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion,
                            List<Link> references) {
        this(nodeId, nodeIds, answer, found, catalogVersion, references, List.of(), List.of(), null, null, List.of());
    }

    public AskRouteResponse(String nodeId, List<String> nodeIds, String answer, Boolean found, String catalogVersion,
                            List<Link> references, List<Link> pages, List<Phone> phones, String facilityId) {
        this(nodeId, nodeIds, answer, found, catalogVersion, references, pages, phones, facilityId, null, List.of());
    }

    public AskRouteResponse withLogId(Long id) {
        return new AskRouteResponse(nodeId, nodeIds, answer, found, catalogVersion, references, pages, phones, facilityId, id, sections);
    }

    public AskRouteResponse withSections(List<Section> value) {
        return new AskRouteResponse(nodeId, nodeIds, answer, found, catalogVersion, references, pages, phones, facilityId, logId,
                value == null ? List.of() : value);
    }

    public record Phone(String label, String phone) {
    }

    /**
     * 답 아래 한 덩어리. items(목록) 또는 table(표) 중 하나 이상.
     * @param ordered 순서가 있는 절차면 true(앱이 1. 2. 로 그린다)
     */
    public record Section(String title, List<String> items, boolean ordered, Table table) {
    }

    /** @param head 머리 행(열 이름), rows 칸 값(원문 표현 그대로). 열·행 상한은 서버가 자른다. */
    public record Table(List<String> head, List<List<String>> rows) {
    }

    /** @param date 공지 게시일 또는 학식 날짜(yyyy-MM-dd), 학교 페이지면 null */
    public record Link(String title, String url, String date) {
    }
}
