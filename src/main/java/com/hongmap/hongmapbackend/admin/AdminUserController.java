package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminUserListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserPriorHistoryResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserResponse;
import com.hongmap.hongmapbackend.admin.dto.UserSuspendRequest;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회원 조회·이용 정지. ADMIN 전용 — SecurityConfig 의 /admin/** 규칙. */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 조회", description = "q: 회원 id(숫자) 또는 닉네임 일부. 비우면 정지된 회원 목록.")
    @GetMapping
    public AdminUserListResponse search(@RequestParam(required = false) String q) {
        return adminUserService.search(q);
    }

    @Operation(summary = "회원 한 명 조회")
    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @Operation(summary = "재가입 회원의 탈퇴 전 이력",
            description = "정지·신고 이력으로 탈퇴 기록(1년 보관)이 남은 계정이 다시 가입한 경우의 스냅숏(제보·받은 신고·단 신고)과 "
                    + "보관 사진 보기 URL. 기록이 없으면 404.")
    @GetMapping("/{id}/prior-history")
    public AdminUserPriorHistoryResponse priorHistory(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        AdminUserPriorHistoryResponse response = adminUserService.priorHistory(id);
        log.info("탈퇴 전 이력 조회: adminId={}, userId={}", adminId, id);
        return response;
    }

    @Operation(summary = "이용 정지", description = "정지된 회원은 로그인·조회는 되지만 제보·신고·문의·닉네임 변경이 403 으로 막힙니다. 사유 필수.")
    @PostMapping("/{id}/suspend")
    public AdminUserResponse suspend(@AuthenticationPrincipal Long adminId, @PathVariable Long id,
                                     @Valid @RequestBody UserSuspendRequest request) {
        AdminUserResponse response = adminUserService.suspend(id, request.reason());
        log.info("회원 정지: adminId={}, userId={}, reason={}", adminId, id, request.reason());
        return response;
    }

    @Operation(summary = "관리자 지정", description = "정지된 회원은 지정할 수 없습니다.")
    @PostMapping("/{id}/grant-admin")
    public AdminUserResponse grantAdmin(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        AdminUserResponse response = adminUserService.grantAdmin(id);
        log.info("관리자 지정: adminId={}, userId={}", adminId, id);
        return response;
    }

    @Operation(summary = "관리자 해제", description = "자기 자신은 해제할 수 없습니다(관리자가 0명이 되지 않게).")
    @PostMapping("/{id}/revoke-admin")
    public AdminUserResponse revokeAdmin(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        AdminUserResponse response = adminUserService.revokeAdmin(adminId, id);
        log.info("관리자 해제: adminId={}, userId={}", adminId, id);
        return response;
    }

    @Operation(summary = "이용 정지 해제")
    @PostMapping("/{id}/unsuspend")
    public AdminUserResponse unsuspend(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        AdminUserResponse response = adminUserService.unsuspend(id);
        log.info("회원 정지 해제: adminId={}, userId={}", adminId, id);
        return response;
    }
}
