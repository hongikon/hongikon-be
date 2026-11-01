package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskPublicQuotaTest {

    /** 한국 2026-11-01 10:00. */
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T01:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(now.get(), zone); }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void 계정마다_하루_한도를_넘으면_429() {
        AskPublicQuota quota = new AskPublicQuota(true, 2, 100, 100, Duration.ofMinutes(10), clock);
        quota.acquire(1L);
        quota.acquire(1L);
        assertThatThrownBy(() -> quota.acquire(1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(AskPublicQuota.USER_LIMIT_MESSAGE);
        quota.acquire(2L);
        assertThat(quota.status(1L).remaining()).isZero();
        assertThat(quota.status(2L).remaining()).isEqualTo(1);
    }

    @Test
    void 전체_하루_상한이_차면_모든_계정이_429() {
        AskPublicQuota quota = new AskPublicQuota(true, 10, 2, 100, Duration.ofMinutes(10), clock);
        quota.acquire(1L);
        quota.acquire(2L);
        assertThatThrownBy(() -> quota.acquire(3L)).hasMessageContaining(AskPublicQuota.DAILY_LIMIT_MESSAGE);
        assertThat(quota.status(3L).soldOut()).isTrue();
        assertThat(quota.status(3L).remaining()).isZero();
    }

    @Test
    void 한국_날짜가_바뀌면_다시_센다() {
        AskPublicQuota quota = new AskPublicQuota(true, 1, 1, 100, Duration.ofMinutes(10), clock);
        quota.acquire(1L);
        now.set(Instant.parse("2026-11-01T14:59:00Z")); // 한국 23:59
        assertThatThrownBy(() -> quota.acquire(1L)).isInstanceOf(ResponseStatusException.class);
        now.set(Instant.parse("2026-11-01T15:00:00Z")); // 한국 11-02 00:00
        quota.acquire(1L);
    }

    @Test
    void 실패한_질문은_되돌린다() {
        AskPublicQuota quota = new AskPublicQuota(true, 1, 5, 100, Duration.ofMinutes(10), clock);
        quota.acquire(1L);
        quota.refund(1L);
        quota.acquire(1L);
        assertThat(quota.status(1L).remaining()).isZero();
    }

    @Test
    void 짧은_시간에_몰아치면_429() {
        AskPublicQuota quota = new AskPublicQuota(true, 100, 100, 2, Duration.ofMinutes(10), clock);
        quota.acquire(1L);
        quota.acquire(1L);
        assertThatThrownBy(() -> quota.acquire(1L)).hasMessageContaining(AskPublicQuota.BURST_MESSAGE);
        assertThat(quota.status(1L).remaining()).isEqualTo(98);
    }

    @Test
    void 게스트_상태는_남은_수_0() {
        AskPublicQuota quota = new AskPublicQuota(true, 100, 150, 20, Duration.ofMinutes(10), clock);
        AskPublicQuota.Status status = quota.status(null);
        assertThat(status.loggedIn()).isFalse();
        assertThat(status.remaining()).isZero();
        assertThat(status.limit()).isEqualTo(100);
    }
}
