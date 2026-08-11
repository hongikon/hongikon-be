package com.hongmap.hongmapbackend.department.dto;

import java.util.List;

public record DepartmentListResponse(
        List<DepartmentResponse> departments
) {
}
