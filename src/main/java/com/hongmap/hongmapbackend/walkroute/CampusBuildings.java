package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 길찾기가 보는 건물 목록 — GET /map/data 스냅숏의 buildings 를 그대로 읽는다(앱과 같은 건물·출입구·층). 지도 데이터 version 별로 한 번만 읽는다. */
@Component
class CampusBuildings {

    record Snapshot(String version, List<CampusBuilding> list, Map<Long, CampusBuilding> byId) {
    }

    private final MapDataService mapDataService;
    private final JsonMapper jsonMapper;
    private volatile Snapshot cached;

    CampusBuildings(MapDataService mapDataService, JsonMapper jsonMapper) {
        this.mapDataService = mapDataService;
        this.jsonMapper = jsonMapper;
    }

    Snapshot current() {
        MapDataService.Snapshot snapshot = mapDataService.current();
        Snapshot c = cached;
        if (c != null && c.version().equals(snapshot.version())) {
            return c;
        }
        List<CampusBuilding> list = new ArrayList<>();
        Map<Long, CampusBuilding> byId = new HashMap<>();
        for (JsonNode node : jsonMapper.readTree(snapshot.body()).path("buildings")) {
            CampusBuilding building = CampusBuilding.from(node);
            if (building == null) {
                continue;
            }
            list.add(building);
            if (building.id() != null) {
                byId.put(building.id(), building);
            }
        }
        c = new Snapshot(snapshot.version(), List.copyOf(list), Map.copyOf(byId));
        cached = c;
        return c;
    }
}
