package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.department.dto.DepartmentListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "전체 학과 목록 조회", description = "홍익대학교 전체 학과 목록을 조회합니다.")
    @GetMapping("/departments")
    public DepartmentListResponse getAll() {
        return departmentService.getAll();
    }
}
