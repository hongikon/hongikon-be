package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 닉네임 설정 API 와, 공개 제보 응답에 로그인 닉네임 원문이 실리지 않는지. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppNicknameIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User author;
    User other;
    Building building;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("홍길동").build());
        other = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("김철수").build());
        building = buildingRepository.save(Building.builder()
                .name("닉네임관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private String uniqueNickname(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private void putNickname(User user, String json, int expectedStatus) throws Exception {
        mockMvc.perform(put("/users/me/nickname").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void meShowsMaskedNameUntilAppNicknameIsSet() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNickname").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("홍**"))
                .andExpect(jsonPath("$.maskedDefaultName").value("홍**"))
                .andExpect(content().string(not(containsString("홍길동"))));

        String nickname = uniqueNickname("홍익");
        mockMvc.perform(put("/users/me/nickname").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"  " + nickname + " \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNickname").value(nickname))
                .andExpect(jsonPath("$.displayName").value(nickname));

        mockMvc.perform(delete("/users/me/nickname").header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appNickname").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("홍**"));
    }

    @Test
    void emptyNicknameClears() throws Exception {
        putNickname(author, "{\"nickname\":\"" + uniqueNickname("ab") + "\"}", 200);
        putNickname(author, "{\"nickname\":\"\"}", 200);
        assertThat(userRepository.findById(author.getId()).orElseThrow().getAppNickname()).isNull();
    }

    @Test
    void rejectsInvalidReservedAndDuplicateNicknames() throws Exception {
        mockMvc.perform(put("/users/me/nickname").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"홍익온운영\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("쓸 수 없어요")));
        putNickname(author, "{\"nickname\":\"a\"}", 400);
        putNickname(author, "{\"nickname\":\"hi there\"}", 400);

        String taken = uniqueNickname("Taken");
        putNickname(other, "{\"nickname\":\"" + taken + "\"}", 200);
        mockMvc.perform(put("/users/me/nickname").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + taken.toLowerCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 다른 사람이 쓰고 있는 닉네임이에요."));

        // 본인 것은 대소문자만 바꿔도 된다
        putNickname(other, "{\"nickname\":\"" + taken.toUpperCase() + "\"}", 200);
    }

    @Test
    void limitsChangesPerDay() throws Exception {
        for (int i = 0; i < AppNicknameChangeLimiter.MAX_CHANGES; i++) {
            putNickname(author, "{\"nickname\":\"" + uniqueNickname("n") + "\"}", 200);
        }
        putNickname(author, "{\"nickname\":\"" + uniqueNickname("n") + "\"}", 429);
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/users/me/nickname").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicReportsNeverExposeProviderNickname() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Report report = reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
        jdbcTemplate.update("UPDATE reports SET status = ? WHERE id = ?", ReportStatus.ACTIVE.name(), report.getId());

        String path = "/reports?live=true&buildingId=" + building.getId();
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].authorNickname").value("홍**"))
                .andExpect(jsonPath("$.reports[0].authorDisplayName").value("홍**"))
                .andExpect(content().string(not(containsString("홍길동"))));

        String nickname = uniqueNickname("빵");
        putNickname(author, "{\"nickname\":\"" + nickname + "\"}", 200);
        mockMvc.perform(get(path).header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.reports[0].authorDisplayName").value(nickname))
                .andExpect(jsonPath("$.reports[0].authorNickname").value(nickname));
    }

    @Test
    void createResponseUsesDisplayName() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"FOOD_TRUCK",
                 "title":"붕어빵","startsAt":"%s","endsAt":"%s"}
                """.formatted(building.getId(), now.minusMinutes(5).withNano(0), now.plusHours(2).withNano(0));
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorDisplayName").value("홍**"))
                .andExpect(content().string(not(containsString("홍길동"))));
    }
}
