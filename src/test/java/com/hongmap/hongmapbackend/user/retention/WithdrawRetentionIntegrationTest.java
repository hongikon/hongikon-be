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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴 회원 부정 이용 방지 기록(withdraw_retentions): 대상 판단(정지 이력·신고받은 제보), 해시 저장(원문 없음)·스냅숏·1년 기한,
 * 사진 사본(커밋 뒤 retained/ 복사 → 원본 삭제), 재가입 감지(관리자 알림·연결·관리자 API), 재탈퇴 갱신, 만료 정리.
 * S3 는 메모리 가짜 저장소, 관리자 알림은 발행된 이벤트(@RecordApplicationEvents)로 확인한다.
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
        final List<String> copied = Collections.synchronizedList(new ArrayList<>());
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
        @Override public synchronized void copy(String sourceKey, String destinationKey) {
            StoredObject source = objects.get(sourceKey);
            if (source == null) {
                throw new IllegalStateException("no such key");
            }
            objects.put(destinationKey, source);
            copied.add(sourceKey + "->" + destinationKey);
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
        storage.copied.clear();
        storage.deleted.clear();
        building = buildingRepository.save(Building.builder()
                .name("보관테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        admin = newUser("관리자", UUID.randomUUID().toString());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @Test
    void 이력이_없는_회원은_기록을_남기지_않고_사진도_복사하지_않는다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("평범한학생", socialId);
        User other = newUser("다른학생", UUID.randomUUID().toString());
        String imageKey = uploadedKey();
        saveReport(me, "평범한 제보", List.of(imageKey));
        // 남의 제보에 신고를 단 것만으로는 대상이 아니다
        Report othersReport = saveReport(other, "남의 제보", List.of());
        reportFlagRepository.save(ReportFlag.builder().report(othersReport).user(me).reason("SPAM").build());

        withdraw(me);

        assertThat(recordOf(SocialType.KAKAO, socialId)).isEmpty();
        assertThat(storage.copied).isEmpty();
        assertThat(storage.deleted).contains(imageKey);
    }

    @Test
    void 정지된_회원은_해시_스냅숏_1년_기한으로_기록되고_사진_사본을_뜬다() throws Exception {
        String socialId = "kakao-" + UUID.randomUUID();
        User me = newUser("정지된학생", socialId);
        User other = newUser("신고한학생", UUID.randomUUID().toString());
        String imageKey = uploadedKey();
        Report mine = saveReport(me, "도배 제보", List.of(imageKey));
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
        assertThat(record.getReportCount()).isEqualTo(1);
        assertThat(record.getFlaggedReportCount()).isZero();
        assertThat(record.getRetainUntil()).isCloseTo(record.getWithdrawnAt().plusYears(1), within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(record.getWithdrawnAt()).isCloseTo(LocalDateTime.now(), within(1, java.time.temporal.ChronoUnit.MINUTES));
        assertThat(record.getRejoinedUserId()).isNull();

        // 스냅숏: 제보·단 신고는 있고 닉네임은 없다
        assertThat(record.getSnapshot()).doesNotContain("정지된학생").doesNotContain("신고한학생");
        RetentionSnapshot snapshot = retentionService.readSnapshot(record);
        assertThat(snapshot.withdrawals()).hasSize(1);
        RetentionSnapshot.Withdrawal w = snapshot.withdrawals().get(0);
        assertThat(w.status()).isEqualTo("SUSPENDED");
        assertThat(w.reports()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(mine.getId());
            assertThat(r.title()).isEqualTo("도배 제보");
            assertThat(r.buildingId()).isEqualTo(building.getId());
            assertThat(r.floor()).isEqualTo(1);
            assertThat(r.retainedImageKeys()).containsExactly("retained/" + imageKey);
        });
        assertThat(w.flagsFiled()).singleElement().satisfies(f -> {
            assertThat(f.reportId()).isEqualTo(othersReport.getId());
            assertThat(f.reason()).isEqualTo("FALSE_INFO");
        });

        // 사진: 커밋 뒤 retained/ 로 복사한 다음 원본 삭제
        assertThat(record.getRetainedImageKeys()).containsExactly("retained/" + imageKey);
        assertThat(storage.copied).containsExactly(imageKey + "->retained/" + imageKey);
        assertThat(storage.deleted).contains(imageKey);
        assertThat(storage.objects).containsKey("retained/" + imageKey).doesNotContainKey(imageKey);
        assertThat(userRepository.findById(me.getId())).isEmpty();
    }

    @Test
    void 신고받은_제보를_쓴_회원은_정지되지_않았어도_기록된다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("신고받은학생", socialId);
        User flagger1 = newUser("신고자1", UUID.randomUUID().toString());
        User flagger2 = newUser("신고자2", UUID.randomUUID().toString());
        Report flagged = saveReport(me, "허위 제보", List.of());
        saveReport(me, "멀쩡한 제보", List.of());
        reportFlagRepository.save(ReportFlag.builder().report(flagged).user(flagger1).reason("FALSE_INFO").build());
        reportFlagRepository.save(ReportFlag.builder().report(flagged).user(flagger2).reason("SPAM").build());

        withdraw(me);

        WithdrawRetention record = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        assertThat(record.isWasSuspended()).isFalse();
        assertThat(record.getSuspendedAt()).isNull();
        assertThat(record.getReportCount()).isEqualTo(2);
        assertThat(record.getFlaggedReportCount()).isEqualTo(1);
        RetentionSnapshot.ReportEntry entry = retentionService.readSnapshot(record).withdrawals().get(0).reports().stream()
                .filter(r -> r.id().equals(flagged.getId())).findFirst().orElseThrow();
        assertThat(entry.flagCount()).isEqualTo(2);
        assertThat(entry.flagReasons()).containsExactlyInAnyOrder("FALSE_INFO", "SPAM");
        assertThat(record.getSnapshot()).doesNotContain("신고자1");
        assertThat(reportRepository.findById(flagged.getId())).isEmpty();
    }

    @Test
    void 같은_소셜_계정으로_재가입하면_관리자_알림과_연결이_되고_관리자_API에_이력이_보인다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User me = newUser("돌아온학생", socialId);
        saveReport(me, "정지 전 제보", List.of());
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
                .andExpect(jsonPath("$.priorHistory.reportCount").value(1))
                .andExpect(jsonPath("$.priorHistory.flaggedReportCount").value(0));
        mockMvc.perform(get("/admin/users").param("q", String.valueOf(rejoined.getId())).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].priorHistory.reportCount").value(1));
        mockMvc.perform(get("/admin/users/" + rejoined.getId() + "/prior-history").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(rejoined.getId()))
                .andExpect(jsonPath("$.priorHistory.wasSuspendedAtWithdrawal").value(true))
                .andExpect(jsonPath("$.withdrawals[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.withdrawals[0].reports[0].title").value("정지 전 제보"))
                .andExpect(jsonPath("$.retainedImageUrls").isArray());

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
    void 기한이_지난_기록은_정리_작업이_행과_사진_사본을_지운다() throws Exception {
        String expiredSocialId = UUID.randomUUID().toString();
        User expiredUser = newUser("오래된학생", expiredSocialId);
        String imageKey = uploadedKey();
        saveReport(expiredUser, "오래된 제보", List.of(imageKey));
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
        assertThat(storage.deleted).contains("retained/" + imageKey);
        assertThat(storage.objects).doesNotContainKey("retained/" + imageKey);
        assertThat(recordOf(SocialType.KAKAO, activeSocialId)).isPresent();
    }

    @Test
    void 재가입한_회원이_다시_탈퇴하면_같은_기록을_갱신하고_기한을_다시_센다() throws Exception {
        String socialId = UUID.randomUUID().toString();
        User first = newUser("반복학생", socialId);
        saveReport(first, "첫 계정 제보", List.of());
        suspend(first, "도배");
        withdraw(first);
        WithdrawRetention before = recordOf(SocialType.KAKAO, socialId).orElseThrow();
        // 첫 탈퇴가 한 달 전이었던 것처럼 당겨 둔다
        LocalDateTime earlier = LocalDateTime.now().minusMonths(1);
        jdbcTemplate.update("UPDATE withdraw_retentions SET withdrawn_at = ?, retain_until = ? WHERE id = ?",
                earlier, earlier.plusYears(1), before.getId());

        User second = signUp("반복학생", socialId);
        saveReport(second, "둘째 계정 제보", List.of());
        withdraw(second); // 둘째 계정은 정지·신고 이력이 없어도 기존 기록에 이어 붙는다

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
        assertThat(after.getReportCount()).isEqualTo(2);
        RetentionSnapshot snapshot = retentionService.readSnapshot(after);
        assertThat(snapshot.withdrawals()).hasSize(2);
        assertThat(snapshot.withdrawals().get(0).reports()).extracting(RetentionSnapshot.ReportEntry::title).containsExactly("첫 계정 제보");
        assertThat(snapshot.withdrawals().get(1).reports()).extracting(RetentionSnapshot.ReportEntry::title).containsExactly("둘째 계정 제보");
        assertThat(snapshot.withdrawals().get(1).status()).isEqualTo("ACTIVE");
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
        LocalDateTime now = LocalDateTime.now();
        Report report = Report.builder()
                .user(user).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title(title).content("본문")
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
