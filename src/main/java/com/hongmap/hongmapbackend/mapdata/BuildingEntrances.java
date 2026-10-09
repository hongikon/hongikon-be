package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * buildings.entrances(JSON [{label, lat, lng, minFloor, maxFloor}]) 를 라벨별 좌표로 읽는다.
 * 경로망의 ENTRANCE 노드는 좌표를 저장하지 않고 여기서 해석한다. label·lat·lng 가 없는 항목은 건너뛰고,
 * 같은 라벨이 두 번이면 앞의 것을 쓴다. JSON 자체가 깨졌으면 invalid=true(출입구 없음으로 다룬다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildingEntrances {

    private final JsonMapper jsonMapper;

    /** 라벨 → 좌표(입력 순서 유지). */
    public record Parsed(Map<String, PathGeo.Point> byLabel, boolean invalid) {

        static final Parsed EMPTY = new Parsed(Map.of(), false);
        static final Parsed INVALID = new Parsed(Map.of(), true);

        public PathGeo.Point find(String label) {
            return byLabel.get(label);
        }
    }

    public Parsed parse(Building building) {
        String raw = building.getEntrances();
        if (raw == null || raw.isBlank()) {
            return Parsed.EMPTY;
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(raw);
        } catch (JacksonException e) {
            log.warn("건물 출입구 JSON 파싱 실패 (buildingId={})", building.getId());
            return Parsed.INVALID;
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            return Parsed.EMPTY;
        }
        if (!root.isArray()) {
            return Parsed.INVALID;
        }
        Map<String, PathGeo.Point> byLabel = new LinkedHashMap<>();
        for (JsonNode e : root) {
            JsonNode label = e.get("label");
            JsonNode lat = e.get("lat");
            JsonNode lng = e.get("lng");
            if (label == null || !label.isString() || label.asString().isBlank()
                    || lat == null || !lat.isNumber() || lng == null || !lng.isNumber()) {
                continue;
            }
            byLabel.putIfAbsent(label.asString(), new PathGeo.Point(lat.doubleValue(), lng.doubleValue()));
        }
        return new Parsed(Collections.unmodifiableMap(byLabel), false);
    }

    /** 건물 id → 출입구. 한 번에 여러 건물을 읽을 때(지도 데이터·점검·임포트). */
    public Map<Long, Parsed> index(Collection<Building> buildings) {
        Map<Long, Parsed> index = new HashMap<>();
        for (Building b : buildings) {
            index.put(b.getId(), parse(b));
        }
        return index;
    }
}
