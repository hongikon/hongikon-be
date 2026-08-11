package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.department.dto.DepartmentListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학과 목록은 회원가입/온보딩 화면에서도 필요할 수 있어 게스트 허용.
 * SecurityConfig의 permitAll()에 GET /departments 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping("/departments")
    public DepartmentListResponse getAll() {
        return departmentService.getAll();
    }
}
