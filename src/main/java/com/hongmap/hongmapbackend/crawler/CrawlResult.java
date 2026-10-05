package com.hongmap.hongmapbackend.crawler;

import java.util.List;

/**
 * 크롤링 1회 실행 요약 — 관리자 대시보드(CrawlerRunTracker 스냅샷)와 운영 로그용.
 *
 * @param savedCount     새로 저장한 소식 수
 * @param boardCount     대상 게시판 수
 * @param failedBoards   이번에 실패한 게시판(표시용 출처명)
 * @param skippedBoards  연속 실패로 이번엔 건너뛴 게시판(CrawlerBoardCircuitBreaker)
 * @param emptyBoards    첫 페이지 목록이 0건이었던 게시판(비었거나 URL·마크업이 바뀐 경우 — 운영자 확인용)
 * @param requestCount   학교 서버로 보낸 HTTP 요청 수(목록+상세, 재시도 포함)
 * @param durationMs     실행 시간(ms, 푸시 발송 포함)
 */
public record CrawlResult(
        int savedCount,
        int boardCount,
        List<String> failedBoards,
        List<String> skippedBoards,
        List<String> emptyBoards,
        long requestCount,
        long durationMs
) {
}
