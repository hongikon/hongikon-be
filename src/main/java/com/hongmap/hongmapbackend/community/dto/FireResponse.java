package com.hongmap.hongmapbackend.community.dto;

/**
 * 🔥 누르기·끄기 결과.
 *
 * @param fired           지금 내가 🔥를 누른 상태인지
 * @param fireCount       이 제보의 🔥 수
 * @param recentFireCount 최근 report.hot.window-minutes(기본 60분) 안에 눌린 🔥 수
 * @param hot             recentFireCount 가 report.hot.threshold(기본 5) 이상인지
 */
public record FireResponse(boolean fired, long fireCount, long recentFireCount, boolean hot) {
}
