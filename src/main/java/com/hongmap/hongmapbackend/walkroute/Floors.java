package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.walkroute.WalkRoute.Point;

/**
 * 층 규칙(앱 src/utils/floors.ts). 양수는 지상, 음수는 지하, 0 은 B1 과 1F 사이 로비층 L(홍문관 R동), 1.5 같은 중간층도 있다.
 */
final class Floors {

    private Floors() {
    }

    /** 층을 오르내린 횟수를 셀 수 있는 연속 눈금. 0층이 없어 지상층을 하나씩 내리고, L(0)은 반 층으로 본다. */
    static double levelIndex(double floor) {
        if (floor == 0) {
            return -0.5;
        }
        return floor > 0 ? floor - 1 : floor;
    }

    /**
     * 고를 수 있는 층(앱 buildFloorOptions — 층 다이얼과 같은 값). floorLevels(층 목록)가 있으면 그대로, 없으면 floors 로 지하 최하층~
     * 지상 최상층, 그것도 없으면 출입구가 걸친 층(1.5 같은 중간층 포함), 다 없으면 빈 목록(층을 고를 수 없는 건물).
     */
    static java.util.SortedSet<Double> floorOptions(CampusBuilding building) {
        java.util.SortedSet<Double> values = new java.util.TreeSet<>();
        if (!building.floorLevels().isEmpty()) {
            values.addAll(building.floorLevels());
            return values;
        }
        double lowest;
        double highest;
        if (building.floors() != null && building.floors() > 0) {
            lowest = building.basementFloors() != null && building.basementFloors() != 0 ? -building.basementFloors() : 1;
            highest = building.floors();
        } else if (!building.entrances().isEmpty()) {
            lowest = 1;
            highest = 1;
            for (CampusBuilding.Entrance e : building.entrances()) {
                lowest = Math.min(lowest, e.minFloor());
                highest = Math.max(highest, e.maxFloor());
                for (double bound : new double[]{e.minFloor(), e.maxFloor()}) {
                    if (bound != Math.rint(bound)) {
                        values.add(bound);
                    }
                }
            }
        } else {
            return values;
        }
        for (double level = Math.ceil(lowest); level <= Math.floor(highest); level += 1) {
            if (level != 0) {
                values.add(level);
            }
        }
        return values;
    }

    /** 숫자를 앱(JavaScript)처럼 적는다: 3 → "3", 1.5 → "1.5". */
    static String num(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** 고른 층에서 드나들 출입구. 그 층에 걸친 출입구, 없으면 층 차이가 가장 적은 출입구(같으면 지상 1층에 가까운 쪽). */
    static CampusBuilding.Entrance matchEntrance(CampusBuilding building, Double floor) {
        if (floor == null || building.entrances().isEmpty()) {
            return null;
        }
        for (CampusBuilding.Entrance entrance : building.entrances()) {
            if (floor >= entrance.minFloor() && floor <= entrance.maxFloor()) {
                return entrance;
            }
        }
        CampusBuilding.Entrance best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestGroundDistance = Double.POSITIVE_INFINITY;
        for (CampusBuilding.Entrance entrance : building.entrances()) {
            double entranceFloor = nearestFloorOf(entrance, floor);
            double distance = Math.abs(levelIndex(floor) - levelIndex(entranceFloor));
            double groundDistance = Math.abs(levelIndex(entranceFloor));
            if (distance < bestDistance || (distance == bestDistance && groundDistance < bestGroundDistance)) {
                best = entrance;
                bestDistance = distance;
                bestGroundDistance = groundDistance;
            }
        }
        return best;
    }

    private static double nearestFloorOf(CampusBuilding.Entrance entrance, double floor) {
        return Math.min(Math.max(floor, entrance.minFloor()), entrance.maxFloor());
    }

    /** 고른 층과 그 층이 쓰는 출입구 사이를 오르내리는 시간(초). 층을 안 골랐으면 0. */
    static double entranceTransitSeconds(CampusBuilding building, Double floor) {
        if (floor == null) {
            return 0;
        }
        CampusBuilding.Entrance entrance = matchEntrance(building, floor);
        double entranceFloor = entrance != null ? nearestFloorOf(entrance, floor) : 1;
        return Math.abs(levelIndex(floor) - levelIndex(entranceFloor)) * WalkRouter.FLOOR_TRANSIT_SECONDS;
    }

    /** 경로 시작·끝 좌표: 고른 층의 출입구, 없으면 건물 대표 좌표. */
    static Point resolveEntrancePoint(CampusBuilding building, Double floor) {
        CampusBuilding.Entrance match = matchEntrance(building, floor);
        return match != null ? new Point(match.lat(), match.lng()) : new Point(building.lat(), building.lng());
    }
}
