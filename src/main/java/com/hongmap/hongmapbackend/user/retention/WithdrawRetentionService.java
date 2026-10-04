package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 탈퇴 회원 부정 이용 방지 기록(개인정보 처리방침: 탈퇴 후 1년 분리 보관). 운영자 결정(2026-10-04):
 * <ul>
 *   <li><b>대상</b>(2026-10-04 법무 검토로 축소 — 개인정보 보호법 제3조·제16조 최소 수집, 제15조 제1항 제6호 필요성·비례성):
 *       탈퇴 시점에 이용 정지(SUSPENDED)거나 정지된 적이 있는(suspended_at 있음) 회원, 또는 운영진이 위반으로 확정한
 *       제보(관리자 삭제 DELETED, {@link #CONFIRMED_VIOLATION_STATUSES})가 1건 이상인 회원. 신고만 받았거나 반려(REJECTED)된 제보는 대상이 아니다.
 *       그 밖의 회원은 지금처럼 즉시 삭제하고 아무것도 남기지 않는다.
 *       단, 이미 보관 중인 기록이 있는 계정(재가입 후 재탈퇴)은 새 이력이 없어도 기록을 이어 붙이고 기한을 다시 센다.</li>
 *   <li><b>무엇을</b>: 소셜 종류 + 소셜 id 의 HMAC(원문 아님), 정지 여부·사유·시각, 위반 확정 제보의 요약(RetentionSnapshot —
 *       분류·제목·본문 200자·상태·작성일·처리 사유·받은 신고 수/사유). 사진은 보관하지 않는다(관리자 삭제 시점에 이미 지워지고,
 *       탈퇴 때 남은 사진도 지금처럼 모두 지운다). 위치·기간, 위반이 아닌 제보, 이 회원이 단 신고, 닉네임·이메일·Apple 토큰도 남기지 않는다.</li>
 *   <li><b>얼마나</b>: 마지막 탈퇴로부터 1년. {@link #purgeExpired()} 가 매일 만료된 행을 지운다.</li>
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

    /**
     * "운영진이 위반으로 확정한 제보" = 관리자가 검토(PATCH /admin/reports/{id})해 삭제(DELETED)한 제보만(운영자 결정 2026-10-04).
     * 관리자만 만들 수 있는 상태다(본인 삭제는 행을 지우고, 신고 누적은 HIDDEN 으로만 간다). 반려(REJECTED)는 "중복 제보"·"캠퍼스 밖" 같은
     * 단순 반려가 섞여 넣지 않고, 숨김(HIDDEN)은 자동 숨김과 구분되지 않는 "재검토 대기"라 넣지 않는다.
     * 쿼리에서 reviewed_at 이 있는(관리자 처리) 것만 본다.
     */
    public static final Set<ReportStatus> CONFIRMED_VIOLATION_STATUSES = EnumSet.of(ReportStatus.DELETED);

    /** 스냅숏에 남기는 제보 본문 길이 */
    public static final int CONTENT_SUMMARY_LENGTH = 200;

    private final WithdrawRetentionRepository repository;
    private final WithdrawRetentionKeys keys;
    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    /**
     * 회원탈퇴(UserService.withdraw) 안에서, 제보·신고를 지우기 <b>전에</b> 부른다(같은 트랜잭션 — 탈퇴가 롤백되면 기록도 없다).
     * 사진은 보관하지 않는다 — 호출한 쪽은 지금처럼 모든 제보 사진을 커밋 뒤 지운다.
     *
     * @return 기록을 남겼으면(새로 만들었거나 갱신) true
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

        // 운영진이 위반으로 확정한(관리자 삭제) 제보만. 신고만 받았거나 반려된 제보는 대상이 아니다. 일반 회원은 보통 0건이라 여기서 끝난다.
        List<Report> violations = reportRepository.findConfirmedViolationsByUserId(
                user.getId(), CONFIRMED_VIOLATION_STATUSES);
        boolean suspensionHistory = user.isSuspended() || user.getSuspendedAt() != null;
        if (!suspensionHistory && violations.isEmpty() && !hasActiveRecord) {
            return false;
        }

        Map<Long, List<String>> reasonsByReport = new LinkedHashMap<>();
        if (!violations.isEmpty()) {
            for (Object[] row : reportFlagRepository.findReasonsOnReportsOfUser(user.getId())) {
                reasonsByReport.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
            }
        }
        List<RetentionSnapshot.ViolationReport> entries = new ArrayList<>();
        for (Report report : violations) {
            List<String> reasons = reasonsByReport.getOrDefault(report.getId(), List.of());
            entries.add(new RetentionSnapshot.ViolationReport(
                    report.getId(), report.getCategory().name(), report.getCustomCategoryLabel(),
                    report.getTitle(), summarize(report.getContent()), report.getStatus().name(), report.getCreatedAt(),
                    report.getModerationNote(), reasons.size(), List.copyOf(reasons)));
        }

        WithdrawRetention record = existing.orElseGet(() -> new WithdrawRetention(user.getSocialType(), hash));
        List<RetentionSnapshot.Withdrawal> withdrawals = new ArrayList<>();
        if (hasActiveRecord) {
            withdrawals.addAll(readSnapshot(record).withdrawals());
        } else if (existing.isPresent()) {
            // 만료됐지만 아직 정리 전인 행 — 지난 이력은 버린다(기한이 지났다).
            record.resetExpired();
        }
        withdrawals.add(new RetentionSnapshot.Withdrawal(now, user.getStatus().name(), user.getSuspendedReason(),
                user.getSuspendedAt(), entries));

        record.recordWithdrawal(user.isSuspended(), user.getSuspendedReason(), user.getSuspendedAt(), entries.size(),
                jsonMapper.writeValueAsString(new RetentionSnapshot(RetentionSnapshot.VERSION, withdrawals)),
                now, now.plus(RETENTION_PERIOD));
        repository.save(record);
        log.info("withdraw retention recorded userId={} retentionId={} suspended={} violationReports={} renewed={}",
                user.getId(), record.getId(), user.isSuspended(), entries.size(), hasActiveRecord);
        return true;
    }

    /** 본문 요약 — 앞 {@link #CONTENT_SUMMARY_LENGTH}자만 남긴다(최소 보관). */
    static String summarize(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= CONTENT_SUMMARY_LENGTH ? trimmed : trimmed.substring(0, CONTENT_SUMMARY_LENGTH) + "…";
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

    /**
     * 보관 기한(retain_until)이 지난 기록을 지운다. 매일 1회(app.withdraw-retention.purge-cron, 기본 03:40 UTC). 한 번에 최대 200건.
     * 사진은 보관하지 않으므로 지울 S3 객체도 없다.
     *
     * @return 지운 기록 수
     */
    @Scheduled(cron = "${app.withdraw-retention.purge-cron:0 40 3 * * *}")
    @Transactional
    public int purgeExpired() {
        List<WithdrawRetention> expired = repository.findTop200ByRetainUntilLessThanEqualOrderByIdAsc(now());
        repository.deleteAll(expired);
        if (!expired.isEmpty()) {
            log.info("withdraw retention purged {} records", expired.size());
        }
        return expired.size();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
