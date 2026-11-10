package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.common.devkey.DevKeyGuard;
import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 경로 조사 도구용 경로망 버전: 암호키, 올리기(검사·비교), 길 시험, 적용하면 운영 길찾기가 바로 그 경로망, 백업 다시 받기. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteGraphIntegrationTest {

    private static final String KEY = "test-ask-dev-key";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired WalkGraphVersionRepository repository;
    @Autowired WalkRouteService walkRouteService;
    @MockitoBean MapDataService mapDataService;

    private long r;
    private long t;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(getClass().getClassLoader().getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("buildings", fixture.get("buildings"));
        when(mapDataService.current()).thenReturn(new MapDataService.Snapshot("v1", MAPPER.writeValueAsBytes(payload)));
        for (JsonNode b : fixture.get("buildings")) {
            String name = b.get("name").asString();
            if (name.endsWith("R동")) {
                r = b.get("id").asLong();
            } else if (name.endsWith("T동")) {
                t = b.get("id").asLong();
            }
        }
    }

    private static String bundledGraph() throws Exception {
        try (InputStream in = RouteGraphIntegrationTest.class.getClassLoader().getResourceAsStream(SurveyGraph.RESOURCE)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** 저장소 경로망에서 R동 출입구로 이어지는 간선을 모두 뺀 초안(R동이 길망에서 떨어진다). */
    private static String draftWithoutR() throws Exception {
        ObjectNode graph = (ObjectNode) MAPPER.readTree(bundledGraph());
        var edges = MAPPER.createArrayNode();
        for (JsonNode e : graph.get("edges")) {
            if (!e.get(0).asString().contains("hongik_r") && !e.get(1).asString().contains("hongik_r")) {
                edges.add(e);
            }
        }
        graph.set("edges", edges);
        return MAPPER.writeValueAsString(graph);
    }

    @Test
    void 키가_없거나_틀리면_403() throws Exception {
        mockMvc.perform(get("/map/route-graph/versions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/map/route-graph/versions").header(DevKeyGuard.HEADER, "wrong")).andExpect(status().isForbidden());
    }

    @Test
    void 모양이_틀린_경로망이나_v3_가_아닌_백업은_400() throws Exception {
        mockMvc.perform(post("/map/route-graph/versions").header(DevKeyGuard.HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"graph\":{\"vertices\":[1,2]}}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/map/route-graph/versions").header(DevKeyGuard.HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"graph\":" + bundledGraph() + ",\"backup\":{\"format\":\"x\"}}")).andExpect(status().isBadRequest());
    }

    @Test
    void 올리면_초안으로_검사_비교하고_적용하면_운영_길찾기가_바로_바뀐다() throws Exception {
        String backup = "{\"format\":\"hongikon.pathSurvey\",\"version\":3,\"nodes\":[],\"edges\":[]}";
        String body = mockMvc.perform(post("/map/route-graph/versions").header(DevKeyGuard.HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceName\":\"R동 끊긴 초안\",\"graph\":" + draftWithoutR() + ",\"backup\":" + backup + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.report.comparison.against").value("file"))
                .andReturn().getResponse().getContentAsString();
        JsonNode view = MAPPER.readTree(body);
        long id = view.get("id").asLong();
        assertThat(view.at("/report/comparison/pairs").asInt()).isGreaterThan(600);
        // R동이 끊겨 R동에서·으로 가는 길이 직선 어림 또는 연결로 바뀐 경우가 비교표에 잡힌다.
        assertThat(view.at("/report/comparison/biggest").toString()).contains("R동");

        // 길 시험: 초안과 운영을 나란히.
        mockMvc.perform(get("/map/route-graph/versions/" + id + "/routes").header(DevKeyGuard.HEADER, KEY)
                        .param("from", String.valueOf(r)).param("to", String.valueOf(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active[0].isEstimate").value(false))
                .andExpect(jsonPath("$.version[0]").exists());

        // 운영 길찾기는 아직 저장소 경로망.
        WalkRoute before = walkRouteService.routes(r, null, t, null).get(0);

        mockMvc.perform(post("/map/route-graph/versions/" + id + "/activate").header(DevKeyGuard.HEADER, KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/map/route-graph/versions").header(DevKeyGuard.HEADER, KEY))
                .andExpect(jsonPath("$.activeSource").value("v" + id));
        WalkRoute after = walkRouteService.routes(r, null, t, null).get(0);
        assertThat(after.steps()).isNotEqualTo(before.steps());

        // 백업 원본을 다시 받는다(다른 컴퓨터의 조사 도구로 불러오기).
        mockMvc.perform(get("/map/route-graph/versions/" + id + "/backup").header(DevKeyGuard.HEADER, KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.format").value("hongikon.pathSurvey"));

        // 저장소 경로망을 새 버전으로 올려 적용하면 되돌아온다(이전 ACTIVE 는 ARCHIVED).
        String back = mockMvc.perform(post("/map/route-graph/versions").header(DevKeyGuard.HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"graph\":" + bundledGraph() + "}")).andReturn().getResponse().getContentAsString();
        long backId = MAPPER.readTree(back).get("id").asLong();
        mockMvc.perform(post("/map/route-graph/versions/" + backId + "/activate").header(DevKeyGuard.HEADER, KEY)).andExpect(status().isOk());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(WalkGraphVersion.Status.ARCHIVED);
        assertThat(walkRouteService.routes(r, null, t, null).get(0).steps()).isEqualTo(before.steps());
    }
}
