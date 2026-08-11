package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.department.dto.DepartmentListResponse;
import com.hongmap.hongmapbackend.department.dto.DepartmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public DepartmentListResponse getAll() {
        var departments = departmentRepository.findAll().stream()
                .map(DepartmentResponse::of)
                .toList();
        return new DepartmentListResponse(departments);
    }
}
