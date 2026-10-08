package com.hongmap.hongmapbackend.mapdata.dto;

/** 소속마다 다른 혜택(예외만). 여기 없는 소속은 업체 기본 혜택(benefit)을 따른다. */
public record MapAffiliationBenefit(String affiliation, String benefit) {
}
