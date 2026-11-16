package com.hongmap.hongmapbackend.walkroute;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 길찾기 후보 하나 — 앱의 RouteAlternative 와 같은 모양(null 필드는 생략).
 * 직선 어림(isEstimate)에는 usesElevator 아래 값들이 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalkRoute(
        List<Point> points,
        long distanceMeters,
        double totalSeconds,
        long minutes,
        boolean hasIndoorTransit,
        /* 경로에 계단 구간(경로 조사의 계단 선)이 있다. */
        boolean hasStairs,
        /* 경로망에 아직 없는 건물이라 출입구에서 가장 가까운 길까지 직선으로 이은 구간이 있다. */
        boolean hasLinkSegment,
        /* 실측 경로망이 이 구간을 덮지 못해 직선 거리로 대신한 값이다. */
        boolean isEstimate,
        Boolean usesElevator,
        Long indoorMeters,
        /* 출발·도착 말고 안으로 지나가는 건물 이름(지나는 순서). */
        List<String> viaBuildings,
        /* 지나는 곳의 조사 메모(지나는 순서, 읽기 쉽게 고친 것). */
        List<String> notes,
        /* 길 흐름: "R동 1층 출구", "Z2동 2층 → 3층 통과", "엘리베이터로 5층 올라가기". */
        List<String> steps,
        /* 건물 안 구간. 지도에서 점선으로 겹쳐 그린다. */
        List<List<Point>> indoorSegments,
        /* 지도에 찍는 층 바뀌는 곳(11-16): 건물 출구·입구, 건물 안 계단·엘리베이터 층 이동, 바깥 계단. 지나는 순서. */
        List<Marker> markers
) {
    public record Point(double lat, double lng) {
    }

    /**
     * kind: exit(건물 나가기)·enter(들어가기)·pass(바로 지나가기)·floor(건물 안 층 이동)·stairs(바깥 계단).
     * label: 지도 말풍선 글('T동 3층 입구', '엘리베이터 5층 ↑', '계단 ↓'). floor: 그 자리의 층(모르면 null).
     */
    public record Marker(double lat, double lng, String kind, String label, Double floor) {
    }
}
