package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminUserListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import com.hongmap.hongmapbackend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 회원 조회와 이용 정지·해제(약관 제10조, App Store 가이드라인 1.2). */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * q 가 숫자면 회원 id 로, 아니면 닉네임 일부로 찾는다. q 가 비어 있으면 정지된 회원 목록.
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse search(String q) {
        List<User> users;
        if (q == null || q.isBlank()) {
            users = userRepository.findTop200ByStatusOrderBySuspendedAtDesc(UserStatus.SUSPENDED);
        } else if (q.trim().matches("\\d{1,18}")) {
            users = userRepository.findById(Long.parseLong(q.trim())).map(List::of).orElse(List.of());
        } else {
            users = userRepository.findTop50ByNicknameContainingOrderByIdDesc(q.trim());
        }
        return new AdminUserListResponse(users.stream().map(AdminUserResponse::of).toList());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) {
        return AdminUserResponse.of(find(userId));
    }

    /** 이미 정지된 회원이면 사유만 바꾼다. 관리자는 정지할 수 없다(실수로 관리 권한을 잃지 않게). */
    @Transactional
    public AdminUserResponse suspend(Long userId, String reason) {
        User user = find(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "관리자 계정은 정지할 수 없습니다.");
        }
        user.suspend(reason.trim());
        return AdminUserResponse.of(user);
    }

    @Transactional
    public AdminUserResponse unsuspend(Long userId) {
        User user = find(userId);
        user.unsuspend();
        return AdminUserResponse.of(user);
    }

    /** 관리자로 지정한다. 정지된 회원은 먼저 해제해야 한다. 이미 관리자면 그대로 돌려준다. */
    @Transactional
    public AdminUserResponse grantAdmin(Long userId) {
        User user = find(userId);
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정지된 회원은 관리자로 지정할 수 없어요. 먼저 정지를 해제해 주세요.");
        }
        user.changeRole(UserRole.ADMIN);
        return AdminUserResponse.of(user);
    }

    /**
     * 관리자 권한을 해제한다. 자기 자신은 해제할 수 없다 — 실수로 콘솔에서 쫓겨나지 않게, 그리고 요청한 관리자가 남으니
     * 관리자가 0명이 되는 일도 없다(0명이면 다시 지정할 방법이 SQL 뿐이다).
     */
    @Transactional
    public AdminUserResponse revokeAdmin(Long adminId, Long userId) {
        if (userId.equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신의 관리자 권한은 해제할 수 없어요.");
        }
        User user = find(userId);
        if (user.getRole() != UserRole.ADMIN) {
            return AdminUserResponse.of(user);
        }
        user.changeRole(UserRole.USER);
        return AdminUserResponse.of(user);
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
    }
}
