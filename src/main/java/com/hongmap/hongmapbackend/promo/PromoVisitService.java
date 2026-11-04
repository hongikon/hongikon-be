package com.hongmap.hongmapbackend.promo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 홍보 링크 방문 수 세기. 경로(source)·날짜(KST)별 숫자 하나만 올린다 — 누가 왔는지는 남기지 않는다.
 * 경로는 promo.sources 에 적은 것만 센다(아무 이름이나 넣어 행을 늘리지 못하게).
 * 앱 받기 안내 페이지(hongikon.com/app — 포스터 QR 이 가리키는 곳) 자체의 방문은 {@link #APP_PAGE} 로 따로 센다(11-05):
 * QR·직접 입력·홍보 링크를 거쳐 온 것을 모두 합친 페이지 방문이다. 홍보 링크는 그중 어느 경로로 왔는지를 센다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoVisitService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 카카오톡·슬랙 등이 링크 미리보기를 만들려고 먼저 한 번 열어 본다. 그건 방문이 아니다.
    private static final Pattern BOT_UA = Pattern.compile(
            "bot|crawl|spider|scrap|preview|facebookexternalhit|curl|wget|python|java/|okhttp",
            Pattern.CASE_INSENSITIVE);

    /** 앱 받기 안내 페이지 방문 이름. 이동(`record`)으로는 세지 않는다 — 페이지가 열리면 `recordPageView` 로 한 번 센다. */
    public static final String APP_PAGE = "app";

    private final JdbcTemplate jdbcTemplate;

    @Value("${promo.sources:club}")
    private String sourcesProperty;

    public boolean isKnownSource(String source) {
        return allowedSources().contains(source);
    }

    /** 사람으로 보이는 방문만 센다. 실패해도(테이블 없음 등) 이동은 막지 않는다. */
    public void record(String source, String userAgent) {
        if (APP_PAGE.equals(source) || !isKnownSource(source)) {
            return;
        }
        increment(source, userAgent);
    }

    /** 앱 받기 안내 페이지가 열렸다(페이지 안 보이지 않는 이미지 요청). {@link #APP_PAGE} 만 센다. */
    public void recordPageView(String source, String userAgent) {
        if (APP_PAGE.equals(source)) {
            increment(source, userAgent);
        }
    }

    private void increment(String source, String userAgent) {
        if (userAgent == null || userAgent.isBlank() || BOT_UA.matcher(userAgent).find()) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO promo_visits (source, visit_date, visits) VALUES (?, ?, 1) "
                            + "ON DUPLICATE KEY UPDATE visits = visits + 1",
                    source, LocalDate.now(KST));
        } catch (RuntimeException e) {
            log.warn("홍보 링크 방문 수 기록 실패(source={}): {}", source, e.getMessage());
        }
    }

    /** 관리자 조회용. 최근 날짜부터. */
    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList(
                "SELECT source, visit_date AS date, visits FROM promo_visits ORDER BY visit_date DESC, source");
    }

    private Set<String> allowedSources() {
        return Arrays.stream(sourcesProperty.split(","))
                .map(String::trim)
                .filter(s -> s.matches("[a-z0-9-]{1,32}"))
                .collect(Collectors.toSet());
    }
}
