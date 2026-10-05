package com.hongmap.hongmapbackend.report.dto;

/**
 * @param total   내 제보 전체 개수(DELETED 포함)
 * @param pending 승인 대기 개수
 */
public record MyReportCountResponse(long total, long pending) {
}
