package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 경로망 임포트 결과. dryRun 이면 아무것도 바꾸지 않고 이 리포트만 준다. errors 가 하나라도 있으면 적용하지 않는다(applied=false).
 * - counts: 넣을(넣은) 중간점·출입구 노드·간선 수. 출입구 노드는 간선이 쓰는 참조만 만든다.
 * - idConversions: MapDataRules.CODE_REGEX 에 맞지 않아 바꾼 점 id.
 * - entranceRefs: 찾은 출입구 참조와 만든 노드 id, 무엇으로 찾았는지(matchedBy: buildingCode / displayName / name).
 * - errors: type = FORMAT, INVALID_NODE, DUPLICATE_NODE_ID, INVALID_COORD, INVALID_REF, BUILDING_NOT_FOUND,
 *   BUILDING_AMBIGUOUS, LABEL_NOT_FOUND, INVALID_EDGE, EDGE_UNKNOWN_NODE, SELF_LOOP, DUPLICATE_EDGE.
 * - warnings: 적용은 막지 않는 것 — 고립된 점, 본망과 끊긴 덩어리, 간선이 쓰지 않는 출입구 참조.
 */
public record PathImportReport(
        boolean dryRun,
        boolean applied,
        Integer format,
        String source,
        Counts counts,
        List<IdConversion> idConversions,
        List<EntranceRef> entranceRefs,
        List<Issue> errors,
        Warnings warnings
) {
    public record Counts(int waypoints, int entranceNodes, int edges) {
    }

    public record IdConversion(String from, String to) {
    }

    public record EntranceRef(String ref, String buildingCode, String label, String nodeId, String matchedBy) {
    }

    /** target 은 문제가 된 점 id·참조·간선 위치(edges[3] 등). candidates 는 LABEL_NOT_FOUND·BUILDING_AMBIGUOUS 때만. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issue(String type, String target, String message, List<String> candidates) {
    }

    public record Warnings(List<String> isolatedNodes, List<PathAuditResponse.Component> detachedComponents,
                           List<String> unusedEntranceRefs) {
    }
}
