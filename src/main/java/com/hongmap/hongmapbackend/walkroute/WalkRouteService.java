package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 건물(층)에서 건물(층)까지 걷는 길 후보. 건물은 GET /map/data 와 같은 값(MapDataService 스냅숏)을 읽어, 앱이 보는 건물·출입구와
 * 늘 같다. 지도 데이터가 바뀌면(version) 그래프를 다시 만든다. 같은 질문은 결과를 기억해 둔다(건물·층 조합이 많지 않다).
 */
@Service
public class WalkRouteService {

    /** 고를 수 있는 층의 범위(지하 10층~지상 60층). 그 밖이면 400. */
    static final double MIN_FLOOR = -10;
    static final double MAX_FLOOR = 60;
    private static final int RESULT_CACHE_SIZE = 2000;
    private static final int APPROACH_CACHE_SIZE = 200;

    private final MapDataService mapDataService;
    private final JsonMapper jsonMapper;
    private final SurveyGraph survey;
    private volatile Built built;
    private final Map<String, List<WalkRoute>> results = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<WalkRoute>> eldest) {
            return size() > RESULT_CACHE_SIZE;
        }
    });

    private final Map<String, WalkRouter.Approach> approaches = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, WalkRouter.Approach> eldest) {
            return size() > APPROACH_CACHE_SIZE;
        }
    });

    private record Built(String version, WalkRouter router, Map<Long, CampusBuilding> byId) {
    }

    public WalkRouteService(MapDataService mapDataService, JsonMapper jsonMapper) {
        this.mapDataService = mapDataService;
        this.jsonMapper = jsonMapper;
        this.survey = SurveyGraph.load(jsonMapper);
    }

    /** 경로망 조사 자료를 내보낸 시각(응답·확인용). */
    public String graphExportedAt() {
        return survey.exportedAt();
    }

    public List<WalkRoute> routes(long fromId, Double fromFloor, long toId, Double toFloor) {
        checkFloor(fromFloor);
        checkFloor(toFloor);
        if (fromId == toId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "출발과 도착이 같은 건물입니다");
        }
        Built current = current();
        CampusBuilding from = current.byId().get(fromId);
        CampusBuilding to = current.byId().get(toId);
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다");
        }
        String key = current.version() + "|" + fromId + "|" + fromFloor + "|" + toId + "|" + toFloor;
        List<WalkRoute> cached = results.get(key);
        if (cached != null) {
            return cached;
        }
        List<WalkRoute> routes = current.router().routes(from, fromFloor, to, toFloor);
        results.put(key, routes);
        return routes;
    }

    /** 내 위치 출발용(위치는 받지 않는다): 도착 건물(층)까지 모든 실외 길 점에서의 길. */
    public WalkRouter.Approach approach(long toId, Double toFloor) {
        checkFloor(toFloor);
        Built current = current();
        CampusBuilding to = current.byId().get(toId);
        if (to == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다");
        }
        String key = current.version() + "|approach|" + toId + "|" + toFloor;
        WalkRouter.Approach cached = approaches.get(key);
        if (cached != null) {
            return cached;
        }
        WalkRouter.Approach approach = current.router().approach(to, toFloor);
        approaches.put(key, approach);
        return approach;
    }

    private static void checkFloor(Double floor) {
        if (floor != null && (!Double.isFinite(floor) || floor < MIN_FLOOR || floor > MAX_FLOOR)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "층 값이 올바르지 않습니다");
        }
    }

    private Built current() {
        MapDataService.Snapshot snapshot = mapDataService.current();
        Built b = built;
        if (b != null && b.version().equals(snapshot.version())) {
            return b;
        }
        synchronized (this) {
            b = built;
            if (b != null && b.version().equals(snapshot.version())) {
                return b;
            }
            List<CampusBuilding> buildings = new ArrayList<>();
            Map<Long, CampusBuilding> byId = new HashMap<>();
            JsonNode list = jsonMapper.readTree(snapshot.body()).path("buildings");
            for (JsonNode node : list) {
                CampusBuilding building = CampusBuilding.from(node);
                if (building == null) {
                    continue;
                }
                buildings.add(building);
                if (building.id() != null) {
                    byId.put(building.id(), building);
                }
            }
            b = new Built(snapshot.version(), new WalkRouter(survey, buildings), Map.copyOf(byId));
            built = b;
            results.clear();
            approaches.clear();
            return b;
        }
    }
}
