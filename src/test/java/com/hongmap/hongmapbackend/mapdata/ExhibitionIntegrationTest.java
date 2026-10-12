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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장소별 전시 일정: GET /map/data 의 exhibitions(오늘 KST 기준 필터·정렬·KST 자정 캐시 교체)와
 * 관리자 /admin/map/exhibitions(CRUD·권한·검증·캐시 무효화).
 * 날짜 기준을 고정하려고 Clock 을 바꿔 끼운다. H2 를 다른 테스트와 같이 쓰므로 이 테스트가 만든 장소의 전시만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExhibitionIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 기준 "오늘"(KST). */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    /** 테스트가 움직이는 UTC Clock. */
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());

        void setKst(LocalDateTime kst) {
            instant.set(kst.atZone(KST).toInstant());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @Autowired MutableClock clock;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired CampusFacilityRepository facilityRepository;
    @Autowired ExhibitionRepository exhibitionRepository;
    @Autowired MapDataService mapDataService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JsonMapper jsonMapper;

    User admin;
    User normal;
    Building building;
    String venue;      // kind 행사·전시
    String venue2;     // kind 행사·전시 (정렬 확인용)
    String printer;    // kind 프린터 — 전시를 걸 수 없다
    String missing;    // campus_facilities 에 없는 code
    String prefix;

    @BeforeEach
    void setUp() {
        clock.setKst(TODAY.atTime(12, 0));
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        normal = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        prefix = "test-ex-" + suffix;
        building = buildingRepository.save(Building.builder()
                .name("전시관-" + suffix).displayName("전시관 MH동-" + suffix).code("test_ex_" + suffix)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .sortOrder(-1000).build());
        venue = prefix + "-b-gallery";
        venue2 = prefix + "-a-gallery";
        printer = prefix + "-printer";
        missing = prefix + "-missing";
        facilityRepository.save(CampusFacility.builder().code(venue).kind("행사·전시").building(building).floor(4)
                .note("미술관 1관").sortOrder(-1000).build());
        facilityRepository.save(CampusFacility.builder().code(venue2).kind("행사·전시").building(building).floor(2)
                .note("미술관 2관").sortOrder(-999).build());
        facilityRepository.save(CampusFacility.builder().code(printer).kind("프린터").building(building).floor(1)
                .sortOrder(-998).build());
        mapDataService.invalidate();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM exhibitions WHERE facility_code LIKE 'test-ex-%'");
        facilityRepository.findAll().stream()
                .filter(f -> f.getCode().startsWith("test-ex-"))
                .forEach(facilityRepository::delete);
        buildingRepository.delete(building);
        mapDataService.invalidate();
    }

    @Test
    void 지도_데이터는_진행_중과_60일_안_예정_전시만_장소_시작일_순으로_준다() throws Exception {
        Exhibition current = save(venue, "진행 중(오늘 끝)", TODAY.minusDays(2), TODAY, "평일 10:00~18:00",
                "설명", "안내", "https://homa.hongik.ac.kr");
        Exhibition horizon = save(venue, "60일째 시작", TODAY.plusDays(60), TODAY.plusDays(65), null, null, null, null);
        Exhibition soon = save(venue, "곧 시작", TODAY.plusDays(5), TODAY.plusDays(9), null, " ", null, null);
        Exhibition otherVenue = save(venue2, "다른 장소", TODAY, TODAY, null, null, null, null);
        save(venue, "지난 전시(어제 끝)", TODAY.minusDays(10), TODAY.minusDays(1), null, null, null, null);
        save(venue, "61일째 시작", TODAY.plusDays(61), TODAY.plusDays(70), null, null, null, null);
        save(missing, "없는 장소", TODAY, TODAY.plusDays(3), null, null, null, null);
        mapDataService.invalidate();

        MvcResult result = mockMvc.perform(get("/map/data")).andExpect(status().isOk()).andReturn();
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.properties().stream().map(e -> e.getKey()).toList())
                .containsExactly("version", "buildings", "facilities", "partners", "exhibitions", "paths");

        List<JsonNode> mine = mine(body);
        // venue2(...-a-gallery) 가 venue(...-b-gallery) 보다 앞(facilityId 순), 같은 장소는 시작일 순.
        assertThat(mine.stream().map(n -> n.get("id").asLong()).toList())
                .containsExactly(otherVenue.getId(), current.getId(), soon.getId(), horizon.getId());

        JsonNode c = mine.get(1);
        assertThat(c.get("facilityId").asString()).isEqualTo(venue);
        assertThat(c.get("title").asString()).isEqualTo("진행 중(오늘 끝)");
        assertThat(c.get("startsOn").asString()).isEqualTo("2026-10-04");
        assertThat(c.get("endsOn").asString()).isEqualTo("2026-10-06");
        assertThat(c.get("hours").asString()).isEqualTo("평일 10:00~18:00");
        assertThat(c.get("description").asString()).isEqualTo("설명");
        assertThat(c.get("link").get("label").asString()).isEqualTo("안내");
        assertThat(c.get("link").get("url").asString()).isEqualTo("https://homa.hongik.ac.kr");
        // null 필드는 생략, link 는 url 이 있을 때만
        JsonNode h = mine.get(3);
        assertThat(h.properties().stream().map(e -> e.getKey()).toList())
                .containsExactly("id", "facilityId", "title", "startsOn", "endsOn");
    }

    @Test
    void 전시가_없으면_빈_배열이다() throws Exception {
        mockMvc.perform(get("/map/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions").isArray());
    }

    @Test
    void 캐시는_KST_자정이_지나면_다시_만든다() throws Exception {
        Exhibition endsToday = save(venue, "오늘 끝", TODAY.minusDays(1), TODAY, null, null, null, null);
        mapDataService.invalidate();

        clock.setKst(TODAY.atTime(23, 59, 30));
        String etag = mockMvc.perform(get("/map/data")).andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + endsToday.getId() + ")]").isNotEmpty())
                .andReturn().getResponse().getHeader("ETag");

        // 1분 뒤 = KST 다음 날 00:00:30 (UTC 로는 아직 같은 날 15:00). 5분 TTL 안이지만 날짜가 바뀌었으니 다시 만든다.
        clock.setKst(TODAY.plusDays(1).atTime(0, 0, 30));
        MvcResult next = mockMvc.perform(get("/map/data").header("If-None-Match", etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + endsToday.getId() + ")]").isEmpty())
                .andReturn();
        assertThat(next.getResponse().getHeader("ETag")).isNotEqualTo(etag);
    }

    @Test
    void 관리자_전시_추가_목록_수정_삭제() throws Exception {
        String auth = bearer(admin);
        Exhibition past = save(venue, "지난 전시", TODAY.minusDays(30), TODAY.minusDays(25), null, null, null, null);

        MvcResult created = mockMvc.perform(post("/admin/map/exhibitions").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(" " + venue + " ", " 새 전시 ", "2026-10-12", "2026-10-16",
                                "평일 10:00~18:00 (토·일 휴관)", "설명이에요.", "{\"label\":\"공식 안내\",\"url\":\"https://homa.hongik.ac.kr/x\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.facilityId").value(venue))
                .andExpect(jsonPath("$.title").value("새 전시"))
                .andExpect(jsonPath("$.startsOn").value("2026-10-12"))
                .andExpect(jsonPath("$.endsOn").value("2026-10-16"))
                .andExpect(jsonPath("$.hours").value("평일 10:00~18:00 (토·일 휴관)"))
                .andExpect(jsonPath("$.description").value("설명이에요."))
                .andExpect(jsonPath("$.link.label").value("공식 안내"))
                .andExpect(jsonPath("$.link.url").value("https://homa.hongik.ac.kr/x"))
                .andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        // 목록: 지난 전시 포함, 시작일 최근 순
        MvcResult list = mockMvc.perform(get("/admin/map/exhibitions").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : jsonMapper.readTree(list.getResponse().getContentAsByteArray()).get("exhibitions")) {
            if (n.get("facilityId").asString().startsWith(prefix)) {
                ids.add(n.get("id").asLong());
            }
        }
        assertThat(ids).containsExactly(id, past.getId());

        // 수정: 통째로 바꾼다 — 빈 칸·빠진 link 는 지워진다, 시작일 = 종료일 허용
        mockMvc.perform(put("/admin/map/exhibitions/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venue2, "바뀐 전시", "2026-10-20", "2026-10-20", "  ", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.facilityId").value(venue2))
                .andExpect(jsonPath("$.title").value("바뀐 전시"))
                .andExpect(jsonPath("$.endsOn").value("2026-10-20"))
                .andExpect(jsonPath("$.hours").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.link").doesNotExist());
        Exhibition updated = exhibitionRepository.findById(id).orElseThrow();
        assertThat(updated.getLinkUrl()).isNull();
        assertThat(updated.getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 20));

        mockMvc.perform(put("/admin/map/exhibitions/999999999").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venue, "없음", "2026-10-20", "2026-10-20", null, null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(delete("/admin/map/exhibitions/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(exhibitionRepository.existsById(id)).isFalse();
        mockMvc.perform(delete("/admin/map/exhibitions/" + id).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void 관리자_전시_API는_비로그인_401_일반사용자_403() throws Exception {
        Exhibition e = save(venue, "권한 확인", TODAY, TODAY, null, null, null, null);
        String body = json(venue, "x", "2026-10-06", "2026-10-06", null, null, null);

        mockMvc.perform(get("/admin/map/exhibitions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/map/exhibitions").header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/map/exhibitions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin/map/exhibitions").header("Authorization", bearer(normal))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/map/exhibitions/" + e.getId()).header("Authorization", bearer(normal))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/map/exhibitions/" + e.getId()).header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/map/exhibitions/" + e.getId())).andExpect(status().isUnauthorized());

        Exhibition unchanged = exhibitionRepository.findById(e.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("권한 확인");
        assertThat(exhibitionRepository.findAll().stream().filter(x -> x.getFacilityCode().startsWith(prefix)).count())
                .isEqualTo(1);
    }

    @Test
    void 잘못된_전시_입력은_400() throws Exception {
        String auth = bearer(admin);
        String okLink = "{\"label\":\"안내\",\"url\":\"https://example.com\"}";
        String[] bad = {
                json(missing, "제목", "2026-10-06", "2026-10-07", null, null, null),              // 없는 장소
                json(printer, "제목", "2026-10-06", "2026-10-07", null, null, null),              // 행사·전시 아님
                json("", "제목", "2026-10-06", "2026-10-07", null, null, null),                   // 장소 빈칸
                json(venue, " ", "2026-10-06", "2026-10-07", null, null, null),                   // 제목 빈칸
                json(venue, "가".repeat(151), "2026-10-06", "2026-10-07", null, null, null),      // 제목 길이
                json(venue, "제목", "2026-10-08", "2026-10-07", null, null, null),                // 시작 > 종료
                json(venue, "제목", "2026/10/06", "2026-10-07", null, null, null),                // 날짜 형식
                json(venue, "제목", "2026-02-30", "2026-03-01", null, null, null),                // 없는 날짜
                json(venue, "제목", "2026-10-06", null, null, null, null),                        // 종료일 없음
                json(venue, "제목", "2026-10-06", "2026-10-07", "가".repeat(101), null, null),    // 관람 시간 길이
                json(venue, "제목", "2026-10-06", "2026-10-07", null, "가".repeat(1001), null),   // 설명 길이
                json(venue, "제목", "2026-10-06", "2026-10-07", null, null,
                        "{\"label\":\"안내\",\"url\":\"http://example.com\"}"),                     // https 아님
                json(venue, "제목", "2026-10-06", "2026-10-07", null, null,
                        "{\"label\":\"안내\",\"url\":\"javascript:alert(1)\"}"),
                json(venue, "제목", "2026-10-06", "2026-10-07", null, null,
                        "{\"label\":\"\",\"url\":\"https://example.com\"}"),                        // 링크 이름 빈칸
        };
        for (String body : bad) {
            mockMvc.perform(post("/admin/map/exhibitions").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isString());
        }
        // 경계값은 통과: 제목 150자, 설명 1000자, 관람 시간 100자
        mockMvc.perform(post("/admin/map/exhibitions").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venue, "가".repeat(150), "2026-10-06", "2026-10-06", "가".repeat(100),
                                "가".repeat(1000), okLink)))
                .andExpect(status().isCreated());

        Exhibition e = save(venue, "수정 검증", TODAY, TODAY, null, null, null, null);
        mockMvc.perform(put("/admin/map/exhibitions/" + e.getId()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(printer, "제목", "2026-10-06", "2026-10-07", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
        assertThat(exhibitionRepository.findById(e.getId()).orElseThrow().getFacilityCode()).isEqualTo(venue);
    }

    @Test
    void 전시_쓰기는_지도_데이터_캐시를_바로_비운다() throws Exception {
        String auth = bearer(admin);
        Exhibition e = save(venue, "처음 제목", TODAY, TODAY.plusDays(3), null, null, null, null);
        mapDataService.invalidate();
        String etag1 = mockMvc.perform(get("/map/data")).andReturn().getResponse().getHeader("ETag");

        // DB 를 직접 바꾸면(관리자 API 아님) 캐시 시간 동안은 그대로 — 캐시가 실제로 쓰인다는 확인.
        jdbcTemplate.update("UPDATE exhibitions SET title = ? WHERE id = ?", "직접 바꾼 제목", e.getId());
        mockMvc.perform(get("/map/data").header("If-None-Match", etag1)).andExpect(status().isNotModified());

        // 추가 → 바로 반영
        MvcResult created = mockMvc.perform(post("/admin/map/exhibitions").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venue, "새 전시", "2026-10-07", "2026-10-08", null, null, null)))
                .andExpect(status().isCreated()).andReturn();
        long newId = jsonMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
        MvcResult afterCreate = mockMvc.perform(get("/map/data").header("If-None-Match", etag1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + newId + ")].title").value("새 전시"))
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + e.getId() + ")].title").value("직접 바꾼 제목"))
                .andReturn();
        String etag2 = afterCreate.getResponse().getHeader("ETag");
        assertThat(etag2).isNotEqualTo(etag1);

        // 수정 → 바로 반영
        mockMvc.perform(put("/admin/map/exhibitions/" + newId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venue, "고친 전시", "2026-10-07", "2026-10-08", null, null, null)))
                .andExpect(status().isOk());
        String etag3 = mockMvc.perform(get("/map/data").header("If-None-Match", etag2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + newId + ")].title").value("고친 전시"))
                .andReturn().getResponse().getHeader("ETag");

        // 삭제 → 바로 반영
        mockMvc.perform(delete("/admin/map/exhibitions/" + newId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/map/data").header("If-None-Match", etag3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhibitions[?(@.id == " + newId + ")]").isEmpty());
    }

    private Exhibition save(String facilityCode, String title, LocalDate startsOn, LocalDate endsOn, String hours,
                            String description, String linkLabel, String linkUrl) {
        return exhibitionRepository.save(Exhibition.builder()
                .facilityCode(facilityCode).title(title).startsOn(startsOn).endsOn(endsOn)
                .hours(hours).description(description == null || description.isBlank() ? null : description)
                .linkLabel(linkLabel).linkUrl(linkUrl).build());
    }

    private List<JsonNode> mine(JsonNode body) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode n : body.get("exhibitions")) {
            if (n.get("facilityId").asString().startsWith(prefix)) {
                result.add(n);
            }
        }
        return result;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private String json(String facilityId, String title, String startsOn, String endsOn, String hours,
                        String description, String link) {
        var node = jsonMapper.createObjectNode();
        node.put("facilityId", facilityId);
        node.put("title", title);
        if (startsOn != null) {
            node.put("startsOn", startsOn);
        }
        if (endsOn != null) {
            node.put("endsOn", endsOn);
        }
        if (hours != null) {
            node.put("hours", hours);
        }
        if (description != null) {
            node.put("description", description);
        }
        if (link != null) {
            node.set("link", jsonMapper.readTree(link));
        }
        return jsonMapper.writeValueAsString(node);
    }
}
