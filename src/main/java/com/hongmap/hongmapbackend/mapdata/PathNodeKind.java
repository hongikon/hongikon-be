package com.hongmap.hongmapbackend.mapdata;

/** 경로망 점 종류. path_nodes.kind 에 이름 그대로 저장한다. */
public enum PathNodeKind {
    /** 경로 중간점 — 좌표를 저장한다. */
    WAYPOINT,
    /** 건물 출입구 참조 — (building, entranceLabel) 로 buildings.entrances 의 출입구를 가리킨다. 좌표는 저장하지 않는다. */
    ENTRANCE
}
