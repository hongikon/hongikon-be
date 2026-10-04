package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import com.hongmap.hongmapbackend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 탈퇴 회원 부정 이용 방지 기록(개인정보 처리방침: 탈퇴 후 1년 분리 보관). 운영자 결정(2026-10-04):
 * <ul>
 *   <li><b>대상</b>: 탈퇴 시점에 이용 정지(SUSPENDED)거나 정지된 적이 있는(suspended_at 있음) 회원, 또는 신고(report_flags)를
 *       1건 이상 받은 제보를 쓴 회원. 그 밖의 회원은 지금처럼 즉시 삭제하고 아무것도 남기지 않는다.
 *       단, 이미 보관 중인 기록이 있는 계정(재가입 후 재탈퇴)은 새 이력이 없어도 기록을 이어 붙이고 기한을 다시 센다.</li>
 *   <li><b>무엇을</b>: 소셜 종류 + 소셜 id 의 HMAC(원문 아님), 정지 여부·사유·시각, 제보·받은 신고·단 신고 스냅숏(JSON),
 *       제보 사진 사본(S3 retained/). 닉네임·이메일·Apple 토큰은 남기지 않는다.</li>
 *   <li><b>얼마나</b>: 마지막 탈퇴로부터 1년. {@link #purgeExpired()} 가 매일 행과 사진 사본을 지운다.</li>
 *   <li><b>재가입</b>: 같은 소셜 계정으로 새로 가입하면({@link #onSignup(User)}) 관리자 알림(MEMBER_REJOINED)을 보내고
 *       기록에 새 회원 id 를 연결한다. 자동 정지는 하지 않는다 — 관리자 회원 카드(priorHistory)에서 판단한다.</li>
 * </ul>
 * 로그에는 소셜 id 원문·해시를 남기지 않는다(회원 id·기록 id 만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawRetentionService {

    /** 보관 기간(처리방침과 맞출 것). 바꾸면 FE 개인정보 처리방침 문구도 함께 고친다. */
    public static final Period RETENTION_PERIOD = Period.ofYears(1);

    private final WithdrawRetentionRepository repository;
    private final WithdrawRetentionKeys keys;
    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final ReportImageService reportImageService;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    /**
     * 회원탈퇴(UserService.withdraw) 안에서, 제보·신고를 지우기 <b>전에</b> 부른다(같은 트랜잭션 — 탈퇴가 롤백되면 기록도 없다).
     *
     * @return true 면 이 회원의 제보 사진을 보관 대상으로 기록했다 — 호출한 쪽은 원본을 지우기 전에 사본을 떠야 한다
     *         ({@link ReportImageService#retainThenDeleteAfterCommit(String)}). false 면 기록 없음(지금처럼 바로 삭제).
     */
    @Transactional
    public boolean retainOnWithdraw(User user) {
        String hash = keys.hash(user.getSocialType(), user.getSocialId());
        if (hash == null) {
            return false;
        }
        LocalDateTime now = now();
        Optional<WithdrawRetention> existing = repository.findBySocialTypeAndSocialIdHash(user.getSocialType(), hash);
        boolean hasActiveRecord = existing.filter(r -> !r.isExpired(now)).isPresent();

        // 이 회원이 쓴 제보에 달린 신고(제보 id → 사유 목록). 대상 판단에 먼저 쓴다 — 일반 회원은 제보 본문을 읽지 않고 끝난다.
        Map<Long, List<String>> reasonsByReport = new LinkedHashMap<>();
        for (Object[] row : reportFlagRepository.findReasonsOnReportsOfUser(user.getId())) {
            reasonsByReport.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
        }
        boolean suspensionHistory = user.isSuspended() || user.getSuspendedAt() != null;
        if (!suspensionHistory && reasonsByReport.isEmpty() && !hasActiveRecord) {
            return false;
        }

        List<Report> reports = reportRepository.findAllWithBuildingByUserId(user.getId());
        List<String> retainedImageKeys = new ArrayList<>();
        List<RetentionSnapshot.ReportEntry> reportEntries = new ArrayList<>();
        for (Report report : reports) {
            List<String> retained = report.getImageKeys().stream().map(ReportImageService::retainedKeyOf).toList();
            retainedImageKeys.addAll(retained);
            List<String> reasons = reasonsByReport.getOrDefault(report.getId(), List.of());
            reportEntries.add(new RetentionSnapshot.ReportEntry(
                    report.getId(), report.getCategory().name(), report.getCustomCategoryLabel(),
                    report.getTitle(), report.getContent(),
                    report.getBuilding().getId(), report.getBuilding().getName(), report.getFloor(),
                    report.getLat(), report.getLng(), report.getStartsAt(), report.getEndsAt(),
                    report.getStatus().name(), report.getCreatedAt(), retained, reasons.size(), List.copyOf(reasons)));
        }
        List<RetentionSnapshot.FiledFlag> filed = reportFlagRepository.findFiledByUser(user.getId()).stream()
                .map(row -> new RetentionSnapshot.FiledFlag((Long) row[0], (String) row[1], (LocalDateTime) row[2]))
                .toList();
        int flaggedReportCount = (int) reportEntries.stream().filter(e -> e.flagCount() > 0).count();

        WithdrawRetention record = existing.orElseGet(() -> new WithdrawRetention(user.getSocialType(), hash));
        List<RetentionSnapshot.Withdrawal> withdrawals = new ArrayList<>();
        if (hasActiveRecord) {
            withdrawals.addAll(readSnapshot(record).withdrawals());
        } else if (existing.isPresent()) {
            // 만료됐지만 아직 정리 전인 행 — 지난 이력은 버리고(기한이 지났다) 사진 사본도 지운다.
            record.getRetainedImageKeys().forEach(reportImageService::deleteAfterCommit);
            record.resetExpired();
        }
        withdrawals.add(new RetentionSnapshot.Withdrawal(now, user.getStatus().name(), user.getSuspendedReason(),
                user.getSuspendedAt(), reportEntries, filed));

        record.recordWithdrawal(user.isSuspended(), user.getSuspendedReason(), user.getSuspendedAt(),
                reportEntries.size(), flaggedReportCount,
                jsonMapper.writeValueAsString(new RetentionSnapshot(RetentionSnapshot.VERSION, withdrawals)),
                retainedImageKeys, now, now.plus(RETENTION_PERIOD));
        repository.save(record);
        log.info("withdraw retention recorded userId={} retentionId={} suspended={} reports={} flaggedReports={} images={} renewed={}",
                user.getId(), record.getId(), user.isSuspended(), reportEntries.size(), flaggedReportCount,
                retainedImageKeys.size(), hasActiveRecord);
        return true;
    }

    /**
     * 소셜 로그인으로 회원을 <b>새로</b> 만든 직후 부른다(카카오: CustomOAuth2UserService, Apple: AppleLoginService).
     * 보관 중인 기록과 같은 계정이면 관리자 알림을 발행하고(커밋 뒤 AdminAlertDispatcher, 묶음 규칙 그대로) 기록에 새 회원을 연결한다.
     * 가입·로그인을 막지 않는다 — 실패는 로그만.
     */
    @Transactional
    public void onSignup(User user) {
        try {
            String hash = keys.hash(user.getSocialType(), user.getSocialId());
            if (hash == null) {
                return;
            }
            LocalDateTime now = now();
            repository.findBySocialTypeAndSocialIdHash(user.getSocialType(), hash)
                    .filter(record -> !record.isExpired(now))
                    .ifPresent(record -> {
                        record.linkRejoinedUser(user.getId(), now);
                        repository.save(record);
                        eventPublisher.publishEvent(AdminAlertEvent.memberRejoined(user.getId()));
                        log.info("withdraw retention matched on signup userId={} retentionId={}", user.getId(), record.getId());
                    });
        } catch (RuntimeException e) {
            log.warn("withdraw retention check on signup failed userId={}: {}", user.getId(), e.toString());
        }
    }

    /** 관리자 회원 카드: 이 회원(재가입자)과 연결된, 아직 보관 중인 기록. */
    @Transactional(readOnly = true)
    public Optional<WithdrawRetention> findActiveForUser(Long userId) {
        return repository.findFirstByRejoinedUserIdAndRetainUntilAfter(userId, now());
    }

    /** 관리자 회원 목록: 회원 id → 보관 중인 기록(없는 회원은 빠진다). */
    @Transactional(readOnly = true)
    public Map<Long, WithdrawRetention> findActiveForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByRejoinedUserIdInAndRetainUntilAfter(userIds, now()).stream()
                .collect(Collectors.toMap(WithdrawRetention::getRejoinedUserId, Function.identity(), (a, b) -> a));
    }

    public RetentionSnapshot readSnapshot(WithdrawRetention record) {
        if (record.getSnapshot() == null || record.getSnapshot().isBlank()) {
            return new RetentionSnapshot(RetentionSnapshot.VERSION, List.of());
        }
        try {
            RetentionSnapshot snapshot = jsonMapper.readValue(record.getSnapshot(), RetentionSnapshot.class);
            return snapshot.withdrawals() == null ? new RetentionSnapshot(snapshot.version(), List.of()) : snapshot;
        } catch (JacksonException e) {
            log.warn("withdraw retention snapshot unreadable retentionId={}: {}", record.getId(), e.getOriginalMessage());
            return new RetentionSnapshot(RetentionSnapshot.VERSION, List.of());
        }
    }

    /** 보관 사진 사본의 보기 URL(presigned GET). 저장소가 꺼져 있으면 빈 목록. */
    public List<String> retainedImageUrls(WithdrawRetention record) {
        return reportImageService.viewUrls(record.getRetainedImageKeys());
    }

    /**
     * 보관 기한(retain_until)이 지난 기록과 사진 사본을 지운다. 매일 1회(app.withdraw-retention.purge-cron, 기본 03:40 UTC).
     * 사진은 커밋 뒤 지우고 실패는 로그만 — S3 수명 주기 규칙(retained/ 약 366일)이 안전망이다. 한 번에 최대 200건.
     *
     * @return 지운 기록 수
     */
    @Scheduled(cron = "${app.withdraw-retention.purge-cron:0 40 3 * * *}")
    @Transactional
    public int purgeExpired() {
        List<WithdrawRetention> expired = repository.findTop200ByRetainUntilLessThanEqualOrderByIdAsc(now());
        int images = 0;
        for (WithdrawRetention record : expired) {
            List<String> imageKeys = record.getRetainedImageKeys() == null ? List.of() : record.getRetainedImageKeys();
            imageKeys.forEach(reportImageService::deleteAfterCommit);
            images += imageKeys.size();
            repository.delete(record);
        }
        if (!expired.isEmpty()) {
            log.info("withdraw retention purged {} records, {} retained images scheduled for deletion", expired.size(), images);
        }
        return expired.size();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
