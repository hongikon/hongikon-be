package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.department.dto.UserDepartmentListResponse;
import com.hongmap.hongmapbackend.department.dto.UserDepartmentResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserDepartmentService {

    private final UserDepartmentRepository userDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDepartmentListResponse getMyDepartments(Long userId) {
        var departments = userDepartmentRepository.findByUser_Id(userId).stream()
                .map(UserDepartmentResponse::of)
                .toList();
        return new UserDepartmentListResponse(departments);
    }

    @Transactional
    public UserDepartmentResponse add(Long userId, Long departmentId, boolean isPrimary) {
        if (userDepartmentRepository.existsByUser_IdAndDepartment_Id(userId, departmentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 구독한 학과입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 학과입니다."));

        // 새로 primary 지정 시, 같은 유저의 기존 primary는 먼저 전부 해제 (같은 트랜잭션)
        if (isPrimary) {
            userDepartmentRepository.clearPrimaryForUser(userId);
        }

        UserDepartment saved = userDepartmentRepository.save(
                UserDepartment.builder()
                        .user(user)
                        .department(department)
                        .isPrimary(isPrimary)
                        .build()
        );

        return UserDepartmentResponse.of(saved);
    }

    @Transactional
    public void delete(Long userId, Long departmentId) {
        UserDepartment userDepartment = userDepartmentRepository
                .findByUser_IdAndDepartment_Id(userId, departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구독하지 않은 학과입니다."));

        userDepartmentRepository.delete(userDepartment);
    }
}
