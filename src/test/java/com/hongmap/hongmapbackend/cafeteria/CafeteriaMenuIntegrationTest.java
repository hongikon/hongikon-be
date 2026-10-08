package com.hongmap.hongmapbackend.cafeteria;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuFetchJob.FetchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가져오기(가짜 전송 — 네트워크 없음) → upsert → GET /cafeteria/menus(·/week).
 * cafeteria_menus 는 이 테스트만 쓰므로 매번 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CafeteriaMenuIntegrationTest {

    /** 2026-10-07 07:00:01 KST. */
    private static final Instant FETCHED = Instant.parse("2026-10-06T22:00:01.123456Z");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CafeteriaMenuRepository repository;
    @Autowired
    CafeteriaMenuService service;
    @Autowired
    CafeteriaMenuFetchJob springJob;
    @Autowired
    TaskScheduler taskScheduler;

    private final List<URI> requested = new ArrayList<>();
    private String body;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        requested.clear();
        body = CafeteriaMenuParserTest.fixture();
    }

    private CafeteriaMenuFetchJob job(boolean enabled) {
        CafeteriaMenuClient client = new CafeteriaMenuClient(uri -> {
            requested.add(uri);
            return body;
        });
        CafeteriaMenuService fixedClockService = new CafeteriaMenuService(repository, Clock.fixed(FETCHED, ZoneOffset.UTC));
        return new CafeteriaMenuFetchJob(client, fixedClockService, taskScheduler, enabled, 0);
    }

    @Test
    void testProfileDisablesFetching() {
        assertThat(springJob.runOnce("test")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void fetchStoresRowsAndRefetchIsIdempotent() {
        CafeteriaMenuFetchJob job = job(true);

        FetchResult first = job.runOnce("test").orElseThrow();
        assertThat(first.upsert().inserted()).isEqualTo(30);
        assertThat(first.upsert().updated()).isZero();
        assertThat(repository.count()).isEqualTo(30);

        FetchResult second = job.runOnce("test").orElseThrow();
        assertThat(second.upsert().inserted()).isZero();
        assertThat(second.upsert().updated()).isEqualTo(30);
        assertThat(repository.count()).isEqualTo(30);

        // 같은 키의 메뉴가 바뀌면 덮어쓴다(행은 늘지 않는다).
        body = body.replace("%EB%93%A4%EA%B9%A8%EB%AF%B8%EC%97%AD%EA%B5%AD", "%EC%86%8C%EA%B3%A0%EA%B8%B0%EB%AC%B4%EA%B5%AD");
        job.runOnce("test").orElseThrow();
        assertThat(repository.count()).isEqualTo(30);
        CafeteriaMenu lunch = repository.findByMenuDateBetween(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-07")).stream()
                .filter(m -> m.getRestaurantCode().equals("mh-staff") && m.getMeal().equals("점심"))
                .findFirst().orElseThrow();
        assertThat(lunch.getItems()).startsWith("소고기무국\n시래기영양밥").doesNotContain("들깨미역국");

        assertThat(requested).hasSize(3).containsOnly(CafeteriaMenuClient.SOURCE_URI);
    }

    @Test
    void badResultStoresNothing() {
        body = "{\"result\":\"N\"}";
        assertThat(job(true).runOnce("test")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void transportFailureIsSwallowed() {
        CafeteriaMenuClient failing = new CafeteriaMenuClient(uri -> {
            throw new java.net.http.HttpTimeoutException("timeout");
        });
        CafeteriaMenuFetchJob job = new CafeteriaMenuFetchJob(failing, service, taskScheduler, true, 0);
        assertThat(job.runOnce("test")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void startupCheckFetchesOnlyWhenWeekIsEmpty() {
        CafeteriaMenuFetchJob job = job(true);
        // 오늘(실제 날짜) 주에 메뉴가 없으면 가져온다.
        LocalDate monday = CafeteriaMenuService.monday(service.today());
        job.startupCheck();
        assertThat(requested).hasSize(1);

        // 이번 주 메뉴가 있으면 건너뛴다(오늘이 fixture 주가 아닐 수도 있어 한 행을 직접 넣는다).
        repository.save(new CafeteriaMenu("mh-staff", monday, "테스트", "밥", false, java.time.LocalDateTime.now()));
        job.startupCheck();
        assertThat(requested).hasSize(1);
    }

    @Test
    void concurrentRunIsSkipped() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CafeteriaMenuClient slow = new CafeteriaMenuClient(uri -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return body;
        });
        CafeteriaMenuFetchJob job = new CafeteriaMenuFetchJob(slow, service, taskScheduler, true, 0);
        Thread first = new Thread(() -> job.runOnce("first"));
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        Optional<FetchResult> second = job.runOnce("second");
        release.countDown();
        first.join(5000);

        assertThat(second).isEmpty();
        assertThat(repository.count()).isEqualTo(30);
    }

    @Test
    void dayResponseShape() throws Exception {
        job(true).runOnce("test");

        mockMvc.perform(get("/cafeteria/menus").param("date", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=600"))
                .andExpect(jsonPath("$.date").value("2026-10-07"))
                .andExpect(jsonPath("$.source").value("홍익대학교 홈페이지"))
                .andExpect(jsonPath("$.sourceUrl").value("https://www.hongik.ac.kr/kr/life/seoul-cafeteria.do"))
                .andExpect(jsonPath("$.fetchedAt").value("2026-10-07T07:00:01"))
                .andExpect(jsonPath("$.restaurants", hasSize(2)))
                .andExpect(jsonPath("$.restaurants[0].code").value("dorm2-student"))
                .andExpect(jsonPath("$.restaurants[0].facilityId").value("hi-dorm2-b2f-restaurant-01"))
                .andExpect(jsonPath("$.restaurants[0].name").value("학생식당"))
                .andExpect(jsonPath("$.restaurants[0].meals[*].meal", contains("아침", "점심A", "점심B", "저녁")))
                .andExpect(jsonPath("$.restaurants[0].meals[1].time").value("11:30~14:00"))
                .andExpect(jsonPath("$.restaurants[0].meals[1].price").value("5,800원 (일반 7,500원)"))
                .andExpect(jsonPath("$.restaurants[0].meals[1].items[3]").value("두부튀김스틱&강정"))
                .andExpect(jsonPath("$.restaurants[0].meals[1].items", hasSize(7)))
                .andExpect(jsonPath("$.restaurants[0].meals[1].closed").value(false))
                .andExpect(jsonPath("$.restaurants[0].meals[0].time").value("08:00~09:00"))
                .andExpect(jsonPath("$.restaurants[0].meals[3].time").value("17:30~18:50"))
                .andExpect(jsonPath("$.restaurants[1].code").value("mh-staff"))
                .andExpect(jsonPath("$.restaurants[1].facilityId").value("hi-mh-16f-restaurant"))
                .andExpect(jsonPath("$.restaurants[1].name").value("교직원식당"))
                .andExpect(jsonPath("$.restaurants[1].meals[*].meal", contains("점심", "저녁")))
                .andExpect(jsonPath("$.restaurants[1].meals[0].price").value("9,000원"))
                .andExpect(jsonPath("$.restaurants[1].meals[1].time").value("17:00~18:30"));

        // 휴일
        mockMvc.perform(get("/cafeteria/menus").param("date", "2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants[0].meals", hasSize(4)))
                .andExpect(jsonPath("$.restaurants[0].meals[0].closed").value(true))
                .andExpect(jsonPath("$.restaurants[0].meals[0].items", contains("한글날")))
                .andExpect(jsonPath("$.restaurants[1].meals[0].items", contains("한글날", "운영X")))
                .andExpect(jsonPath("$.restaurants[1].meals[1].closed").value(true));
    }

    @Test
    void dayWithoutRowsStillListsRestaurants() throws Exception {
        mockMvc.perform(get("/cafeteria/menus").param("date", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-10"))
                .andExpect(jsonPath("$.fetchedAt").value(nullValue()))
                .andExpect(jsonPath("$.restaurants[*].code", contains("dorm2-student", "mh-staff")))
                .andExpect(jsonPath("$.restaurants[0].meals", hasSize(0)))
                .andExpect(jsonPath("$.restaurants[1].meals", hasSize(0)));
    }

    @Test
    void defaultDateIsTodayKst() throws Exception {
        mockMvc.perform(get("/cafeteria/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(service.today().toString()));
    }

    @Test
    void badDateIs400() throws Exception {
        for (String bad : List.of("2026-13-01", "2026-02-30", "20261007", "2026-10-7", "abc", "1999-01-01", "../etc")) {
            mockMvc.perform(get("/cafeteria/menus").param("date", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(CafeteriaMenuController.BAD_DATE_MESSAGE));
            mockMvc.perform(get("/cafeteria/menus/week").param("date", bad))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void weekResponseIsMondayToFriday() throws Exception {
        job(true).runOnce("test");

        for (String date : List.of("2026-10-08", "2026-10-05", "2026-10-11")) {
            mockMvc.perform(get("/cafeteria/menus/week").param("date", date))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "public, max-age=600"))
                    .andExpect(jsonPath("$.days", hasSize(5)))
                    .andExpect(jsonPath("$.days[*].date",
                            contains("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09")))
                    .andExpect(jsonPath("$.days[0].restaurants[0].meals[0].closed").value(true))
                    .andExpect(jsonPath("$.days[2].restaurants[0].meals[*].meal", contains("아침", "점심A", "점심B", "저녁")))
                    .andExpect(jsonPath("$.days[2].source").value("홍익대학교 홈페이지"));
        }

        mockMvc.perform(get("/cafeteria/menus/week").param("date", "2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-10-12"))
                .andExpect(jsonPath("$.days[0].restaurants[0].meals", hasSize(0)));
    }
}
