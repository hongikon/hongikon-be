package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경로망: GET /map/data 의 paths(모양·출입구 좌표 해석·깨진 참조 제외·캐시 무효화)와
 * 관리자 /admin/map/path-nodes·path-edges·path-audit(권한·검증·409·점검).
 * H2 를 다른 테스트와 같이 쓰지만 경로망 테이블은 이 클래스와 임포트 테스트만 쓴다 — 테스트마다 비우고 시작한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PathNetworkIntegrationTest {

    /** A동 출입구 A_1F 와 B동 출입구 B_1F 는 230m 쯤 떨어져 있다(긴 간선 확인용). */
    private static final String ENTRANCES_A =
            "[{\"label\":\"A_1F\",\"lat\":37.55,\"lng\":126.925,\"minFloor\":1,\"maxFloor\":1},"
            + "{\"label\":\"A_2F\",\"lat\":37.5501,\"lng\":126.9251,\"minFloor\":2,\"maxFloor\":2}]";
    private static final String ENTRANCES_B =
            "[{\"label\":\"B_1F\",\"lat\":37.552,\"lng\":126.926,\"minFloor\":1,\"maxFloor\":1}]";

    /** H2 는 JSON 컬럼에 문자열을 넣으면 JSON 문자열 값으로 저장한다 — FORMAT JSON 으로 배열 그대로 넣는다(MySQL 은 그냥 파싱). */
    private static final String SET_ENTRANCES = "UPDATE buildings SET entrances = ? FORMAT JSON WHERE id = ?";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired PathNodeRepository nodeRepository;
    @Autowired PathEdgeRepository edgeRepository;
    @Autowired MapDataService mapDataService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JsonMapper jsonMapper;

    User admin;
    User normal;
    Building buildingA;
    Building buildingB;
    String entranceA1;   // e-{A code}-A_1F

    @BeforeEach
    void setUp() {
        clearPaths();
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        normal = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buildingA = buildingRepository.save(Building.builder()
                .name("경로A-" + suffix).displayName("경로관 A동-" + suffix).code("test_pa_" + suffix)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .entrances(ENTRANCES_A).sortOrder(-1000).build());
        buildingB = buildingRepository.save(Building.builder()
                .name("경로B-" + suffix).displayName("경로관 B동-" + suffix).code("test_pb_" + suffix)
                .latitude(new BigDecimal("37.5520000")).longitude(new BigDecimal("126.9260000"))
                .entrances(ENTRANCES_B).sortOrder(-999).build());
        entranceA1 = "e-" + buildingA.getCode() + "-A_1F";
        mapDataService.invalidate();
    }

    @AfterEach
    void tearDown() {
        clearPaths();
        buildingRepository.delete(buildingA);
        buildingRepository.delete(buildingB);
        mapDataService.invalidate();
    }

    private void clearPaths() {
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();
    }

    // ── GET /map/data paths ──────────────────────────────

    @Test
    void 경로망이_없어도_paths_는_빈_모양으로_맨_끝에_나온다() throws Exception {
        JsonNode body = mapData();
        List<String> keys = body.properties().stream().map(e -> e.getKey()).toList();
        assertThat(keys.get(keys.size() - 1)).isEqualTo("paths");
        assertThat(jsonMapper.writeValueAsString(body.get("paths"))).isEqualTo("{\"nodes\":[],\"edges\":[]}");
    }

    @Test
    void 만든_경로망이_map_data_에_code_순으로_나오고_출입구_좌표는_건물_entrances_에서_읽는다() throws Exception {
        createWaypoint("n1", "37.5500500", "126.9250500").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("n1")).andExpect(jsonPath("$.kind").value("WAYPOINT"));
        createWaypoint("n2", "37.5501500", "126.9251500").andExpect(status().isCreated());
        createEntrance("", buildingA.getCode(), "A_1F").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(entranceA1))
                .andExpect(jsonPath("$.lat").value(37.55))
                .andExpect(jsonPath("$.buildingCode").value(buildingA.getCode()));
        createEdge("n2", "n1").andExpect(status().isCreated()).andExpect(jsonPath("$.lengthM").isNumber());
        createEdge(entranceA1, "n2").andExpect(status().isCreated());

        JsonNode paths = mapData().get("paths");
        JsonNode nodes = paths.get("nodes");
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).get("id").asString()).isEqualTo(entranceA1);   // "e-…" < "n1" < "n2"
        assertThat(nodes.get(0).get("lat").asDouble()).isEqualTo(37.55);
        assertThat(nodes.get(0).get("lng").asDouble()).isEqualTo(126.925);
        assertThat(nodes.get(0).get("entrance").get("building").asString()).isEqualTo(buildingA.getCode());
        assertThat(nodes.get(0).get("entrance").get("label").asString()).isEqualTo("A_1F");
        assertThat(nodes.get(1).get("id").asString()).isEqualTo("n1");
        assertThat(nodes.get(1).has("entrance")).isFalse();
        assertThat(nodes.get(1).get("lat").asDouble()).isEqualTo(37.55005);
        // [작은 id, 큰 id] 를 정렬 — 넣은 방향(n2→n1)과 상관없다
        assertThat(jsonMapper.writeValueAsString(paths.get("edges")))
                .isEqualTo("[[\"" + entranceA1 + "\",\"n2\"],[\"n1\",\"n2\"]]");
    }

    @Test
    void 경로_쓰기는_캐시를_바로_비우고_실패한_쓰기는_version_을_바꾸지_않는다() throws Exception {
        createWaypoint("n1", "37.5500500", "126.9250500");
        createWaypoint("n2", "37.5501500", "126.9251500");
        String etag1 = etag();
        mockMvc.perform(get("/map/data").header("If-None-Match", etag1)).andExpect(status().isNotModified());

        createEdge("n1", "n2").andExpect(status().isCreated());
        String etag2 = etag();
        assertThat(etag2).isNotEqualTo(etag1);
        mockMvc.perform(get("/map/data").header("If-None-Match", etag1)).andExpect(status().isOk());

        createEdge("n2", "n1").andExpect(status().isConflict());   // 실패 → 그대로
        mockMvc.perform(get("/map/data").header("If-None-Match", etag2)).andExpect(status().isNotModified());

        mockMvc.perform(put("/admin/map/path-nodes/n1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"WAYPOINT\",\"lat\":37.5502,\"lng\":126.9252}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lat").value(37.5502)).andExpect(jsonPath("$.degree").value(1));
        assertThat(etag()).isNotEqualTo(etag2);
    }

    @Test
    void 출입구_좌표가_바뀌면_paths_와_version_이_따라_바뀐다() throws Exception {
        createEntrance("", buildingA.getCode(), "A_1F").andExpect(status().isCreated());
        String etag1 = etag();

        jdbcTemplate.update(SET_ENTRANCES,
                ENTRANCES_A.replace("\"lat\":37.55,", "\"lat\":37.5503,"), buildingA.getId());
        mapDataService.invalidate();   // SQL 로 직접 고친 경우는 TTL 이 지나야 반영된다 — 여기서는 바로 비운다

        assertThat(etag()).isNotEqualTo(etag1);
        assertThat(mapData().get("paths").get("nodes").get(0).get("lat").asDouble()).isEqualTo(37.5503);
    }

    @Test
    void 출입구_라벨이_사라지면_그_점과_간선만_빠지고_점검에_나온다() throws Exception {
        createWaypoint("n1", "37.5500500", "126.9250500");
        createWaypoint("n2", "37.5501500", "126.9251500");
        createEntrance("", buildingA.getCode(), "A_1F");
        createEntrance("", buildingB.getCode(), "B_1F");
        createEdge("n1", "n2");
        createEdge("n1", entranceA1);
        String entranceB1 = "e-" + buildingB.getCode() + "-B_1F";
        createEdge("n2", entranceB1);

        // 동기화 SQL 이 A_1F 를 지우고, B 동 entrances 를 깨진 JSON 으로 덮어쓴 상황
        jdbcTemplate.update(SET_ENTRANCES,
                "[{\"label\":\"A_2F\",\"lat\":37.5501,\"lng\":126.9251}]", buildingA.getId());
        jdbcTemplate.update(SET_ENTRANCES, "{\"oops\":1}", buildingB.getId());
        mapDataService.invalidate();

        JsonNode paths = mapData().get("paths");
        assertThat(paths.get("nodes")).hasSize(2);
        assertThat(jsonMapper.writeValueAsString(paths.get("edges"))).isEqualTo("[[\"n1\",\"n2\"]]");

        mockMvc.perform(get("/admin/map/path-audit").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokenEntranceRefs.length()").value(2))
                .andExpect(jsonPath("$.brokenEntranceRefs[?(@.node == '" + entranceA1 + "')].reason").value("LABEL_MISSING"))
                .andExpect(jsonPath("$.brokenEntranceRefs[?(@.node == '" + entranceB1 + "')].reason").value("ENTRANCES_INVALID"));
        mockMvc.perform(get("/admin/map/path-nodes").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.nodes[?(@.id == '" + entranceA1 + "')].broken").value(true))
                .andExpect(jsonPath("$.nodes[?(@.id == 'n1')].broken").value(false));
    }

    // ── 관리자 편집: 409 ─────────────────────────────────

    @Test
    void 중복과_참조_중인_삭제는_409() throws Exception {
        createWaypoint("n1", "37.5500500", "126.9250500");
        createWaypoint("n2", "37.5501500", "126.9251500");
        createEntrance("", buildingA.getCode(), "A_1F");
        createEdge("n1", "n2").andExpect(status().isCreated());

        createWaypoint("n1", "37.5509", "126.9259").andExpect(status().isConflict());                 // 같은 id
        createEntrance("", buildingA.getCode(), "A_1F").andExpect(status().isConflict());             // 같은 출입구
        createEdge("n2", "n1").andExpect(status().isConflict())                                         // 역방향도 같은 간선
                .andExpect(jsonPath("$.message").value("이미 이어진 두 점이에요."));
        mockMvc.perform(delete("/admin/map/path-nodes/n1").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이어진 간선 1개를 먼저 지워 주세요."));

        // 간선을 지우면 점도 지울 수 있다
        Long edgeId = edgeRepository.findAll().get(0).getId();
        mockMvc.perform(delete("/admin/map/path-edges/" + edgeId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/admin/map/path-nodes/n1").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void 경로가_참조하는_건물은_지울_수_없다() throws Exception {
        createEntrance("", buildingA.getCode(), "A_1F").andExpect(status().isCreated());
        assertThatThrownBy(() -> buildingRepository.deleteById(buildingA.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB_제약도_종류별_필드와_간선_방향을_지킨다() throws Exception {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO path_nodes (code, kind, created_at, updated_at)"
                + " VALUES ('bad', 'WAYPOINT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        createWaypoint("n1", "37.5500500", "126.9250500");
        createWaypoint("n2", "37.5501500", "126.9251500");
        Long id1 = nodeRepository.findByCodeWithBuilding("n1").orElseThrow().getId();
        Long id2 = nodeRepository.findByCodeWithBuilding("n2").orElseThrow().getId();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO path_edges (node_a_id, node_b_id, created_at)"
                + " VALUES (?, ?, CURRENT_TIMESTAMP)", Math.max(id1, id2), Math.min(id1, id2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 관리자 편집: 400·404 ─────────────────────────────

    @Test
    void 잘못된_입력은_400() throws Exception {
        createWaypoint("n1", "37.5500500", "126.9250500");
        createEntrance("", buildingA.getCode(), "A_1F");
        String a = buildingA.getCode();
        String[][] cases = {
                {"{\"kind\":\"WAYPOINT\",\"lat\":37.55}", "중간점은 위도와 경도를 함께 입력해 주세요."},
                {"{\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92,\"buildingCode\":\"" + a + "\"}", "중간점에는 건물·출입구를 넣지 않아요."},
                {"{\"kind\":\"STAIRS\",\"lat\":37.55,\"lng\":126.92}", "종류는 WAYPOINT 또는 ENTRANCE 예요."},
                {"{\"id\":\"bad id!\",\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92}", "id 는 영문·숫자·-·_ 로 100자 이하여야 해요."},
                {"{\"kind\":\"WAYPOINT\",\"lat\":40,\"lng\":126.92}", "위도는 33~39 사이여야 해요."},
                {"{\"kind\":\"ENTRANCE\",\"buildingCode\":\"" + a + "\",\"entranceLabel\":\"A_2F\",\"lat\":37.55,\"lng\":126.92}",
                        "출입구 노드의 좌표는 건물 출입구에서 읽어요 — 위도·경도를 비워 주세요."},
                {"{\"kind\":\"ENTRANCE\",\"entranceLabel\":\"A_2F\"}", "출입구 노드는 건물과 출입구 라벨을 골라 주세요."},
                {"{\"kind\":\"ENTRANCE\",\"buildingCode\":\"no_such\",\"entranceLabel\":\"A_2F\"}", "존재하지 않는 건물이에요."},
                {"{\"kind\":\"ENTRANCE\",\"buildingCode\":\"" + a + "\",\"entranceLabel\":\"A_9F\"}", "그 건물에 없는 출입구 라벨이에요."},
                {"{\"id\":\"mine\",\"kind\":\"ENTRANCE\",\"buildingCode\":\"" + a + "\",\"entranceLabel\":\"A_2F\"}",
                        "출입구 노드 id 는 e-" + a + "-A_2F 로 정해져요 — 비우거나 같은 값을 넣어 주세요."},
        };
        for (String[] c : cases) {
            mockMvc.perform(post("/admin/map/path-nodes").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(c[1]));
        }
        createEdge("n1", "n1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("같은 점끼리는 이을 수 없어요."));
        createEdge("n1", "nope").andExpect(status().isBadRequest());
        // 출입구 노드는 고칠 수 없고, 중간점을 출입구로 바꿀 수도 없다
        mockMvc.perform(put("/admin/map/path-nodes/" + entranceA1).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/admin/map/path-nodes/n1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ENTRANCE\",\"buildingCode\":\"" + a + "\",\"entranceLabel\":\"A_2F\"}"))
                .andExpect(status().isBadRequest());
        // id 를 비우면 pn-xxxxxxxx, 출입구 노드 id 를 같은 값으로 넣는 것은 된다
        createWaypoint("", "37.5509", "126.9259").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("^pn-[a-z0-9]{8}$")));
        createEntrance("e-" + a + "-A_2F", a, "A_2F").andExpect(status().isCreated());
    }

    @Test
    void 없는_대상은_404() throws Exception {
        String body = "{\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92}";
        mockMvc.perform(put("/admin/map/path-nodes/nope").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/admin/map/path-nodes/nope").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/admin/map/path-edges/999999").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 경로망_관리자_API는_비로그인_401_일반사용자_403() throws Exception {
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>(List.of(
                get("/admin/map/path-nodes"),
                post("/admin/map/path-nodes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92}"),
                put("/admin/map/path-nodes/n1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"WAYPOINT\",\"lat\":37.55,\"lng\":126.92}"),
                delete("/admin/map/path-nodes/n1"),
                get("/admin/map/path-edges"),
                post("/admin/map/path-edges").contentType(MediaType.APPLICATION_JSON).content("{\"a\":\"n1\",\"b\":\"n2\"}"),
                delete("/admin/map/path-edges/1"),
                get("/admin/map/path-audit"),
                post("/admin/map/path-network/import").contentType(MediaType.APPLICATION_JSON).content("{\"format\":1}")));
        for (MockHttpServletRequestBuilder r : requests) {
            mockMvc.perform(r).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder r : requests) {
            mockMvc.perform(r.header("Authorization", bearer(normal))).andExpect(status().isForbidden());
        }
        assertThat(nodeRepository.count()).isZero();
    }

    // ── 점검 ─────────────────────────────────────────────

    @Test
    void 점검은_고립된_점_끊긴_덩어리_경로에_없는_출입구_긴_간선을_알려준다() throws Exception {
        // 본망: n1 – n2 – e-A_1F, n2 – e-B_1F(230m 쯤) / 끊긴 덩어리: n4 – n5 / 고립: n3
        createWaypoint("n1", "37.5500500", "126.9250500");
        createWaypoint("n2", "37.5501500", "126.9251500");
        createWaypoint("n3", "37.5505", "126.9255");
        createWaypoint("n4", "37.5506", "126.9256");
        createWaypoint("n5", "37.5507", "126.9257");
        createEntrance("", buildingA.getCode(), "A_1F");
        String entranceB1 = "e-" + buildingB.getCode() + "-B_1F";
        createEntrance("", buildingB.getCode(), "B_1F");
        createEdge("n1", "n2");
        createEdge("n2", entranceA1);
        createEdge("n2", entranceB1);
        createEdge("n4", "n5");

        MvcResult result = mockMvc.perform(get("/admin/map/path-audit").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.nodes").value(7))
                .andExpect(jsonPath("$.summary.edges").value(4))
                .andExpect(jsonPath("$.summary.components").value(3))
                .andExpect(jsonPath("$.summary.mainComponentSize").value(4))
                .andExpect(jsonPath("$.brokenEntranceRefs.length()").value(0))
                .andExpect(jsonPath("$.isolatedNodes[0]").value("n3"))
                .andExpect(jsonPath("$.isolatedNodes.length()").value(1))
                .andExpect(jsonPath("$.detachedComponents[0].size").value(2))
                .andExpect(jsonPath("$.detachedComponents[0].nodes[0]").value("n4"))
                .andExpect(jsonPath("$.longEdgeThresholdM").value(100.0))
                .andExpect(jsonPath("$.longEdges.length()").value(1))
                .andExpect(jsonPath("$.longEdges[0].b").value(entranceB1))
                .andReturn();
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.get("longEdges").get(0).get("lengthM").asDouble()).isGreaterThan(AdminMapPathService.LONG_EDGE_METERS);
        List<String> unlinked = new ArrayList<>();
        for (JsonNode e : body.get("unlinkedEntrances")) {
            if (e.get("building").asString().equals(buildingA.getCode()) || e.get("building").asString().equals(buildingB.getCode())) {
                unlinked.add(e.get("label").asString());
            }
        }
        assertThat(unlinked).containsExactly("A_2F");

        mockMvc.perform(get("/admin/map/path-edges").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.edges.length()").value(4));
        mockMvc.perform(get("/admin/map/path-nodes").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.nodes.length()").value(7))
                .andExpect(jsonPath("$.nodes[?(@.id == 'n2')].degree").value(3));
    }

    // ── 도우미 ───────────────────────────────────────────

    private JsonNode mapData() throws Exception {
        MvcResult result = mockMvc.perform(get("/map/data")).andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String etag() throws Exception {
        return mockMvc.perform(get("/map/data")).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }

    private org.springframework.test.web.servlet.ResultActions createWaypoint(String id, String lat, String lng) throws Exception {
        return mockMvc.perform(post("/admin/map/path-nodes").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"kind\":\"WAYPOINT\",\"lat\":" + lat + ",\"lng\":" + lng + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions createEntrance(String id, String buildingCode, String label) throws Exception {
        return mockMvc.perform(post("/admin/map/path-nodes").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"kind\":\"ENTRANCE\",\"buildingCode\":\"" + buildingCode
                        + "\",\"entranceLabel\":\"" + label + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions createEdge(String a, String b) throws Exception {
        return mockMvc.perform(post("/admin/map/path-edges").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"a\":\"" + a + "\",\"b\":\"" + b + "\"}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }
}
