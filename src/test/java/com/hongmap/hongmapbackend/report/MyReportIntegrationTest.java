package com.hongmap.hongmapbackend.report;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 제보 내역: 본인 것만, 최신순, 화면용 상태, 사유 노출 범위, 페이지, 개수, 삭제 권한. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyReportIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User me;
    User other;
    Building building;
    LocalDateTime now;
    int seq;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("내제보").build());
        other = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("남의제보").build());
        building = buildingRepository.save(Building.builder()
                .name("내역관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        now = LocalDateTime.now();
        seq = 0;
    }

    /** 다른 테스트 클래스가 같은 H2 DB 를 보므로 만든 제보를 남기지 않는다. */
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM reports WHERE building_id = ?", building.getId());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    /** 만든 순서대로 created_at 을 1분씩 늘려, 나중에 만든 것이 더 최신이 되게 한다. */
    private Report save(User author, String title, ReportStatus status, String note,
                        LocalDateTime startsAt, LocalDateTime endsAt) {
        Report report = reportRepository.save(Report.builder()
                .user(author).building(building).floor(3)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.EVENT).title(title)
                .startsAt(startsAt).endsAt(endsAt)
                .status(status).moderationNote(note)
                .reviewedAt(status == ReportStatus.PENDING ? null : now)
                .build());
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?",
                now.minusDays(1).plusMinutes(++seq), report.getId());
        return report;
    }

    private Report saveLive(User author, String title, ReportStatus status, String note) {
        return save(author, title, status, note, now.minusHours(1), now.plusHours(2));
    }

    @Test
    void 본인_제보만_최신순으로_화면용_상태와_함께() throws Exception {
        saveLive(me, "대기", ReportStatus.PENDING, null);
        saveLive(me, "표시중", ReportStatus.ACTIVE, "관리자 내부 메모");
        save(me, "예정", ReportStatus.ACTIVE, null, now.plusHours(3), now.plusHours(5));
        save(me, "종료", ReportStatus.ACTIVE, null, now.minusHours(5), now.minusHours(1));
        save(me, "대기중종료", ReportStatus.PENDING, null, now.minusHours(5), now.minusHours(1));
        saveLive(me, "반려", ReportStatus.REJECTED, "장소가 정확하지 않아요");
        saveLive(me, "숨김", ReportStatus.HIDDEN, null);
        saveLive(me, "삭제", ReportStatus.DELETED, "삭제 메모");
        saveLive(other, "남의 것", ReportStatus.ACTIVE, null);

        mockMvc.perform(get("/users/me/reports").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content", hasSize(8)))
                .andExpect(jsonPath("$.content[0].title").value("삭제"))
                .andExpect(jsonPath("$.content[0].status").value("DELETED"))
                .andExpect(jsonPath("$.content[0].displayStatus").value("DELETED"))
                .andExpect(jsonPath("$.content[0].moderationNote").value(nullValue()))
                .andExpect(jsonPath("$.content[1].displayStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.content[2].title").value("반려"))
                .andExpect(jsonPath("$.content[2].displayStatus").value("REJECTED"))
                .andExpect(jsonPath("$.content[2].moderationNote").value("장소가 정확하지 않아요"))
                .andExpect(jsonPath("$.content[2].reviewedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[3].title").value("대기중종료"))
                .andExpect(jsonPath("$.content[3].status").value("PENDING"))
                .andExpect(jsonPath("$.content[3].displayStatus").value("ENDED"))
                .andExpect(jsonPath("$.content[4].displayStatus").value("ENDED"))
                .andExpect(jsonPath("$.content[5].displayStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[6].title").value("표시중"))
                .andExpect(jsonPath("$.content[6].displayStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[6].moderationNote").value(nullValue()))
                .andExpect(jsonPath("$.content[6].buildingName").value(building.getName()))
                .andExpect(jsonPath("$.content[6].floor").value(3))
                .andExpect(jsonPath("$.content[6].category").value("EVENT"))
                .andExpect(jsonPath("$.content[6].imageUrls", hasSize(0)))
                .andExpect(jsonPath("$.content[7].displayStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[7].reviewedAt").value(nullValue()))
                // 작성자·신고자 정보는 내려가지 않는다
                .andExpect(jsonPath("$.content[0].authorNickname").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].flags").doesNotExist());

        mockMvc.perform(get("/users/me/reports").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("남의 것"));
    }

    @Test
    void 페이지로_나눠_읽는다() throws Exception {
        saveLive(me, "1", ReportStatus.PENDING, null);
        saveLive(me, "2", ReportStatus.PENDING, null);
        saveLive(me, "3", ReportStatus.PENDING, null);

        mockMvc.perform(get("/users/me/reports").param("page", "0").param("size", "2")
                        .header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("3"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/users/me/reports").param("page", "1").param("size", "2")
                        .header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("1"))
                .andExpect(jsonPath("$.hasNext").value(false));

        // 상한(50)을 넘는 size 는 50 으로 맞춘다
        mockMvc.perform(get("/users/me/reports").param("size", "500").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void 개수는_전체와_승인_대기() throws Exception {
        saveLive(me, "대기1", ReportStatus.PENDING, null);
        saveLive(me, "대기2", ReportStatus.PENDING, null);
        saveLive(me, "반려", ReportStatus.REJECTED, "사유");
        saveLive(other, "남의 대기", ReportStatus.PENDING, null);

        mockMvc.perform(get("/users/me/reports/count").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pending").value(2));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get("/users/me/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me/reports/count")).andExpect(status().isUnauthorized());
    }

    @Test
    void 신고로_숨겨진_제보는_검토_전에는_지울_수_없다() throws Exception {
        Report hidden = saveLive(me, "숨겨진 내 것", ReportStatus.HIDDEN, null);

        mockMvc.perform(delete("/reports/{id}", hidden.getId()).header("Authorization", bearer(me)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/users/me/reports").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 내역에서_본인_제보만_지울_수_있다() throws Exception {
        Report mine = saveLive(me, "반려된 내 것", ReportStatus.REJECTED, "사유");
        Report others = saveLive(other, "남의 것", ReportStatus.REJECTED, "사유");

        mockMvc.perform(delete("/reports/{id}", others.getId()).header("Authorization", bearer(me)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/reports/{id}", mine.getId()).header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me/reports").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/users/me/reports").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
