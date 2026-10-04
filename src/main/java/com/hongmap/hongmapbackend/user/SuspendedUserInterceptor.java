package com.hongmap.hongmapbackend.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

/**
 * 정지된(SUSPENDED) 사용자의 쓰기 요청을 403 으로 막는다. 대상 경로는 SuspendedUserWebConfig 에서 정한다
 * (제보 등록·사진 업로드·신고, 문의, 닉네임 변경). 조회·삭제와 로그인·탈퇴는 막지 않는다.
 * 서비스마다 검사를 넣지 않고 경로로 막아, 나중에 생기는 쓰기 API 도 경로만 더하면 된다.
 */
@Component
@RequiredArgsConstructor
public class SuspendedUserInterceptor implements HandlerInterceptor {

    public static final String SUSPENDED_MESSAGE =
            "운영 정책 위반으로 이용이 정지된 계정이에요. 제보·신고·문의를 할 수 없어요. 문의: hongikonsupport@gmail.com";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        // 만들기·바꾸기(POST/PUT/PATCH)만 막는다. 지우기(닉네임 지우기 등)는 정지 중에도 허용.
        if (!HttpMethod.POST.matches(method) && !HttpMethod.PUT.matches(method) && !HttpMethod.PATCH.matches(method)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return true; // 게스트 문의 등은 그대로 둔다(인증 필요 여부는 SecurityConfig 가 판단).
        }
        boolean suspended = userRepository.findStatusById(userId)
                .map(status -> status == UserStatus.SUSPENDED)
                .orElse(false);
        if (suspended) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SUSPENDED_MESSAGE);
        }
        return true;
    }
}
