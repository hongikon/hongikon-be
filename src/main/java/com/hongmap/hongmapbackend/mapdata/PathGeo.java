package com.hongmap.hongmapbackend.mapdata;

/** 경로망 좌표 계산. */
public final class PathGeo {

    /** 지구 평균 반지름(m). */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private PathGeo() {
    }

    public record Point(double lat, double lng) {
    }

    /** 두 점 사이 거리(m, haversine). 캠퍼스 범위에서는 오차가 무시할 만하다. */
    public static double distanceMeters(Point a, Point b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** 소수 첫째 자리까지(응답용). */
    public static double roundMeters(double meters) {
        return Math.round(meters * 10) / 10.0;
    }
}
