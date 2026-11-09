package com.hongmap.hongmapbackend.walkroute;

/** 두 좌표 사이의 대권 거리. 앱(src/utils/geo.ts)과 같은 값이 나오게 fdlibm(StrictMath)으로 계산한다 — V8 의 Math.sin·cos·atan2 도 fdlibm 이다. */
final class Geo {

    private static final double EARTH_RADIUS_METERS = 6371000;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180;

    private Geo() {
    }

    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = (lat2 - lat1) * DEGREES_TO_RADIANS;
        double dLng = (lng2 - lng1) * DEGREES_TO_RADIANS;
        double sLat = StrictMath.sin(dLat / 2);
        double sLng = StrictMath.sin(dLng / 2);
        double a = sLat * sLat
                + StrictMath.cos(lat1 * DEGREES_TO_RADIANS) * StrictMath.cos(lat2 * DEGREES_TO_RADIANS) * (sLng * sLng);
        return EARTH_RADIUS_METERS * 2 * StrictMath.atan2(StrictMath.sqrt(a), StrictMath.sqrt(1 - a));
    }
}
