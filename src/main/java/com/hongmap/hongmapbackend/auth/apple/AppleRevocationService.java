package com.hongmap.hongmapbackend.auth.apple;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 탈퇴한 Apple 사용자의 토큰 폐기(App Store 가이드라인 5.1.1(v)).
 * <ul>
 *   <li>{@link #revokeOrQueue}: 탈퇴 커밋 뒤 바로 폐기한다. Apple 호출이 실패(FAILED)하면 {@code apple_pending_revocations} 에
 *       암호화해 넣는다. 설정·토큰이 없어 건너뛴(SKIPPED) 건은 다시 해도 같으므로 넣지 않는다(prod 는 키가 없으면 기동 안 함).</li>
 *   <li>{@link #retryPending}: 매시간 대기열을 다시 시도. 성공하면 지우고, {@value #MAX_ATTEMPTS}번(약 3일) 실패하면
 *       ERROR 로그를 남기고 지운다(탈퇴자 토큰을 무기한 보관하지 않음 — 이때는 Apple 개발자 콘솔에서 수동 확인).</li>
 * </ul>
 */
@Slf4j
@Service
public class AppleRevocationService {

    static final int MAX_ATTEMPTS = 72;
    private static final Duration RETRY_INTERVAL = Duration.ofHours(1);

    private final AppleAuthClient appleAuthClient;
    private final PendingAppleRevocationRepository repository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    @Autowired
    public AppleRevocationService(AppleAuthClient appleAuthClient, PendingAppleRevocationRepository repository,
                                  PlatformTransactionManager transactionManager) {
        this(appleAuthClient, repository, transactionManager, Clock.systemDefaultZone());
    }

    AppleRevocationService(AppleAuthClient appleAuthClient, PendingAppleRevocationRepository repository,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.appleAuthClient = appleAuthClient;
        this.repository = repository;
        // afterCommit 안에서 불려도 원래 트랜잭션에 묻히지 않도록 항상 새 트랜잭션으로 저장한다.
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** 탈퇴 커밋 뒤에 부른다. 예외를 던지지 않는다. */
    public AppleAuthClient.RevokeResult revokeOrQueue(String refreshToken, String clientId) {
        AppleAuthClient.RevokeResult result = appleAuthClient.revoke(refreshToken, clientId);
        if (result == AppleAuthClient.RevokeResult.FAILED) {
            try {
                LocalDateTime now = LocalDateTime.now(clock);
                requiresNew.executeWithoutResult(status ->
                        repository.save(new PendingAppleRevocation(refreshToken, clientId, now, now.plus(RETRY_INTERVAL))));
            } catch (RuntimeException e) {
                // 대기열 저장마저 실패하면 로그가 유일한 기록이다(토큰 원문은 남기지 않는다).
                log.error("Apple 토큰 폐기 실패 건을 재시도 대기열에 넣지 못했습니다 clientId={} — 수동 확인 필요", clientId, e);
            }
        }
        return result;
    }

    @Scheduled(cron = "${app.apple.revocation-retry-cron:0 17 * * * *}")
    public void retryPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PendingAppleRevocation> due = repository.findTop50ByNextAttemptAtLessThanEqualOrderByIdAsc(now);
        for (PendingAppleRevocation pending : due) {
            AppleAuthClient.RevokeResult result = appleAuthClient.revoke(pending.getRefreshToken(), pending.getClientId());
            requiresNew.executeWithoutResult(status -> {
                if (result == AppleAuthClient.RevokeResult.REVOKED) {
                    repository.deleteById(pending.getId());
                    log.info("Apple 토큰 폐기 재시도 성공 id={} (시도 {}회)", pending.getId(), pending.getAttempts() + 1);
                } else if (result == AppleAuthClient.RevokeResult.SKIPPED || pending.getAttempts() + 1 >= MAX_ATTEMPTS) {
                    repository.deleteById(pending.getId());
                    log.error("Apple 토큰 폐기를 포기합니다 id={} clientId={} 결과={} 시도={}회 — Apple 개발자 콘솔에서 수동 확인 필요",
                            pending.getId(), pending.getClientId(), result, pending.getAttempts() + 1);
                } else {
                    pending.failedAgain(now.plus(RETRY_INTERVAL));
                    repository.save(pending);
                }
            });
        }
    }
}
