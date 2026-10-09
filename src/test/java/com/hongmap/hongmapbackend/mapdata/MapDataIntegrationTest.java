package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.partner.entity.Partner;
import com.hongmap.hongmapbackend.partner.repository.PartnerRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /map/data(모양·ETag·304·캐시 무효화)와 관리자 지도 편집(/admin/map/**: 권한·검증·코드 생성).
 * H2 인메모리 DB — 다른 테스트 클래스와 DB 를 같이 쓰므로 이 테스트가 만든 행만 골라 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MapDataIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired PartnerRepository partnerRepository;
    @Autowired CampusFacilityRepository facilityRepository;
    @Autowired MapDataService mapDataService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JsonMapper jsonMapper;

    User admin;
    User normal;
    Building building;
    String buildingCode;
    String partnerCode;
    String facilityCode;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        normal = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buildingCode = "test_b_" + suffix;
        building = buildingRepository.save(Building.builder()
                .name("지도관-" + suffix)
                .displayName("지도관 R동-" + suffix)
                .code(buildingCode)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .color("#2563EB").mapCategory("강의").type("강의·행정 복합동")
                .floors(15).basementFloors(4)
                .facilities("[\"엘리베이터\",\"프린터\"]")
                .linkLabel("홈페이지").linkUrl("https://www.hongik.ac.kr")
                .boundary("[[37.55,126.92],[37.551,126.921]]")
                .extraBoundaries("[[[37.552,126.922],[37.553,126.923]]]")
                .entrances("[{\"label\":\"정문\",\"lat\":37.55,\"lng\":126.92,\"minFloor\":1,\"maxFloor\":3}]")
                .sortOrder(-1000)
                .build());

        partnerCode = "test-p-" + suffix;
        Partner partner = Partner.builder()
                .code(partnerCode).sortOrder(-1000)
                .name("테스트카페").category("카페")
                .latitude(new BigDecimal("37.5458493")).longitude(new BigDecimal("126.9224087"))
                .benefit("전 메뉴 10% 할인").address("서울 마포구 와우산로 1")
                .roadAddress("도로명 주소는 안 나간다")
                .build();
        partner.addAffiliation("총학생회", null);
        partner.addAffiliation("경제학부", "음료 1잔 무료");
        partnerRepository.save(partner);

        facilityCode = "test-f-" + suffix;
        facilityRepository.save(CampusFacility.builder()
                .code(facilityCode).kind("프린터").building(building).floor(2).note("복도 끝")
                .sortOrder(-1000).build());

        // 저장소로 직접 넣었으니(관리자 API 아님) 캐시를 비운다.
        mapDataService.invalidate();
    }

    @AfterEach
    void tearDown() {
        facilityRepository.findAll().stream()
                .filter(f -> f.getCode().startsWith("test-f-") || f.getCode().startsWith("f-")
                        || f.getCode().startsWith("adm-f-"))
                .forEach(facilityRepository::delete);
        partnerRepository.findAll().stream()
                .filter(p -> p.getCode() != null && (p.getCode().startsWith("test-p-") || p.getCode().startsWith("p-")
                        || p.getCode().startsWith("adm-p-")))
                .forEach(partnerRepository::delete);
        buildingRepository.delete(building);
        mapDataService.invalidate();
    }

    @Test
    void 지도_데이터는_비로그인으로_받고_앱_타입_모양이다() throws Exception {
        String b = "$.buildings[?(@.code == '" + buildingCode + "')]";
        String p = "$.partners[?(@.id == '" + partnerCode + "')]";
        String f = "$.facilities[?(@.id == '" + facilityCode + "')]";
        MvcResult result = mockMvc.perform(get("/map/data").header("Origin", "https://hongikon.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=300"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://hongikon.com"))
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath(b + ".name").value("지도관 R동-" + building.getCode().substring(7)))
                .andExpect(jsonPath(b + ".id").value(building.getId().intValue()))
                .andExpect(jsonPath(b + ".lat").value(37.55))
                .andExpect(jsonPath(b + ".category").value("강의"))
                .andExpect(jsonPath(b + ".facilities[1]").value("프린터"))
                .andExpect(jsonPath(b + ".link.url").value("https://www.hongik.ac.kr"))
                .andExpect(jsonPath(b + ".boundary[0][1]").value(126.92))
                .andExpect(jsonPath(b + ".extraBoundaries[0][1][0]").value(37.553))
                .andExpect(jsonPath(b + ".entrances[0].maxFloor").value(3))
                .andExpect(jsonPath(b + ".hours").doesNotExist())
                .andExpect(jsonPath(f + ".kind").value("프린터"))
                .andExpect(jsonPath(f + ".buildingName").value("지도관 R동-" + building.getCode().substring(7)))
                .andExpect(jsonPath(f + ".floor").value(2))
                .andExpect(jsonPath(f + ".lat").doesNotExist())
                .andExpect(jsonPath(p + ".name").value("테스트카페"))
                .andExpect(jsonPath(p + ".affiliations[0]").value("총학생회"))
                .andExpect(jsonPath(p + ".affiliations[1]").value("경제학부"))
                .andExpect(jsonPath(p + ".lng").value(126.9224087))
                .andExpect(jsonPath(p + ".affiliationBenefits.length()").value(1))
                .andExpect(jsonPath(p + ".affiliationBenefits[0].affiliation").value("경제학부"))
                .andExpect(jsonPath(p + ".affiliationBenefits[0].benefit").value("음료 1잔 무료"))
                .andExpect(jsonPath(p + ".roadAddress").doesNotExist())
                .andExpect(jsonPath(p + ".mapIcon").doesNotExist())
                .andExpect(jsonPath(p + ".link").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getHeader("Access-Control-Expose-Headers")).contains("ETag");
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsByteArray());
        String version = body.get("version").asString();
        assertThat(version).matches("^[0-9a-f]{16}$");
        assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"" + version + "\"");
        // 앞에서 보는 순서: version, buildings, facilities, partners, exhibitions, paths(맨 끝, 항상). sort_order 가 가장 작으니 이 테스트 행이 맨 앞.
        assertThat(body.properties().stream().map(e -> e.getKey()).toList())
                .containsExactly("version", "buildings", "facilities", "partners", "exhibitions", "paths");
        assertThat(body.get("exhibitions").isArray()).isTrue();
        assertThat(body.get("paths").get("nodes").isArray()).isTrue();
        assertThat(body.get("paths").get("edges").isArray()).isTrue();
        assertThat(body.get("buildings").get(0).get("code").asString()).isEqualTo(buildingCode);

        // version = 본문(version 제외)의 SHA-256 앞 16 hex
        ObjectNode withoutVersion = (ObjectNode) body.deepCopy();
        withoutVersion.remove("version");
        assertThat(MapDataService.version(jsonMapper.writeValueAsBytes(withoutVersion))).isEqualTo(version);
    }

    @Test
    void If_None_Match가_같으면_304() throws Exception {
        String etag = mockMvc.perform(get("/map/data")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        MvcResult notModified = mockMvc.perform(get("/map/data").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "public, max-age=300"))
                .andReturn();
        assertThat(notModified.getResponse().getContentAsByteArray()).isEmpty();

        mockMvc.perform(get("/map/data").header("If-None-Match", "\"0000000000000000\", W/" + etag))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/map/data").header("If-None-Match", "\"0000000000000000\""))
                .andExpect(status().isOk());
    }

    @Test
    void 캐시는_관리자_쓰기에_바로_무효화된다() throws Exception {
        String etag1 = mockMvc.perform(get("/map/data")).andReturn().getResponse().getHeader("ETag");

        // DB 를 직접 바꾸면(관리자 API 아님) 캐시 시간 동안은 그대로 — 캐시가 실제로 쓰인다는 확인.
        jdbcTemplate.update("UPDATE partners SET name = ? WHERE code = ?", "직접 바꾼 이름", partnerCode);
        mockMvc.perform(get("/map/data").header("If-None-Match", etag1)).andExpect(status().isNotModified());

        // 관리자 수정 → 바로 새 버전
        mockMvc.perform(put("/admin/map/partners/" + partnerCode).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson("", "관리자가 바꾼 이름", "카페", "[\"총학생회\"]", "37.5458493", "126.9224087", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partnerCode))
                .andExpect(jsonPath("$.name").value("관리자가 바꾼 이름"));

        MvcResult after = mockMvc.perform(get("/map/data").header("If-None-Match", etag1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')].name").value("관리자가 바꾼 이름"))
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')].affiliations.length()").value(1))
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')].affiliationBenefits").doesNotExist())
                .andReturn();
        assertThat(after.getResponse().getHeader("ETag")).isNotEqualTo(etag1);

        // 편의시설 삭제도 바로 반영
        mockMvc.perform(delete("/admin/map/facilities/" + facilityCode).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/map/data"))
                .andExpect(jsonPath("$.facilities[?(@.id == '" + facilityCode + "')]").isEmpty());
    }

    @Test
    void 관리자_지도_API는_비로그인_401_일반사용자_403() throws Exception {
        mockMvc.perform(get("/admin/map/partners")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/map/partners").header("Authorization", bearer(normal))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/map/partners").header("Authorization", bearer(normal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson("", "새 업체", "카페", "[]", "37.55", "126.92", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/map/facilities/" + facilityCode).header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/map/facilities/" + facilityCode))
                .andExpect(status().isUnauthorized());
        assertThat(facilityRepository.existsByCode(facilityCode)).isTrue();

        mockMvc.perform(get("/admin/map/partners").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')].name").value("테스트카페"));
        mockMvc.perform(get("/admin/map/facilities").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilities[?(@.id == '" + facilityCode + "')].buildingCode").value(buildingCode));
        mockMvc.perform(get("/admin/map/buildings").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildings[?(@.code == '" + buildingCode + "')].name")
                        .value("지도관 R동-" + buildingCode.substring(7)));
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        String auth = bearer(admin);
        String[] badPartners = {
                partnerJson("", "업체", "술집", "[]", "37.55", "126.92", null),                    // 분류
                partnerJson("", "업체", "카페", "[\"없는소속\"]", "37.55", "126.92", null),          // 소속
                partnerJson("", "업체", "카페", "[\"총학생회\",\"총학생회\"]", "37.55", "126.92", null), // 소속 중복
                partnerJson("", "업체", "카페", "[]", "40.1", "126.92", null),                     // 위도
                partnerJson("", "업체", "카페", "[]", "37.55", "120", null),                       // 경도
                partnerJson("", "업체", "카페", "[]", "37.55", "126.92", "http://example.com"),   // https 아님
                partnerJson("", "업체", "카페", "[]", "37.55", "126.92", "javascript:alert(1)"),
                partnerJson("", "가".repeat(101), "카페", "[]", "37.55", "126.92", null),          // 이름 길이
                partnerJson("", " ", "카페", "[]", "37.55", "126.92", null),                       // 이름 빈칸
                partnerJson("bad id!", "업체", "카페", "[]", "37.55", "126.92", null),             // id 형식
                "{\"name\":\"업체\",\"category\":\"카페\",\"lat\":37.55,\"lng\":126.92,\"mapIcon\":\"약국\"}",
                "{\"name\":\"업체\",\"category\":\"카페\",\"lat\":37.55,\"lng\":126.92,\"affiliations\":[\"총학생회\"],"
                        + "\"affiliationBenefits\":[{\"affiliation\":\"경제학부\",\"benefit\":\"x\"}]}",
        };
        for (String body : badPartners) {
            mockMvc.perform(post("/admin/map/partners").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }
        String[] badFacilities = {
                "{\"kind\":\"자판기\",\"buildingCode\":\"" + buildingCode + "\"}",
                "{\"kind\":\"프린터\",\"buildingCode\":\"no_such_building\"}",
                "{\"kind\":\"프린터\",\"buildingCode\":\"" + buildingCode + "\",\"lat\":37.55}",
                "{\"kind\":\"프린터\",\"buildingCode\":\"" + buildingCode + "\",\"floor\":500}",
                "{\"kind\":\"프린터\",\"buildingCode\":\"" + buildingCode + "\",\"note\":\"" + "가".repeat(256) + "\"}",
        };
        for (String body : badFacilities) {
            mockMvc.perform(post("/admin/map/facilities").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }
        mockMvc.perform(put("/admin/map/partners/no-such-code").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson("", "업체", "카페", "[]", "37.55", "126.92", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 제휴업체_삭제는_업체_이름을_정확히_넣어야_한다() throws Exception {
        String auth = bearer(admin);
        mockMvc.perform(delete("/admin/map/partners/" + partnerCode).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/admin/map/partners/" + partnerCode).header("Authorization", auth)
                        .param("confirmName", "테스트 카페"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/map/partners").header("Authorization", auth))
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')]").isNotEmpty());
        mockMvc.perform(delete("/admin/map/partners/" + partnerCode).header("Authorization", auth)
                        .param("confirmName", "  테스트카페 "))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/admin/map/partners").header("Authorization", auth))
                .andExpect(jsonPath("$.partners[?(@.id == '" + partnerCode + "')]").isEmpty());
    }

    @Test
    void id를_비우면_코드를_만들고_중복_id는_409() throws Exception {
        String auth = bearer(admin);
        mockMvc.perform(post("/admin/map/partners").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 병원\",\"category\":\"의료/미용\",\"mapIcon\":\"병원\",\"lat\":37.55123456789,"
                                + "\"lng\":126.92,\"benefit\":\"진료비 10%\",\"affiliations\":[\"총학생회\",\"기숙사\"],"
                                + "\"affiliationBenefits\":[{\"affiliation\":\"기숙사\",\"benefit\":\"진료비 20%\"},"
                                + "{\"affiliation\":\"총학생회\",\"benefit\":\"진료비 10%\"}],"
                                + "\"link\":{\"label\":\"예약\",\"url\":\"https://example.com/book\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("^p-[a-z0-9]{8}$")))
                .andExpect(jsonPath("$.mapIcon").value("병원"))
                .andExpect(jsonPath("$.lat").value(37.5512346))
                // 기본 혜택과 같은 소속 혜택은 예외가 아니다 → 기숙사만 남는다.
                .andExpect(jsonPath("$.affiliationBenefits.length()").value(1))
                .andExpect(jsonPath("$.affiliationBenefits[0].affiliation").value("기숙사"))
                .andExpect(jsonPath("$.link.label").value("예약"));

        mockMvc.perform(post("/admin/map/partners").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson(partnerCode, "같은 id", "카페", "[]", "37.55", "126.92", null)))
                .andExpect(status().isConflict());

        String customCode = "adm-p-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/admin/map/partners").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson(customCode, "직접 id", "카페", "[]", "37.55", "126.92", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(customCode));

        mockMvc.perform(post("/admin/map/facilities").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"\",\"kind\":\"정수기\",\"buildingCode\":\"" + buildingCode
                                + "\",\"floor\":-1,\"lat\":37.5501,\"lng\":126.9251}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("^f-[a-z0-9]{8}$")))
                .andExpect(jsonPath("$.buildingCode").value(buildingCode))
                .andExpect(jsonPath("$.buildingName").value("지도관 R동-" + buildingCode.substring(7)))
                .andExpect(jsonPath("$.floor").value(-1))
                .andExpect(jsonPath("$.lat").value(37.5501));

        mockMvc.perform(post("/admin/map/facilities").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + facilityCode + "\",\"kind\":\"정수기\",\"buildingCode\":\"" + buildingCode + "\"}"))
                .andExpect(status().isConflict());

        // 수정: 소속을 겹치게 바꿔도(기존 경제학부 유지 + 기숙사 추가, 총학생회 제거) UNIQUE 충돌 없이 바뀐다.
        mockMvc.perform(put("/admin/map/partners/" + partnerCode).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + partnerCode + "\",\"name\":\"테스트카페\",\"category\":\"카페\",\"lat\":37.55,\"lng\":126.92,"
                                + "\"benefit\":\"전 메뉴 10% 할인\",\"affiliations\":[\"경제학부\",\"기숙사\"],"
                                + "\"affiliationBenefits\":[{\"affiliation\":\"기숙사\",\"benefit\":\"쿠키 증정\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliations.length()").value(2))
                .andExpect(jsonPath("$.affiliations[0]").value("경제학부"))
                .andExpect(jsonPath("$.affiliations[1]").value("기숙사"))
                .andExpect(jsonPath("$.affiliationBenefits.length()").value(1))
                .andExpect(jsonPath("$.affiliationBenefits[0].benefit").value("쿠키 증정"));
    }

    @Test
    void If_None_Match_파싱() {
        assertThat(MapDataController.matches(null, "abc")).isFalse();
        assertThat(MapDataController.matches("\"abc\"", "abc")).isTrue();
        assertThat(MapDataController.matches("W/\"abc\"", "abc")).isTrue();
        assertThat(MapDataController.matches("\"x\", \"abc\"", "abc")).isTrue();
        assertThat(MapDataController.matches("*", "abc")).isTrue();
        assertThat(MapDataController.matches("\"abcd\"", "abc")).isFalse();
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private static String partnerJson(String id, String name, String category, String affiliations,
                                      String lat, String lng, String linkUrl) {
        String link = linkUrl == null ? "" : ",\"link\":{\"label\":\"링크\",\"url\":\"" + linkUrl + "\"}";
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"affiliations\":"
                + affiliations + ",\"lat\":" + lat + ",\"lng\":" + lng + link + "}";
    }
}
