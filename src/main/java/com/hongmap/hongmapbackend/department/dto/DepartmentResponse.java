package com.hongmap.hongmapbackend.department.dto;

import com.hongmap.hongmapbackend.department.Department;
import lombok.Builder;

@Builder
public record DepartmentResponse(
        Long id,
        String name,
        String college,
        String kind
) {
    public static DepartmentResponse of(Department d) {
        return DepartmentResponse.builder()
                .id(d.getId())
                .name(d.getName())
                .college(d.getCollege())
                .kind(d.getKind())
                .build();
    }
}
