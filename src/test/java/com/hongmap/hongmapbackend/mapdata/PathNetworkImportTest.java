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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경로망 임포트(POST /admin/map/path-network/import): dryRun 기본·리포트(id 변환·참조 찾기·경고),
 * 오류가 있으면 적용 거부(전부 아니면 전무), 적용 시 전체 교체·캐시 무효화·다시 넣어도 같은 결과.
 * 픽스처: src/test/resources/path-network/sample-format1.json (작은 샘플 — 실제 데이터는 앱이 내보낸 파일로 넣는다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PathNetworkImportTest {

    private static final String IMPORT = "/admin/map/path-network/import";

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
    final List<Building> buildings = new ArrayList<>();
    String sample;

    @BeforeEach
    void setUp() throws Exception {
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());

        // alpha: code 로 찾는다 / beta: display_name 으로 / delta: name 이 beta 의 display_name 과 같다(display_name 이 먼저)
        // gamma: display_name 없이 name 으로 / epsilon 둘: display_name 이 같다(모호)
        building("test_pi_alpha", "경로임포트A", "경로임포트 A동",
                "[{\"label\":\"A_1F\",\"lat\":37.55,\"lng\":126.925},{\"label\":\"A_2F\",\"lat\":37.5501,\"lng\":126.9251}]");
        building("test_pi_beta", "경로임포트B", "경로임포트 B동", "[{\"label\":\"B_1F\",\"lat\":37.5502,\"lng\":126.9252}]");
        building("test_pi_delta", "경로임포트 B동", null, "[{\"label\":\"B_1F\",\"lat\":37.5600,\"lng\":126.9300}]");
        building("test_pi_gamma", "경로임포트C", null, "[{\"label\":\"C_1F\",\"lat\":37.5503,\"lng\":126.9253}]");
        building("test_pi_eps1", "경로임포트E1", "경로임포트 중복", "[{\"label\":\"E_1F\",\"lat\":37.5504,\"lng\":126.9254}]");
        building("test_pi_eps2", "경로임포트E2", "경로임포트 중복", "[{\"label\":\"E_1F\",\"lat\":37.5505,\"lng\":126.9255}]");
        sample = new ClassPathResource("path-network/sample-format1.json").getContentAsString(StandardCharsets.UTF_8);
        mapDataService.invalidate();
    }

    @AfterEach
    void tearDown() {
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();
        buildingRepository.deleteAll(buildings);
        buildings.clear();
        mapDataService.invalidate();
    }

    private void building(String code, String name, String displayName, String entrancesJson) {
        buildings.add(buildingRepository.save(Building.builder()
                .code(code).name(name).displayName(displayName)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .entrances(entrancesJson).sortOrder(-1000).build()));
    }

    @Test
    void dryRun_이_기본이고_리포트만_주고_아무것도_바꾸지_않는다() throws Exception {
        nodeRepository.save(PathNode.waypoint("old1", new BigDecimal("37.5500000"), new BigDecimal("126.9250000")));

        MvcResult result = mockMvc.perform(post(IMPORT).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(sample))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.format").value(1))
                .andExpect(jsonPath("$.counts.waypoints").value(4))
                .andExpect(jsonPath("$.counts.entranceNodes").value(3))
                .andExpect(jsonPath("$.counts.edges").value(5))
                .andExpect(jsonPath("$.errors.length()").value(0))
                // 규칙에 맞지 않는 id 만 바꾸고 기록한다
                .andExpect(jsonPath("$.idConversions.length()").value(1))
                .andExpect(jsonPath("$.idConversions[0].from").value("n 3"))
                .andExpect(jsonPath("$.idConversions[0].to").value("n_3"))
                // 경고: 간선 없는 점, 간선이 쓰지 않는 참조
                .andExpect(jsonPath("$.warnings.isolatedNodes[0]").value("n4"))
                .andExpect(jsonPath("$.warnings.unusedEntranceRefs[0]").value("경로임포트 A동#A_2F"))
                .andReturn();

        JsonNode refs = jsonMapper.readTree(result.getResponse().getContentAsByteArray()).get("entranceRefs");
        // buildingCode 가 이름보다 먼저 — buildingName 이 'B동' 이어도 alpha
        assertThat(ref(refs, "경로임포트 A동#A_1F").get("buildingCode").asString()).isEqualTo("test_pi_alpha");
        assertThat(ref(refs, "경로임포트 A동#A_1F").get("matchedBy").asString()).isEqualTo("buildingCode");
        assertThat(ref(refs, "경로임포트 A동#A_1F").get("nodeId").asString()).isEqualTo("e-test_pi_alpha-A_1F");
        // display_name 이 name 보다 먼저 — delta 의 name 도 '경로임포트 B동' 이지만 beta
        assertThat(ref(refs, "경로임포트 B동#B_1F").get("buildingCode").asString()).isEqualTo("test_pi_beta");
        assertThat(ref(refs, "경로임포트 B동#B_1F").get("matchedBy").asString()).isEqualTo("displayName");
        // entranceRefs 에 없는 참조도 '건물명#라벨' 로 읽고, display_name 이 없으면 name
        assertThat(ref(refs, "경로임포트C#C_1F").get("buildingCode").asString()).isEqualTo("test_pi_gamma");
        assertThat(ref(refs, "경로임포트C#C_1F").get("matchedBy").asString()).isEqualTo("name");

        assertThat(nodeRepository.findAll()).extracting(PathNode::getCode).containsExactly("old1");
        assertThat(edgeRepository.count()).isZero();
    }

    @Test
    void 적용하면_경로망을_통째로_바꾸고_map_data_가_바로_바뀌며_다시_넣어도_같다() throws Exception {
        nodeRepository.save(PathNode.waypoint("old1", new BigDecimal("37.5500000"), new BigDecimal("126.9250000")));
        String etagBefore = etag();

        mockMvc.perform(post(IMPORT).param("dryRun", "false").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(sample))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.applied").value(true));

        assertThat(nodeRepository.findAll()).extracting(PathNode::getCode).containsExactlyInAnyOrder(
                "n1", "n2", "n_3", "n4", "e-test_pi_alpha-A_1F", "e-test_pi_beta-B_1F", "e-test_pi_gamma-C_1F");
        assertThat(edgeRepository.count()).isEqualTo(5);

        String etagAfter = etag();
        assertThat(etagAfter).isNotEqualTo(etagBefore);   // 커밋 직후 캐시를 비웠다
        JsonNode paths = mapData().get("paths");
        assertThat(paths.get("nodes")).hasSize(7);
        assertThat(jsonMapper.writeValueAsString(paths.get("edges"))).isEqualTo("["
                + "[\"e-test_pi_alpha-A_1F\",\"n2\"],"
                + "[\"e-test_pi_beta-B_1F\",\"n1\"],"
                + "[\"e-test_pi_gamma-C_1F\",\"n_3\"],"
                + "[\"n1\",\"n2\"],"
                + "[\"n2\",\"n_3\"]]");

        // 같은 파일을 다시 넣으면 DB id 는 바뀌어도 응답은 같다
        mockMvc.perform(post(IMPORT).param("dryRun", "false").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(sample))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(true));
        assertThat(etag()).isEqualTo(etagAfter);
    }

    @Test
    void 오류가_하나라도_있으면_리포트에_모두_싣고_적용은_400_으로_거부한다() throws Exception {
        nodeRepository.save(PathNode.waypoint("old1", new BigDecimal("37.5500000"), new BigDecimal("126.9250000")));
        String bad = """
                {
                  "format": 1,
                  "nodes": [
                    { "id": "n1", "lat": 37.5500500, "lng": 126.9250500 },
                    { "id": "n1", "lat": 37.5500600, "lng": 126.9250600 },
                    { "id": "n9", "lat": 10, "lng": 126.9250500 },
                    { "id": "", "lat": 37.55, "lng": 126.925 }
                  ],
                  "edges": [
                    ["n1", "n1"],
                    ["n1", "nX"],
                    ["n1", "n9"],
                    ["n9", "n1"],
                    ["n1"],
                    ["n1", "없는동#X"],
                    ["n1", "알파소문자"],
                    ["n9", "모호"],
                    ["n9", "코드없음"]
                  ],
                  "entranceRefs": [
                    { "ref": "없는동#X", "buildingName": "없는동", "label": "X" },
                    { "ref": "알파소문자", "buildingCode": "test_pi_alpha", "label": "a_1f" },
                    { "ref": "모호", "buildingName": "경로임포트 중복", "label": "E_1F" },
                    { "ref": "코드없음", "buildingCode": "no_such_code", "label": "A_1F" }
                  ]
                }
                """;

        MvcResult result = mockMvc.perform(post(IMPORT).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andReturn();
        JsonNode errors = jsonMapper.readTree(result.getResponse().getContentAsByteArray()).get("errors");
        List<String> types = new ArrayList<>();
        for (JsonNode e : errors) {
            types.add(e.get("type").asString());
        }
        assertThat(types).containsExactlyInAnyOrder(
                "DUPLICATE_NODE_ID", "INVALID_COORD", "INVALID_NODE",
                "BUILDING_NOT_FOUND", "LABEL_NOT_FOUND", "BUILDING_AMBIGUOUS", "BUILDING_NOT_FOUND",
                "SELF_LOOP", "EDGE_UNKNOWN_NODE", "DUPLICATE_EDGE", "INVALID_EDGE");
        JsonNode label = issue(errors, "LABEL_NOT_FOUND");
        assertThat(label.get("candidates").get(0).asString()).isEqualTo("A_1F");   // 대소문자만 다른 후보가 먼저
        assertThat(issue(errors, "BUILDING_AMBIGUOUS").get("candidates")).hasSize(2);
        assertThat(issue(errors, "DUPLICATE_EDGE").get("target").asString()).isEqualTo("edges[3]");

        mockMvc.perform(post(IMPORT).param("dryRun", "false").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.errors.length()").value(11));
        assertThat(nodeRepository.findAll()).extracting(PathNode::getCode).containsExactly("old1");   // 그대로
    }

    @Test
    void format_이_1_이_아니면_읽지_않는다() throws Exception {
        mockMvc.perform(post(IMPORT).param("dryRun", "false").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"format\":2,\"nodes\":[],\"edges\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].type").value("FORMAT"));
        mockMvc.perform(post(IMPORT).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nodes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("format 이 필요해요."));
    }

    @Test
    void 빈_경로망을_적용하면_전부_지워지고_paths_는_빈_모양이다() throws Exception {
        nodeRepository.save(PathNode.waypoint("old1", new BigDecimal("37.5500000"), new BigDecimal("126.9250000")));
        mockMvc.perform(post(IMPORT).param("dryRun", "false").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"format\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(true));
        assertThat(nodeRepository.count()).isZero();
        assertThat(jsonMapper.writeValueAsString(mapData().get("paths"))).isEqualTo("{\"nodes\":[],\"edges\":[]}");
    }

    private static JsonNode ref(JsonNode refs, String ref) {
        for (JsonNode r : refs) {
            if (r.get("ref").asString().equals(ref)) {
                return r;
            }
        }
        throw new AssertionError("참조 없음: " + ref);
    }

    private static JsonNode issue(JsonNode errors, String type) {
        for (JsonNode e : errors) {
            if (e.get("type").asString().equals(type)) {
                return e;
            }
        }
        throw new AssertionError("오류 없음: " + type);
    }

    private JsonNode mapData() throws Exception {
        MvcResult result = mockMvc.perform(get("/map/data")).andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String etag() throws Exception {
        return mockMvc.perform(get("/map/data")).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId());
    }
}
