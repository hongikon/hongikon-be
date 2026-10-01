package com.hongmap.hongmapbackend.notification;

/**
 * 새 제보 알림을 받을 범위. 지금은 CAMPUS(캠퍼스 전체)만 있다.
 * 앱은 기기 위치(GPS)를 수집하지 않으므로 "근처"는 위치 대신 범위 설정으로 좁힌다 —
 * 나중에 BUILDINGS(관심 건물만) 등을 추가하면 ReportPushDispatcher의 대상 조회에 조건을 더하면 된다.
 */
public enum NewReportScope {
    CAMPUS
}
