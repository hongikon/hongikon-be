package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.image.ImageMetadataSanitizerTest;
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
        final Map<String, byte[]> bodies = new ConcurrentHashMap<>();
        final List<String> deleted = java.util.Collections.synchronizedList(new ArrayList<>());
        final Map<String, Long> signedLengths = new ConcurrentHashMap<>();
        volatile boolean enabled = true;

        /** 크기만 있는 객체(본문 없음) — 크기·형식 검사가 본문 읽기 전에 거르는지 볼 때. */
        void putObject(String key, long size, String contentType) {
            objects.put(key, new StoredObject(size, contentType));
        }

        void putObject(String key, byte[] body, String contentType) {
            objects.put(key, new StoredObject(body.length, contentType));
            bodies.put(key, body);
        }

        @Override public boolean isEnabled() { return enabled; }

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Long contentLength, Duration ttl) {
            if (contentLength != null) {
                signedLengths.put(key, contentLength);
            }
            return new PresignedUpload("https://bucket.s3.test/" + key + "?X-Amz-Signature=put",
                    Map.of("content-type", contentType), Instant.now().plus(ttl));
        }

        @Override public Optional<StoredObject> head(String key) { return Optional.ofNullable(objects.get(key)); }

        @Override
        public Optional<byte[]> get(String key, long maxBytes) {
            byte[] body = bodies.get(key);
            return body == null ? Optional.empty()
                    : Optional.of(java.util.Arrays.copyOf(body, (int) Math.min(body.length, maxBytes + 1)));
        }

        @Override
        public void put(String key, String contentType, byte[] bytes) {
            putObject(key, bytes, contentType);
        }

        @Override public String presignView(String key, Duration ttl) { return "https://bucket.s3.test/" + key + "?X-Amz-Signature=get"; }

        @Override
        public synchronized void delete(String key) {
            objects.remove(key);
            bodies.remove(key);
            deleted.add(key);
        }

        @Override
        public synchronized void copy(String sourceKey, String destinationKey) {
            StoredObject source = objects.get(sourceKey);
            if (source == null) {
                throw new IllegalStateException("no such key");
            }
            objects.put(destinationKey, source);
            if (bodies.containsKey(sourceKey)) {
                bodies.put(destinationKey, bodies.get(sourceKey));
            }
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
        storage.bodies.clear();
        storage.deleted.clear();
        storage.signedLengths.clear();
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

    /** 제보에 붙은 사진 키(sort_order 순). 지연 로딩을 피하려고 DB 에서 바로 읽는다. */
    private List<String> imageKeys(long reportId) {
        return jdbcTemplate.queryForList(
                "SELECT image_key FROM report_images WHERE report_id = ? ORDER BY sort_order", String.class, reportId);
    }

    /** 새 앱 형식: imageKeys 배열(+ 선택으로 구버전 서버 대비 imageKey). */
    private String createBodyWithKeys(List<String> imageKeys, String legacyImageKey) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String keysJson = imageKeys.stream().map(k -> "\"" + k + "\"").collect(java.util.stream.Collectors.joining(","));
        return """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"EVENT","title":"사진 여러 장",
                 "startsAt":"%s","endsAt":"%s","imageKeys":[%s]%s}
                """.formatted(building.getId(), now.minusMinutes(1), now.plusHours(2), keysJson,
                legacyImageKey == null ? "" : ",\"imageKey\":\"" + legacyImageKey + "\"");
    }

    private String uploadedJpeg() throws Exception {
        String key = issueKey("image/jpeg");
        storage.putObject(key, jpegWithGps(), "image/jpeg");
        return key;
    }

    private String uploadedPng() throws Exception {
        String key = issueKey("image/png");
        storage.putObject(key, ImageMetadataSanitizerTest.realPng(), "image/png");
        return key;
    }

    private long createWithKeys(List<String> keys) throws Exception {
        String body = mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(keys, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long reportCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE building_id = ?", Long.class, building.getId());
    }

    /** 구버전 앱 형식: imageKey 1장. */
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
        String uploadedKey = issueKey("image/jpeg");
        storage.putObject(uploadedKey, jpegWithGps(), "image/jpeg");

        String body = mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uploadedKey)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        // 서버가 메타데이터를 지운 사본을 새 키로 저장하고, 앱이 올린 원래 키는 커밋 뒤 지운다.
        String key = imageKeys(id).get(0);
        assertThat(key).matches("reports/[0-9a-f-]{36}\\.jpg").isNotEqualTo(uploadedKey);
        assertThat((String) JsonPath.read(body, "$.imageUrl"))
                .isEqualTo("https://bucket.s3.test/" + key + "?X-Amz-Signature=get");
        assertThat(storage.deleted).contains(uploadedKey).doesNotContain(key);
        assertThat(new String(storage.bodies.get(key), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("SECRETCAM").startsWith("\u00ff\u00d8\u00ff");

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

        // 같은 사진을 다른 제보에 또 붙일 수는 없다(서버 키든 원래 키든).
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(key)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uploadedKey)))
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

        // content-type 은 image/jpeg 인데 본문이 JPEG 가 아니면(매직 바이트 불일치) 원본을 지우고 400.
        String fakeJpeg = issueKey("image/jpeg");
        storage.putObject(fakeJpeg, "<html><script>alert(1)</script></html>".getBytes(), "image/jpeg");
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(fakeJpeg)))
                .andExpect(status().isBadRequest());
        assertThat(storage.deleted).contains(fakeJpeg);

        String pngAsJpeg = issueKey("image/jpeg");
        storage.putObject(pngAsJpeg, ImageMetadataSanitizerTest.realPng(), "image/jpeg");
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(pngAsJpeg)))
                .andExpect(status().isBadRequest());
        assertThat(storage.deleted).contains(pngAsJpeg);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE building_id = ?", Long.class, building.getId())).isZero();
    }

    @Test
    void contentLength를_보내면_서명에_넣고_5MB를_넘으면_400() throws Exception {
        mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":123456}"))
                .andExpect(status().isCreated());
        assertThat(storage.signedLengths).containsValue(123456L);

        mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":" + (5L * 1024 * 1024 + 1) + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/reports/images").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원탈퇴하면_제보_사진도_S3에서_지운다() throws Exception {
        String uploaded = issueKey("image/png");
        storage.putObject(uploaded, ImageMetadataSanitizerTest.realPng(), "image/png");
        long id = ((Number) JsonPath.read(mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uploaded)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        String key = imageKeys(id).get(0);
        assertThat(storage.objects).containsKey(key);

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());

        assertThat(reportRepository.findById(id)).isEmpty();
        assertThat(storage.deleted).contains(key);
        assertThat(storage.objects).doesNotContainKey(key);
    }

    private static byte[] jpegWithGps() throws java.io.IOException {
        return ImageMetadataSanitizerTest.insertAfterApp0(ImageMetadataSanitizerTest.realJpeg(),
                ImageMetadataSanitizerTest.exifApp1WithGps(6));
    }

    @Test
    void 본인_삭제와_관리자_반려는_S3_사진도_지운다() throws Exception {
        String uploadedA = issueKey("image/jpeg");
        storage.putObject(uploadedA, ImageMetadataSanitizerTest.realJpeg(), "image/jpeg");
        long idA = ((Number) JsonPath.read(mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uploadedA)))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        String keyA = imageKeys(idA).get(0);
        mockMvc.perform(delete("/reports/" + idA).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(storage.deleted).contains(keyA);

        String uploadedB = issueKey("image/png");
        storage.putObject(uploadedB, ImageMetadataSanitizerTest.realPng(), "image/png");
        long idB = ((Number) JsonPath.read(mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(uploadedB)))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        String keyB = imageKeys(idB).get(0);
        mockMvc.perform(patch("/admin/reports/" + idB).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"note\":\"사진 부적절\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(storage.deleted).contains(keyB);
        assertThat(imageKeys(idB)).isEmpty();
    }

    @Test
    void 사진_3장을_순서대로_붙이면_imageUrls_순서가_같고_imageUrl은_첫장() throws Exception {
        List<String> uploaded = List.of(uploadedJpeg(), uploadedPng(), uploadedJpeg());

        String body = mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(uploaded, uploaded.get(0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        List<String> keys = imageKeys(id);
        assertThat(keys).hasSize(3).doesNotContainAnyElementsOf(uploaded);
        assertThat(keys.get(0)).endsWith(".jpg");
        assertThat(keys.get(1)).endsWith(".png");
        assertThat(keys.get(2)).endsWith(".jpg");
        List<String> urls = JsonPath.read(body, "$.imageUrls");
        assertThat(urls).containsExactly(keys.stream()
                .map(k -> "https://bucket.s3.test/" + k + "?X-Amz-Signature=get").toArray(String[]::new));
        assertThat((String) JsonPath.read(body, "$.imageUrl")).isEqualTo(urls.get(0));
        // 앱이 올린 원래 키는 모두 지우고, 정리본에는 메타데이터가 없다.
        assertThat(storage.deleted).containsAll(uploaded);
        for (String k : keys) {
            assertThat(new String(storage.bodies.get(k), java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("SECRETCAM");
        }

        mockMvc.perform(get("/admin/reports").param("status", "PENDING").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.reports[?(@.id == " + id + ")].imageUrls[2]")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(keys.get(2)))));
        mockMvc.perform(patch("/admin/reports/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(jsonPath("$.imageUrls.length()").value(3));
        mockMvc.perform(get("/reports").param("live", "true").param("buildingId", String.valueOf(building.getId())))
                .andExpect(jsonPath("$.reports[0].imageUrls.length()").value(3))
                .andExpect(jsonPath("$.reports[0].imageUrls[1]").value(org.hamcrest.Matchers.containsString(keys.get(1))))
                .andExpect(jsonPath("$.reports[0].imageUrl").value(org.hamcrest.Matchers.containsString(keys.get(0))));
    }

    @Test
    void 사진이_없으면_imageUrls는_빈_배열() throws Exception {
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls").isArray())
                .andExpect(jsonPath("$.imageUrls.length()").value(0))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void 사진은_3장까지이고_같은_키를_두번_붙일_수_없다_S3작업_없이_400() throws Exception {
        List<String> four = List.of(uploadedJpeg(), uploadedJpeg(), uploadedJpeg(), uploadedJpeg());
        int objectsBefore = storage.objects.size();
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(four, null)))
                .andExpect(status().isBadRequest());
        String a = four.get(0);
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(List.of(a, a), null)))
                .andExpect(status().isBadRequest());
        // 두 번째 키 형식이 틀리면 첫 장도 건드리지 않는다.
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(List.of(a, "other/x.jpg"), null)))
                .andExpect(status().isBadRequest());
        assertThat(storage.objects).hasSize(objectsBefore);
        assertThat(storage.deleted).isEmpty();
        assertThat(reportCount()).isZero();
    }

    @Test
    void 중간_사진이_실패하면_롤백되고_앞_사진의_원래_키로_다시_시도할_수_있다() throws Exception {
        String good = uploadedJpeg();
        String fake = issueKey("image/jpeg");
        storage.putObject(fake, "<html></html>".getBytes(), "image/jpeg");

        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(List.of(good, fake), null)))
                .andExpect(status().isBadRequest());
        assertThat(reportCount()).isZero();
        // 앞 사진의 정리본은 롤백으로 지워지고 원래 키는 남는다. 위장 파일은 지운다.
        assertThat(storage.objects).containsOnlyKeys(good);
        assertThat(storage.deleted).contains(fake).doesNotContain(good);

        String retry = uploadedPng();
        long id = createWithKeys(List.of(good, retry));
        assertThat(imageKeys(id)).hasSize(2);
        assertThat(storage.deleted).contains(good, retry);
    }

    @Test
    void imageKeys가_있으면_구버전_imageKey는_무시한다() throws Exception {
        String first = uploadedJpeg();
        String second = uploadedPng();
        String legacyOnly = uploadedJpeg();
        String body = mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(createBodyWithKeys(List.of(first, second), legacyOnly)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(imageKeys(id)).hasSize(2);
        assertThat(storage.objects).containsKey(legacyOnly); // 붙지 않은 키는 수명 주기 규칙이 정리
    }

    @Test
    void 여러장_제보의_본인삭제_관리자반려_탈퇴는_사진을_모두_지운다() throws Exception {
        long idA = createWithKeys(List.of(uploadedJpeg(), uploadedPng(), uploadedJpeg()));
        List<String> keysA = imageKeys(idA);
        mockMvc.perform(delete("/reports/" + idA).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(storage.deleted).containsAll(keysA);
        assertThat(imageKeys(idA)).isEmpty();

        long idB = createWithKeys(List.of(uploadedPng(), uploadedJpeg()));
        List<String> keysB = imageKeys(idB);
        mockMvc.perform(patch("/admin/reports/" + idB).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls.length()").value(0))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(storage.deleted).containsAll(keysB);
        assertThat(imageKeys(idB)).isEmpty();

        long idC = createWithKeys(List.of(uploadedJpeg(), uploadedJpeg()));
        long idD = createWithKeys(List.of(uploadedPng()));
        List<String> keysCD = new ArrayList<>(imageKeys(idC));
        keysCD.addAll(imageKeys(idD));
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(storage.deleted).containsAll(keysCD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_images WHERE report_id IN (?, ?)", Long.class, idC, idD)).isZero();
    }
}
