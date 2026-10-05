package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예정 제보: 시작·종료 시각 검증(POST /reports)과 지도 목록의 live / include=upcoming 구분(GET /reports).
 * H2 인메모리 DB, 서버 시각은 UTC(HongmapBackendApplication).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportScheduleIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    User author;
    Building building;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        building = buildingRepository.save(Building.builder()
                .name("예정관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    // ---------- 등록 검증 ----------

    @Test
    void 내일_시작하는_예정_제보를_올릴_수_있다() throws Exception {
        Instant starts = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
        create(starts, starts.plus(Duration.ofHours(4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.startsAt").value(org.hamcrest.Matchers.startsWith(
                        LocalDateTime.ofInstant(starts, java.time.ZoneOffset.UTC).toString().substring(0, 16))));
    }

    @Test
    void 지금_시작은_몇_분_어긋나도_받는다() throws Exception {
        Instant starts = Instant.now().minus(Duration.ofMinutes(3));
        create(starts, starts.plus(Duration.ofHours(2))).andExpect(status().isCreated());
    }

    @Test
    void 이미_지난_시작_시각은_400() throws Exception {
        Instant starts = Instant.now().minus(Duration.ofMinutes(30));
        create(starts, starts.plus(Duration.ofHours(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("시작 시각이 이미 지났어요. 지금 또는 이후 시각을 골라 주세요."));
    }

    @Test
    void 시작_시각은_14일_안이어야_한다() throws Exception {
        Instant ok = Instant.now().plus(Duration.ofDays(13));
        create(ok, ok.plus(Duration.ofHours(1))).andExpect(status().isCreated());

        Instant tooFar = Instant.now().plus(Duration.ofDays(15));
        create(tooFar, tooFar.plus(Duration.ofHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("시작 시각은 오늘부터 14일 안으로 골라 주세요."));
    }

    @Test
    void 진행_기간은_시작보다_뒤이고_7일_이내() throws Exception {
        Instant starts = Instant.now().plus(Duration.ofHours(3));
        // 여러 날 행사(예: 3일짜리 축제 부스)도 된다.
        create(starts, starts.plus(Duration.ofDays(3))).andExpect(status().isCreated());
        create(starts, starts.plus(Duration.ofDays(7))).andExpect(status().isCreated());

        create(starts, starts.plus(Duration.ofDays(7)).plusSeconds(60))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("진행 기간은 최대 7일까지 정할 수 있어요."));
        create(starts, starts)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("종료 시각은 시작 시각보다 뒤여야 해요."));
    }

    @Test
    void 종료_시각이_지났으면_400() throws Exception {
        Instant starts = Instant.now().minus(Duration.ofMinutes(5));
        create(starts, Instant.now().minus(Duration.ofMinutes(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("종료 시각이 이미 지났어요. 시간을 다시 골라 주세요."));
    }

    // ---------- 지도 목록 ----------

    @Test
    void 시작_전_제보는_기본_목록에_없고_include_upcoming_이면_48시간_안의_것만_붙는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Report live = active(now.minusHours(1), now.plusHours(2), "진행 중");
        Report soon = active(now.plusHours(20), now.plusHours(22), "내일 아침");
        Report sooner = active(now.plusHours(2), now.plusHours(4), "오늘 오후");
        Report dayAfter = active(now.plusHours(30), now.plusHours(32), "모레 아침");
        Report later = active(now.plusHours(50), now.plusHours(52), "글피");
        Report pendingSoon = reportRepository.save(builder(now.plusHours(1), now.plusHours(3), "검토 전").build());
        // 어제 시작해 모레 끝나는 여러 날 제보는 진행 중이다.
        Report multiDay = active(now.minusDays(1), now.plusDays(2), "3일 축제 부스");

        assertThat(ids("/reports?buildingId=" + building.getId())).containsExactlyInAnyOrder(live.getId(), multiDay.getId());
        // live 다음에 예정이 시작 시각 순으로 붙는다. 승인 전(PENDING)·48시간 밖은 빠진다.
        List<Long> withUpcoming = ids("/reports?buildingId=" + building.getId() + "&include=upcoming");
        assertThat(withUpcoming.subList(0, 2)).containsExactlyInAnyOrder(live.getId(), multiDay.getId());
        assertThat(withUpcoming.subList(2, withUpcoming.size())).containsExactly(sooner.getId(), soon.getId(), dayAfter.getId());
        assertThat(withUpcoming).doesNotContain(later.getId(), pendingSoon.getId());
        // 알 수 없는 include 값은 무시(기본 목록).
        assertThat(ids("/reports?buildingId=" + building.getId() + "&include=all"))
                .containsExactlyInAnyOrder(live.getId(), multiDay.getId());
    }

    @Test
    void 지도_목록은_작성자를_제보마다_따로_읽지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 3; i++) {
            User other = userRepository.save(User.builder()
                    .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("작성자" + i).build());
            reportRepository.save(builder(now.minusHours(1), now.plusHours(2), "진행 " + i)
                    .user(other).status(ReportStatus.ACTIVE).build());
            reportRepository.save(builder(now.plusHours(2 + i), now.plusHours(5 + i), "예정 " + i)
                    .user(other).status(ReportStatus.ACTIVE).build());
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            assertThat(ids("/reports?buildingId=" + building.getId() + "&include=upcoming")).hasSize(6);
            // 진행 중 1번 + 예정 1번 + 사진 묶음 1번 + 댓글 수 1번·공감 통계 1번(건수와 무관).
            // 작성자(닉네임) 지연 로딩이 제보·작성자 수만큼 늘지 않아야 한다.
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    // ---------- 픽스처 ----------

    private org.springframework.test.web.servlet.ResultActions create(Instant startsAt, Instant endsAt) throws Exception {
        String body = """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"FOOD_TRUCK","title":"붕어빵 트럭",
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(building.getId(), startsAt.truncatedTo(ChronoUnit.MILLIS), endsAt.truncatedTo(ChronoUnit.MILLIS));
        return mockMvc.perform(post("/reports")
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(author.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Report.ReportBuilder builder(LocalDateTime startsAt, LocalDateTime endsAt, String title) {
        return Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title(title)
                .startsAt(startsAt).endsAt(endsAt);
    }

    private Report active(LocalDateTime startsAt, LocalDateTime endsAt, String title) {
        return reportRepository.save(builder(startsAt, endsAt, title).status(ReportStatus.ACTIVE).build());
    }

    private List<Long> ids(String url) throws Exception {
        String json = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> raw = JsonPath.read(json, "$.reports[*].id");
        return raw.stream().map(Number::longValue).toList();
    }
}
