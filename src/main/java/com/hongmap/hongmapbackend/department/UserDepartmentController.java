package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentCreateRequest;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentListResponse;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserDepartmentController {

    private final UserDepartmentService userDepartmentService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 학과 목록 조회", description = "로그인한 사용자가 등록한 학과(소속) 목록을 조회합니다.")
    @GetMapping("/users/me/departments")
    public UserDepartmentListResponse getMyDepartments(@AuthenticationPrincipal Long userId) {
        return userDepartmentService.getMyDepartments(userId);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 학과 추가", description = "로그인한 사용자의 소속 학과를 추가합니다.")
    @PostMapping("/users/me/departments")
    public ResponseEntity<UserDepartmentResponse> add(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDepartmentCreateRequest request
    ) {
        var response = userDepartmentService.add(userId, request.departmentId(), request.isPrimary());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 학과 삭제", description = "로그인한 사용자의 소속 학과를 삭제합니다.")
    @DeleteMapping("/users/me/departments/{departmentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long departmentId
    ) {
        userDepartmentService.delete(userId, departmentId);
        return ResponseEntity.noContent().build();
    }
}
