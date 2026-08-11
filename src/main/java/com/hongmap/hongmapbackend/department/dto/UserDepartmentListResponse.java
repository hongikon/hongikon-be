package com.hongmap.hongmapbackend.department.dto;

import java.util.List;

public record UserDepartmentListResponse(
        List<UserDepartmentResponse> departments
) {
}
