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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 건물(층)에서 건물(층)까지 걷는 길 후보. 건물은 GET /map/data 와 같은 값(MapDataService 스냅숏)을 읽어, 앱이 보는 건물·출입구와
 * 늘 같다. 지도 데이터가 바뀌면(version) 그래프를 다시 만든다.
 *
 * <p>층은 그 건물에서 고를 수 있는 층(앱 층 다이얼과 같은 값)만 받는다 — 아무 숫자나 받으면 매번 새로 계산하게 할 수 있다.
 * 내 위치 출발용 approach 는 계산이 무거워(목적지마다 2초 남짓) 한 번에 하나만 계산하고, 만든 응답(JSON)을 기억해 둔다.
 */
@Service
public class WalkRouteService {

    private static final int RESULT_CACHE_SIZE = 2000;
    private static final int APPROACH_CACHE_SIZE = 120;
    /** approach 계산을 기다리는 최대 시간. 넘으면 503(앱은 직선 어림). */
    private static final long APPROACH_WAIT_SECONDS = 15;

    private final MapDataService mapDataService;
    private final JsonMapper jsonMapper;
    private final SurveyGraph survey;
    private final Semaphore approachSlot = new Semaphore(1, true);
    private volatile Built built;
    private final Map<String, List<WalkRoute>> results = lru(RESULT_CACHE_SIZE);
    private final Map<String, byte[]> approaches = lru(APPROACH_CACHE_SIZE);

    private record Built(String version, WalkRouter router, Map<Long, CampusBuilding> byId) {
    }

    public WalkRouteService(MapDataService mapDataService, JsonMapper jsonMapper) {
        this.mapDataService = mapDataService;
        this.jsonMapper = jsonMapper;
        this.survey = SurveyGraph.load(jsonMapper);
    }

    private static <V> Map<String, V> lru(int size) {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > size;
            }
        });
    }

    public List<WalkRoute> routes(long fromId, Double fromFloor, long toId, Double toFloor) {
        if (fromId == toId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "출발과 도착이 같은 건물입니다");
        }
        Built current = current();
        CampusBuilding from = building(current, fromId);
        CampusBuilding to = building(current, toId);
        checkFloor(from, fromFloor);
        checkFloor(to, toFloor);
        String key = current.version() + "|" + fromId + "|" + fromFloor + "|" + toId + "|" + toFloor;
        List<WalkRoute> cached = results.get(key);
        if (cached != null) {
            return cached;
        }
        List<WalkRoute> routes = current.router().routes(from, fromFloor, to, toFloor);
        results.put(key, routes);
        return routes;
    }

    /** 내 위치 출발용(위치는 받지 않는다): 도착 건물(층)까지 모든 실외 길 점에서의 후보. 응답 JSON 그대로. */
    public byte[] approachJson(long toId, Double toFloor) {
        Built current = current();
        CampusBuilding to = building(current, toId);
        checkFloor(to, toFloor);
        String key = current.version() + "|" + toId + "|" + toFloor;
        byte[] cached = approaches.get(key);
        if (cached != null) {
            return cached;
        }
        boolean acquired = false;
        try {
            acquired = approachSlot.tryAcquire(APPROACH_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "길찾기 계산이 밀려 있습니다");
            }
            // 기다리는 동안 같은 목적지를 다른 요청이 계산했을 수 있다.
            cached = approaches.get(key);
            if (cached != null) {
                return cached;
            }
            byte[] json = jsonMapper.writeValueAsBytes(current.router().approach(to, toFloor));
            approaches.put(key, json);
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "길찾기 계산이 끊겼습니다");
        } finally {
            if (acquired) {
                approachSlot.release();
            }
        }
    }

    private static CampusBuilding building(Built current, long id) {
        CampusBuilding building = current.byId().get(id);
        if (building == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다");
        }
        return building;
    }

    /** 층을 고르지 않았거나(null) 그 건물에서 고를 수 있는 층이면 통과. */
    private static void checkFloor(CampusBuilding building, Double floor) {
        if (floor != null && !Floors.floorOptions(building).contains(floor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "그 건물에서 고를 수 없는 층입니다");
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
