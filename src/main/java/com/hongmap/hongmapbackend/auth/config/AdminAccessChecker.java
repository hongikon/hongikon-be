package com.hongmap.hongmapbackend.auth.config;

import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * /admin/**, /crawler/** 접근 판정. 역할을 JWT에 싣지 않고 요청마다 DB에서 확인한다 —
 * 관리자 해제가 토큰 만료(30분)를 기다리지 않고 즉시 반영되고, 기존 토큰 형식도 그대로 둘 수 있다.
 * 관리자 요청은 드물어 조회 한 번의 비용은 문제되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessChecker {

    private final UserRepository userRepository;

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return false;
        }
        return userRepository.findRoleById(userId)
                .map(role -> role == UserRole.ADMIN)
                .orElse(false);
    }
}
