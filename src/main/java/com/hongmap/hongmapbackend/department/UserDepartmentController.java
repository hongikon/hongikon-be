package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.department.dto.UserDepartmentCreateRequest;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentListResponse;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentResponse;
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

    @GetMapping("/users/me/departments")
    public UserDepartmentListResponse getMyDepartments(@AuthenticationPrincipal Long userId) {
        return userDepartmentService.getMyDepartments(userId);
    }

    @PostMapping("/users/me/departments")
    public ResponseEntity<UserDepartmentResponse> add(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDepartmentCreateRequest request
    ) {
        var response = userDepartmentService.add(userId, request.departmentId(), request.isPrimary());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/users/me/departments/{departmentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long departmentId
    ) {
        userDepartmentService.delete(userId, departmentId);
        return ResponseEntity.noContent().build();
    }
}
