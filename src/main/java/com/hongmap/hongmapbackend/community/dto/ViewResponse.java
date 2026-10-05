package com.hongmap.hongmapbackend.community.dto;

/**
 * 조회 기록 결과.
 *
 * @param viewCount 이 제보의 조회 수(사람·기기마다 하루 한 번)
 * @param counted   이번 요청으로 1 올랐는지(오늘 이미 봤거나 식별값이 없으면 false)
 */
public record ViewResponse(long viewCount, boolean counted) {
}
