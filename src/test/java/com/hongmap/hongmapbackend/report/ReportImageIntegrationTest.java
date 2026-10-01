package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.image.ReportImageStorage;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 사진: 업로드 URL 발급 → (S3 PUT) → imageKey 로 등록 → 목록/관리자 화면에 보기 URL → 삭제·반려 시 S3 객체 삭제.
 * S3 는 메모리 가짜 저장소로 대신한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportImageIntegrationTest {

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        FakeStorage fakeReportImageStorage() {
            return new FakeStorage();
        }
    }

    /** "업로드됐다" 를 putObject 로 흉내 낸다. */
    static class FakeStorage implements ReportImageStorage {
        final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        final List<String> deleted = new ArrayList<>();
        volatile boolean enabled = true;

        void putObject(String key, long size, String contentType) {
            objects.put(key, new StoredObject(size, contentType));
        }

        @Override public boolean isEnabled() { return enabled; }

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
            return new PresignedUpload("https://bucket.s3.test/" + key + "?X-Amz-Signature=put",
                    Map.of("content-type", contentType), Instant.now().plus(ttl));
        }

        @Override public Optional<StoredObject> head(String key) { return Optional.ofNullable(objects.get(key)); }

        @Override public String presignView(String key, Duration ttl) { return "https://bucket.s3.test/" + key + "?X-Amz-Signature=get"; }

        @Override
        public synchronized void delete(String key) {
            objects.remove(key);
            deleted.add(key);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired FakeStorage storage;

    User author;
    User admin;
    Building building;

    @BeforeEach
    void setUp() {
        storage.enabled = true;
        storage.objects.clear();
        storage.deleted.clear();
        author = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("사진러").build());
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자2").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("사진관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    /** 다른 테스트 클래스가 같은 H2 DB 를 보므로(지도 목록 개수 단언) 만든 제보를 남기지 않는다. */
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM reports WHERE building_id = ?", building.getId());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private String issueKey(String contentType) throws Exception {
        String body = mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"" + contentType + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.headers.content-type").value(contentType))
                .andExpect(jsonPath("$.maxBytes").value(5 * 1024 * 1024))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        assertThat(key).matches("reports/[0-9a-f-]{36}\\.(jpg|png)");
        assertThat((String) JsonPath.read(body, "$.uploadUrl")).contains(key);
        return key;
    }

    private String createBody(String imageKey) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        return """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"EVENT","title":"사진 제보",
                 "startsAt":"%s","endsAt":"%s"%s}
                """.formatted(building.getId(), now.minusMinutes(1), now.plusHours(2),
                imageKey == null ? "" : ",\"imageKey\":\"" + imageKey + "\"");
    }

    @Test
    void 업로드_URL은_로그인이_필요하고_JPEG_PNG만_받는다() throws Exception {
        mockMvc.perform(post("/reports/images").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/gif\"}"))
                .andExpect(status().isBadRequest());
        assertThat(issueKey("image/jpeg")).endsWith(".jpg");
        assertThat(issueKey("image/png")).endsWith(".png");
    }

    @Test
    void 저장소가_꺼져_있으면_503() throws Exception {
        storage.enabled = false;
        mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isServiceUnavailable());
        // 사진 없는 제보는 그대로 된다.
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void 올린_사진을_붙여_등록하면_목록과_관리자화면에_보기URL이_나온다() throws Exception {
        String key = issueKey("image/jpeg");
        storage.putObject(key, 300_000, "image/jpeg");

        String body = mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://bucket.s3.test/" + key + "?X-Amz-Signature=get"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(reportRepository.findById(id).orElseThrow().getImageKey()).isEqualTo(key);

        mockMvc.perform(get("/admin/reports").param("status", "PENDING").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.reports[?(@.id == " + id + ")].imageUrl").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString(key))));

        mockMvc.perform(patch("/admin/reports/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.containsString(key)));

        mockMvc.perform(get("/reports").param("live", "true").param("buildingId", String.valueOf(building.getId())))
                .andExpect(jsonPath("$.reports[0].id").value(id))
                .andExpect(jsonPath("$.reports[0].imageUrl").value(org.hamcrest.Matchers.containsString(key)));

        // 같은 사진을 다른 제보에 또 붙일 수는 없다.
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(key)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 서버가_발급한_형식이_아니거나_업로드되지_않았거나_너무_크면_400() throws Exception {
        for (String bad : List.of("other/abc.jpg", "reports/../secret.jpg", "reports/" + UUID.randomUUID() + ".gif")) {
            mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                            .contentType(MediaType.APPLICATION_JSON).content(createBody(bad)))
                    .andExpect(status().isBadRequest());
        }

        String notUploaded = issueKey("image/jpeg");
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(notUploaded)))
                .andExpect(status().isBadRequest());

        String tooBig = issueKey("image/jpeg");
        storage.putObject(tooBig, 6L * 1024 * 1024, "image/jpeg");
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(tooBig)))
                .andExpect(status().isBadRequest());
        assertThat(storage.deleted).contains(tooBig);

        String wrongType = issueKey("image/jpeg");
        storage.putObject(wrongType, 1000, "text/html");
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(wrongType)))
                .andExpect(status().isBadRequest());
        assertThat(storage.deleted).contains(wrongType);
    }

    @Test
    void 본인_삭제와_관리자_반려는_S3_사진도_지운다() throws Exception {
        String keyA = issueKey("image/jpeg");
        storage.putObject(keyA, 1000, "image/jpeg");
        long idA = ((Number) JsonPath.read(mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(keyA)))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(delete("/reports/" + idA).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(storage.deleted).contains(keyA);

        String keyB = issueKey("image/png");
        storage.putObject(keyB, 1000, "image/png");
        long idB = ((Number) JsonPath.read(mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(keyB)))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(patch("/admin/reports/" + idB).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"note\":\"사진 부적절\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(storage.deleted).contains(keyB);
        assertThat(reportRepository.findById(idB).orElseThrow().getImageKey()).isNull();
    }
}
