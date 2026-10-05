package com.hongmap.hongmapbackend.department;

import com.hongmap.hongmapbackend.common.persistence.UniqueConflictRetry;
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
    private final UniqueConflictRetry uniqueConflictRetry;

    @Transactional(readOnly = true)
    public UserDepartmentListResponse getMyDepartments(Long userId) {
        var departments = userDepartmentRepository.findByUser_Id(userId).stream()
                .map(UserDepartmentResponse::of)
                .toList();
        return new UserDepartmentListResponse(departments);
    }

    /**
     * 멱등: 이미 구독한 학과면 그 행을 돌려준다(전엔 409). isPrimary=true 로 다시 오면 그 학과를 주 학과로 바꾼다.
     * 동시 요청이 uq_user_department 에 걸리면 새 트랜잭션에서 한 번 더 시도한다(UniqueConflictRetry 가 트랜잭션을 연다).
     */
    public UserDepartmentResponse add(Long userId, Long departmentId, boolean isPrimary) {
        return uniqueConflictRetry.execute("user-department-add", () -> addOnce(userId, departmentId, isPrimary));
    }

    private UserDepartmentResponse addOnce(Long userId, Long departmentId, boolean isPrimary) {
        var existing = userDepartmentRepository.findFirstByUser_IdAndDepartment_IdOrderByIdAsc(userId, departmentId);
        if (existing.isPresent()) {
            UserDepartment current = existing.get();
            // 이미 주 학과면 그대로 둔다 — clearPrimaryForUser(벌크 UPDATE)는 메모리의 이 엔티티를 바꾸지 않아,
            // 이미 true 인 엔티티에 markPrimary 해도 UPDATE 가 안 나가 DB 에서만 false 로 남기 때문.
            if (isPrimary && !current.isPrimary()) {
                userDepartmentRepository.clearPrimaryForUser(userId);
                current.markPrimary();
            }
            return UserDepartmentResponse.of(current);
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

    /** 유니크 키 전 중복 행이 있어도 오류 없이 그 조합을 전부 지운다. 지운 게 없으면 404. */
    @Transactional
    public void delete(Long userId, Long departmentId) {
        if (userDepartmentRepository.deleteAllByUserIdAndDepartmentId(userId, departmentId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "구독하지 않은 학과입니다.");
        }
    }
}
