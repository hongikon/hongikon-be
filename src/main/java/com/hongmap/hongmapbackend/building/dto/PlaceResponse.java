package com.hongmap.hongmapbackend.building.dto;

import com.hongmap.hongmapbackend.building.Place;
import lombok.Builder;

import java.time.LocalTime;

@Builder
public record PlaceResponse(
        Long id,
        Long buildingId,
        String category,
        String name,
        Integer floor,
        LocalTime openTime,
        LocalTime closeTime,
        boolean isOvernight,
        String extraInfo
) {
    public static PlaceResponse of(Place p) {
        return PlaceResponse.builder()
                .id(p.getId())
                .buildingId(p.getBuilding().getId())
                .category(p.getCategory())
                .name(p.getName())
                .floor(p.getFloor())
                .openTime(p.getOpenTime())
                .closeTime(p.getCloseTime())
                .isOvernight(p.getIsOvernight())
                .extraInfo(p.getExtraInfo())
                .build();
    }
}
