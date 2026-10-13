package com.hongmap.hongmapbackend.mapdata.dto;

/** 소속 하나의 제휴 종료일(yyyy-MM-dd, 이 날까지). 기한 있는 소속만 내려간다. */
public record MapAffiliationEnd(String affiliation, String endsOn) {
}
