package com.hongmap.hongmapbackend.department.dto;

import com.hongmap.hongmapbackend.department.UserDepartment;
import lombok.Builder;

@Builder
public record UserDepartmentResponse(
        Long id,
        Long departmentId,
        String departmentName,
        boolean isPrimary
) {
    public static UserDepartmentResponse of(UserDepartment ud) {
        return UserDepartmentResponse.builder()
                .id(ud.getId())
                .departmentId(ud.getDepartment().getId())
                .departmentName(ud.getDepartment().getName())
                .isPrimary(ud.isPrimary())
                .build();
    }
}
