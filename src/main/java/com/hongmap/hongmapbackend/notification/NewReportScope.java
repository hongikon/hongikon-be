package com.hongmap.hongmapbackend.notification;

/**
 * 새 제보 알림을 받을 범위.
 * <ul>
 *   <li>CAMPUS — 캠퍼스 전체 새 제보(유저당 30분에 한 번) + 제보 키워드에 걸린 제보(빈도 제한 없음)</li>
 *   <li>KEYWORDS — 제보 키워드(report_keyword_subscriptions)에 걸린 제보만. 일반 새 제보 알림은 받지 않는다.</li>
 * </ul>
 * 앱은 기기 위치(GPS)를 수집하지 않으므로 "근처"는 위치 대신 범위 설정으로 좁힌다 —
 * 나중에 BUILDINGS(관심 건물만) 등을 추가하면 ReportPushDispatcher의 대상 조회에 조건을 더하면 된다.
 */
public enum NewReportScope {
    CAMPUS,
    KEYWORDS
}
