package com.hongmap.hongmapbackend.mapdata.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /admin/map/path-network/import 본문 — 앱(hongikon-fe)이 pathNodes.ts 에서 내보낸 경로망(format 1).
 * <pre>
 * { "format": 1, "source": "hongikon-fe src/constants/pathNodes.ts @ abc1234",
 *   "nodes": [{ "id": "n110", "lat": 37.55, "lng": 126.92 }],
 *   "edges": [["n110", "n111"], ["n111", "홍문관 R동#HI_R_2F_ENTER"]],
 *   "entranceRefs": [{ "ref": "홍문관 R동#HI_R_2F_ENTER", "buildingName": "홍문관 R동", "buildingCode": "hongik_r", "label": "HI_R_2F_ENTER" }] }
 * </pre>
 * 간선 끝점은 nodes 의 id 이거나 출입구 참조 문자열('건물명#라벨'). entranceRefs 에 없는 참조 문자열도 마지막 '#' 으로 나눠 읽는다.
 */
public record PathImportRequest(
        @NotNull(message = "format 이 필요해요.") Integer format,
        @Size(max = 500, message = "source 는 500자 이하여야 해요.") String source,
        @Size(max = 5000, message = "점은 5000개까지 넣을 수 있어요.") List<Node> nodes,
        @Size(max = 20000, message = "간선은 20000개까지 넣을 수 있어요.") List<List<String>> edges,
        @Size(max = 2000, message = "출입구 참조는 2000개까지 넣을 수 있어요.") List<EntranceRef> entranceRefs
) {
    public record Node(String id, BigDecimal lat, BigDecimal lng) {
    }

    /** buildingCode 가 있으면 그것으로, 없으면 buildingName 을 display_name → name 순으로 찾는다. label 은 정확히 일치. */
    public record EntranceRef(String ref, String buildingName, String buildingCode, String label) {
    }
}
