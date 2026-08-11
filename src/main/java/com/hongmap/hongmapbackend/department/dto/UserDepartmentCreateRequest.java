package com.hongmap.hongmapbackend.department.dto;

import jakarta.validation.constraints.NotNull;

public record UserDepartmentCreateRequest(
        @NotNull
        Long departmentId,

        boolean isPrimary
) {
}
