package com.hongmap.hongmapbackend.building.dto;

import com.hongmap.hongmapbackend.building.Building;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BuildingResponse(
        Long id,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String color,
        String mapCategory,
        String type,
        Integer floors,
        Integer basementFloors,
        String hours,
        String description,
        String contact,
        String facilities,
        String linkLabel,
        String linkUrl,
        String boundary
) {
    public static BuildingResponse of(Building b) {
        return BuildingResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .latitude(b.getLatitude())
                .longitude(b.getLongitude())
                .color(b.getColor())
                .mapCategory(b.getMapCategory())
                .type(b.getType())
                .floors(b.getFloors())
                .basementFloors(b.getBasementFloors())
                .hours(b.getHours())
                .description(b.getDescription())
                .contact(b.getContact())
                .facilities(b.getFacilities())
                .linkLabel(b.getLinkLabel())
                .linkUrl(b.getLinkUrl())
                .boundary(b.getBoundary())
                .build();
    }
}
