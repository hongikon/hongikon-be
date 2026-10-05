package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.admin.AdminAlertType;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportFlag;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.image.ReportImageStorage;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴 회원 부정 이용 방지 기록(withdraw_retentions): 대상 판단(정지 이력·관리자가 삭제한 위반 확정 제보 — 신고만 받았거나 반려된
 * 제보는 아님), 해시 저장(원문 없음)·위반 제보 요약만·1년 기한, 사진은 보관하지 않음(사본 없이 모두 삭제),
 * 재가입 감지(관리자 알림·연결·관리자 API), 재탈퇴 갱신, 만료 정리(행만).
 * S3 는 메모리 가짜 저장소(삭제만 기록), 관리자 알림은 발행된 이벤트(@RecordApplicationEvents)로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class WithdrawRetentionIntegrationTest {

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        FakeStorage fakeRetentionImageStorage() {
            return new FakeStorage();
        }
    }

    static class FakeStorage implements ReportImageStorage {
        final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        final List<String> deleted = Collections.synchronizedList(new ArrayList<>());

        @Override public boolean isEnabled() { return true; }
        @Override public PresignedUpload presignUpload(String key, String type, Long len, Duration ttl) {
            return new PresignedUpload("https://bucket.s3.test/" + key, Map.of(), Instant.now().plus(ttl));
        }
        @Override public Optional<StoredObject> head(String key) { return Optional.ofNullable(objects.get(key)); }
        @Override public Optional<byte[]> get(String key, long maxBytes) { return Optional.empty(); }
        @Override public void put(String key, String type, byte[] bytes) { objects.put(key, new StoredObject(bytes.length, type)); }
        @Override public String presignView(String key, Duration ttl) { return "https://bucket.s3.test/" + key + "?X-Amz-Signature=get"; }
        @Override public synchronized void delete(String key) {
            objects.remove(key);
            deleted.add(key);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportFlagRepository reportFlagRepository;
    @Autowired WithdrawRetentionRepository retentionRepository;
    @Autowired WithdrawRetentionService retentionService;
    @Autowired WithdrawRetentionKeys retentionKeys;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired FakeStorage storage;
    @Autowired ApplicationEvents events;

    Building building;
    User admin;

    @BeforeEach
    void setUp() {
        storage.objects.clear();
        storage.deleted.clear();
        building = buildingRepository.save(Building.builder()
                .name("보관테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        admin = newUser("관리자", UUID.randomUUID().toString());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @Test
    void 신고만_받고_처리되지_않은_제보의_작성자는_기록을_남기지_않는다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("평범한학생", socialId);
        User other = newUser("다른학생", UUID.randomUUID().toString());
        String imageKey = uploadedKey();
        Report flaggedOnly = saveReport(me, "신고만 받은 제보", List.of(imageKey));
        reportFlagRepository.save(ReportFlag.builder().report(flaggedOnly).user(other).reason("SPAM").build());
        // 신고 누적 자동 숨김(HIDDEN, 관리자 처리 아님)도 위반 확정이 아니다
        jdbcTemplate.update("UPDATE reports SET status = 'HIDDEN' WHERE id = ?", flaggedOnly.getId());
        // 남의 제보에 신고를 단 것도 대상이 아니다
        Report othersReport = saveReport(other, "남의 제보", List.of());
        reportFlagRepository.save(ReportFlag.builder().report(othersReport).user(me).reason("SPAM").build());

        withdraw(me);

        assertThat(recordOf(SocialType.KAKAO, socialId)).isEmpty();
        assertThat(storage.deleted).contains(imageKey);
        assertThat(storage.objects).doesNotContainKey(imageKey);
    }

    @Test
    void 관리자가_반려만_한_제보의_작성자는_기록을_남기지_않는다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("반려학생", socialId);
        Report report = saveReport(me, "중복 제보", List.of());
        moderate(report, "REJECTED", "중복 제보");

        withdraw(me);

        assertThat(recordOf(SocialType.KAKAO, socialId)).isEmpty();
    }

    @Test
    void 정지된_회원은_해시와_정지_정보로_1년_기록되고_제보와_사진은_남기지_않는다() throws Exception {
        String socialId = "kakao-" + UUID.randomUUID();
        User me = newUser("정지된학생", socialId);
        User other = newUser("신고한학생", UUID.randomUUID().toString());
        String imageKey = uploadedKey();
        saveReport(me, "평범한 제보", List.of(imageKey));
        Report othersReport = saveReport(other, "남의 제보", List.of());
        reportFlagRepository.save(ReportFlag.builder().report(othersReport).user(me).reason("FALSE_INFO").build());
        suspend(me, "도배");

        withdraw(me);

        WithdrawRetention record = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        // 소셜 id 원문은 어디에도 없고, 서버 키 HMAC(hex 64자)만 남는다
        assertThat(record.getSocialIdHash()).hasSize(64).isNotEqualTo(socialId)
                .isEqualTo(retentionKeys.hash(SocialType.KAKAO, socialId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdraw_retentions WHERE social_id_hash = ? OR snapshot LIKE ?",
                Long.class, socialId, "%" + socialId + "%")).isZero();
        assertThat(record.isWasSuspended()).isTrue();
        assertThat(record.getSuspendedReason()).isEqualTo("도배");
        assertThat(record.getSuspendedAt()).isNotNull();
        assertThat(record.getViolationReportCount()).isZero();
        assertThat(record.getRetainUntil()).isCloseTo(record.getWithdrawnAt().plusYears(1), within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(record.getWithdrawnAt()).isCloseTo(LocalDateTime.now(), within(1, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(record.getRejoinedUserId()).isNull();

        // 스냅숏: 정지 정보만. 위반이 아닌 제보·단 신고·닉네임은 없다
        assertThat(record.getSnapshot()).doesNotContain("평범한 제보").doesNotContain("FALSE_INFO")
                .doesNotContain("정지된학생").doesNotContain("flagsFiled");
        RetentionSnapshot.Withdrawal w = retentionService.readSnapshot(record).withdrawals().get(0);
        assertThat(w.status()).isEqualTo("SUSPENDED");
        assertThat(w.suspendedReason()).isEqualTo("도배");
        assertThat(w.violationReports()).isEmpty();

        // 사진은 보관하지 않고 지금처럼 지운다
        assertThat(storage.deleted).contains(imageKey);
        assertThat(storage.objects).isEmpty();
        assertThat(userRepository.findById(me.getId())).isEmpty();
    }

    @Test
    void 관리자가_삭제한_제보가_있으면_그_제보_요약만_보관하고_사진은_보관하지_않는다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("삭제당한학생", socialId);
        User flagger1 = newUser("신고자1", UUID.randomUUID().toString());
        User flagger2 = newUser("신고자2", UUID.randomUUID().toString());
        String violationImage = uploadedKey();
        String normalImage = uploadedKey();
        Report violation = saveReport(me, "허위 제보", "가".repeat(250), List.of(violationImage));
        Report normal = saveReport(me, "멀쩡한 제보", "본문", List.of(normalImage));
        reportFlagRepository.save(ReportFlag.builder().report(violation).user(flagger1).reason("FALSE_INFO").build());
        reportFlagRepository.save(ReportFlag.builder().report(violation).user(flagger2).reason("SPAM").build());
        reportFlagRepository.save(ReportFlag.builder().report(normal).user(flagger1).reason("SPAM").build());
        moderate(violation, "DELETED", "허위 정보 반복"); // 관리자 삭제 — 사진은 이 시점에 지워진다
        assertThat(storage.deleted).contains(violationImage);

        withdraw(me);

        WithdrawRetention record = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        assertThat(record.isWasSuspended()).isFalse();
        assertThat(record.getSuspendedAt()).isNull();
        assertThat(record.getViolationReportCount()).isEqualTo(1);
        RetentionSnapshot.Withdrawal w = retentionService.readSnapshot(record).withdrawals().get(0);
        assertThat(w.violationReports()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(violation.getId());
            assertThat(r.category()).isEqualTo("FOOD_TRUCK");
            assertThat(r.title()).isEqualTo("허위 제보");
            assertThat(r.content()).hasSize(201).endsWith("…"); // 200자 요약
            assertThat(r.status()).isEqualTo("DELETED");
            assertThat(r.createdAt()).isNotNull();
            assertThat(r.moderationNote()).isEqualTo("허위 정보 반복");
            assertThat(r.flagCount()).isEqualTo(2);
            assertThat(r.flagReasons()).containsExactlyInAnyOrder("FALSE_INFO", "SPAM");
        });
        // 위치·기간·사진·위반 아닌 제보·신고자 닉네임은 스냅숏에 없다
        assertThat(record.getSnapshot()).doesNotContain("멀쩡한 제보").doesNotContain("신고자1")
                .doesNotContain("\"lat\"").doesNotContain("\"lng\"").doesNotContain("\"floor\"")
                .doesNotContain("building").doesNotContain("\"startsAt\"").doesNotContain("\"endsAt\"")
                .doesNotContain("Image").doesNotContain("reports/");

        // 사진: 사본 없이 모두 삭제
        assertThat(storage.deleted).contains(violationImage, normalImage);
        assertThat(storage.objects).isEmpty();
        assertThat(reportRepository.findById(violation.getId())).isEmpty();
    }

    @Test
    void 같은_소셜_계정으로_재가입하면_관리자_알림과_연결이_되고_관리자_API에_이력이_보인다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("돌아온학생", socialId);
        Report violation = saveReport(me, "정지 전 위반 제보", List.of());
        moderate(violation, "DELETED", "욕설");
        saveReport(me, "평범한 제보", List.of());
        suspend(me, "욕설");
        withdraw(me);

        User rejoined = signUp("돌아온학생", socialId);

        WithdrawRetention record = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        assertThat(record.getRejoinedUserId()).isEqualTo(rejoined.getId());
        assertThat(record.getRejoinedAt()).isNotNull();
        assertThat(events.stream(AdminAlertEvent.class)
                .filter(e -> e.type() == AdminAlertType.MEMBER_REJOINED && rejoined.getId().equals(e.targetId())))
                .hasSize(1);

        mockMvc.perform(get("/admin/users/" + rejoined.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorHistory.wasSuspendedAtWithdrawal").value(true))
                .andExpect(jsonPath("$.priorHistory.suspendedReason").value("욕설"))
                .andExpect(jsonPath("$.priorHistory.suspendedAt").isString())
                .andExpect(jsonPath("$.priorHistory.withdrawnAt").isString())
                .andExpect(jsonPath("$.priorHistory.retainUntil").isString())
                .andExpect(jsonPath("$.priorHistory.rejoinedAt").isString())
                .andExpect(jsonPath("$.priorHistory.violationReportCount").value(1))
                .andExpect(jsonPath("$.priorHistory.reportCount").doesNotExist())
                .andExpect(jsonPath("$.priorHistory.flaggedReportCount").doesNotExist());
        mockMvc.perform(get("/admin/users").param("q", String.valueOf(rejoined.getId())).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].priorHistory.violationReportCount").value(1));
        mockMvc.perform(get("/admin/users/" + rejoined.getId() + "/prior-history").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(rejoined.getId()))
                .andExpect(jsonPath("$.priorHistory.wasSuspendedAtWithdrawal").value(true))
                .andExpect(jsonPath("$.withdrawals[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.withdrawals[0].violationReports.length()").value(1))
                .andExpect(jsonPath("$.withdrawals[0].violationReports[0].title").value("정지 전 위반 제보"))
                .andExpect(jsonPath("$.withdrawals[0].violationReports[0].moderationNote").value("욕설"))
                .andExpect(jsonPath("$.withdrawals[0].violationReports[0].retainedImageKeys").doesNotExist())
                .andExpect(jsonPath("$.withdrawals[0].flagsFiled").doesNotExist())
                .andExpect(jsonPath("$.retainedImageUrls").doesNotExist());

        // 이력이 없는 회원은 null / 404, 관리자가 아니면 403
        mockMvc.perform(get("/admin/users/" + admin.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorHistory").value(nullValue()));
        mockMvc.perform(get("/admin/users/" + admin.getId() + "/prior-history").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/users/" + rejoined.getId() + "/prior-history").header("Authorization", bearer(rejoined)))
                .andExpect(status().isForbidden());
        // 자동 정지는 하지 않는다
        assertThat(userRepository.findById(rejoined.getId()).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    void 처음_가입하는_계정은_알림도_연결도_없다() {
        User fresh = signUp("새학생", UUID.randomUUID().toString());

        assertThat(events.stream(AdminAlertEvent.class).filter(e -> e.type() == AdminAlertType.MEMBER_REJOINED)).isEmpty();
        assertThat(retentionService.findActiveForUser(fresh.getId())).isEmpty();
    }

    @Test
    void 기한이_지난_기록은_정리_작업이_행을_지운다() throws Exception {
        String expiredSocialId = UUID.randomUUID().toString();
        User expiredUser = newUser("오래된학생", expiredSocialId);
        suspend(expiredUser, "도배");
        withdraw(expiredUser);

        String activeSocialId = UUID.randomUUID().toString();
        User activeUser = newUser("최근학생", activeSocialId);
        suspend(activeUser, "도배");
        withdraw(activeUser);

        WithdrawRetention expired = recordOf(SocialType.KAKAO, expiredSocialId).orElseThrow();
        jdbcTemplate.update("UPDATE withdraw_retentions SET retain_until = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), expired.getId());
        storage.deleted.clear();

        int purged = retentionService.purgeExpired();

        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(retentionRepository.findById(expired.getId())).isEmpty();
        assertThat(storage.deleted).isEmpty(); // 지울 사진 사본이 없다
        assertThat(recordOf(SocialType.KAKAO, activeSocialId)).isPresent();
    }

    @Test
    void 재가입한_회원이_다시_탈퇴하면_같은_기록을_갱신하고_기한을_다시_센다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User first = newUser("반복학생", socialId);
        Report firstViolation = saveReport(first, "첫 계정 위반 제보", List.of());
        moderate(firstViolation, "DELETED", "도배");
        suspend(first, "도배");
        withdraw(first);
        WithdrawRetention before = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        // 첫 탈퇴가 한 달 전이었던 것처럼 당겨 둔다
        LocalDateTime earlier = LocalDateTime.now().minusMonths(1);
        jdbcTemplate.update("UPDATE withdraw_retentions SET withdrawn_at = ?, retain_until = ? WHERE id = ?",
                earlier, earlier.plusYears(1), before.getId());

        User second = signUp("반복학생", socialId);
        saveReport(second, "둘째 계정 평범한 제보", List.of());
        withdraw(second); // 둘째 계정은 정지·위반 이력이 없어도 기존 기록에 이어 붙는다

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdraw_retentions WHERE social_type = 'KAKAO' AND social_id_hash = ?",
                Long.class, retentionKeys.hash(SocialType.KAKAO, socialId))).isEqualTo(1L);
        WithdrawRetention after = retentionRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getRetainUntil()).isAfter(earlier.plusYears(1).plusDays(20));
        assertThat(after.getRetainUntil()).isCloseTo(after.getWithdrawnAt().plusYears(1), within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(after.getRejoinedUserId()).isNull();
        assertThat(after.getRejoinedAt()).isNull();
        assertThat(after.isWasSuspended()).isTrue();
        assertThat(after.getSuspendedReason()).isEqualTo("도배"); // 이번엔 정지 이력이 없으니 이전 정보 유지
        assertThat(after.getViolationReportCount()).isEqualTo(1);
        RetentionSnapshot snapshot = retentionService.readSnapshot(after);
        assertThat(snapshot.withdrawals()).hasSize(2);
        assertThat(snapshot.withdrawals().get(0).violationReports())
                .extracting(RetentionSnapshot.ViolationReport::title).containsExactly("첫 계정 위반 제보");
        assertThat(snapshot.withdrawals().get(1).violationReports()).isEmpty();
        assertThat(snapshot.withdrawals().get(1).status()).isEqualTo("ACTIVE");
        assertThat(after.getSnapshot()).doesNotContain("둘째 계정 평범한 제보");
    }

    private User newUser(String nickname, String socialId) {
        return userRepository.save(User.builder().socialId(socialId).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    /** 카카오 로그인(CustomOAuth2UserService)처럼 한 트랜잭션 안에서 회원을 만들고 재가입을 확인한다. */
    private User signUp(String nickname, String socialId) {
        return transactionTemplate.execute(status -> {
            User created = newUser(nickname, socialId);
            retentionService.onSignup(created);
            return created;
        });
    }

    private void suspend(User user, String reason) {
        User loaded = userRepository.findById(user.getId()).orElseThrow();
        loaded.suspend(reason);
        userRepository.save(loaded);
    }

    private String uploadedKey() {
        String key = "reports/" + UUID.randomUUID() + ".jpg";
        storage.objects.put(key, new ReportImageStorage.StoredObject(100, "image/jpeg"));
        return key;
    }

    private Report saveReport(User user, String title, List<String> imageKeys) {
        return saveReport(user, title, "본문", imageKeys);
    }

    /** 실제 관리자 검토 API(PATCH /admin/reports/{id})로 상태를 바꾼다. */
    private void moderate(Report report, String status, String note) throws Exception {
        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\",\"note\":\"" + note + "\"}"))
                .andExpect(status().isOk());
    }

    private Report saveReport(User user, String title, String content, List<String> imageKeys) {
        LocalDateTime now = LocalDateTime.now();
        Report report = Report.builder()
                .user(user).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title(title).content(content)
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build();
        report.addImages(imageKeys);
        return reportRepository.save(report);
    }

    private void withdraw(User user) throws Exception {
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
    }

    private Optional<WithdrawRetention> recordOf(SocialType type, String socialId) {
        return retentionRepository.findBySocialTypeAndSocialIdHash(type, retentionKeys.hash(type, socialId));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }
}
