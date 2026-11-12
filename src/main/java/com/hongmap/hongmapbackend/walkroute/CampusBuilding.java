package com.hongmap.hongmapbackend.walkroute;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 길찾기가 보는 건물 — GET /map/data 의 buildings[] 와 같은 값(앱이 보는 것과 같게 그 응답을 그대로 읽는다).
 * name 은 표시 이름, entrances 는 층별 출입구(없으면 빈 목록).
 */
record CampusBuilding(Long id, String code, String name, double lat, double lng, Integer floors, Integer basementFloors,
                      List<Double> floorLevels, List<Entrance> entrances) {

    record Entrance(String label, double lat, double lng, double minFloor, double maxFloor) {
    }

    /** 이름·좌표가 빠진 건물은 앱(normalizeMapData)처럼 버린다(null). */
    static CampusBuilding from(JsonNode node) {
        JsonNode name = node.get("name");
        JsonNode lat = node.get("lat");
        JsonNode lng = node.get("lng");
        if (name == null || !name.isString() || !isFinite(lat) || !isFinite(lng)) {
            return null;
        }
        List<Entrance> entrances = new ArrayList<>();
        JsonNode list = node.get("entrances");
        if (list != null && list.isArray()) {
            for (JsonNode e : list) {
                JsonNode label = e.get("label");
                if (label == null || !label.isString() || !isFinite(e.get("lat")) || !isFinite(e.get("lng"))
                        || !isFinite(e.get("minFloor")) || !isFinite(e.get("maxFloor"))) {
                    continue;
                }
                entrances.add(new Entrance(label.asString(), e.get("lat").asDouble(), e.get("lng").asDouble(),
                        e.get("minFloor").asDouble(), e.get("maxFloor").asDouble()));
            }
        }
        JsonNode id = node.get("id");
        JsonNode code = node.get("code");
        JsonNode floors = node.get("floors");
        JsonNode basementFloors = node.get("basementFloors");
        List<Double> floorLevels = new ArrayList<>();
        JsonNode levels = node.get("floorLevels");
        if (levels != null && levels.isArray()) {
            for (JsonNode level : levels) {
                if (isFinite(level)) {
                    floorLevels.add(level.asDouble());
                }
            }
        }
        return new CampusBuilding(
                id != null && id.isNumber() ? id.asLong() : null,
                code != null && code.isString() ? code.asString() : null,
                name.asString(), lat.asDouble(), lng.asDouble(),
                floors != null && floors.isNumber() ? floors.asInt() : null,
                basementFloors != null && basementFloors.isNumber() ? basementFloors.asInt() : null,
                List.copyOf(floorLevels), List.copyOf(entrances));
    }

    private static boolean isFinite(JsonNode n) {
        return n != null && n.isNumber() && Double.isFinite(n.asDouble());
    }
}
