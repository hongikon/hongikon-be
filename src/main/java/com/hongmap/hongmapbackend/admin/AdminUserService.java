package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminUserListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserPriorHistory;
import com.hongmap.hongmapbackend.admin.dto.AdminUserPriorHistoryResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import com.hongmap.hongmapbackend.user.UserStatus;
import com.hongmap.hongmapbackend.user.UserSuspensionChangedEvent;
import com.hongmap.hongmapbackend.user.retention.RetentionSnapshot;
import com.hongmap.hongmapbackend.user.retention.WithdrawRetention;
import com.hongmap.hongmapbackend.user.retention.WithdrawRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 회원 조회와 이용 정지·해제(약관 제10조, App Store 가이드라인 1.2). */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final WithdrawRetentionService withdrawRetentionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * q 가 숫자면 회원 id 로, 아니면 닉네임·앱 닉네임 일부로 찾는다. q 가 비어 있으면 정지된 회원 목록.
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse search(String q) {
        List<User> users;
        if (q == null || q.isBlank()) {
            users = userRepository.findTop200ByStatusOrderBySuspendedAtDesc(UserStatus.SUSPENDED);
        } else if (q.trim().matches("\\d{1,18}")) {
            users = userRepository.findById(Long.parseLong(q.trim())).map(List::of).orElse(List.of());
        } else {
            users = userRepository.findTop50ByNicknameContainingOrAppNicknameContainingOrderByIdDesc(q.trim(), q.trim());
        }
        Map<Long, WithdrawRetention> priorById =
                withdrawRetentionService.findActiveForUsers(users.stream().map(User::getId).toList());
        return new AdminUserListResponse(users.stream()
                .map(user -> AdminUserResponse.of(user, AdminUserPriorHistory.of(priorById.get(user.getId()))))
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) {
        return toResponse(find(userId));
    }

    /** 이미 정지된 회원이면 사유만 바꾼다. 관리자는 정지할 수 없다(실수로 관리 권한을 잃지 않게). */
    @Transactional
    public AdminUserResponse suspend(Long userId, String reason) {
        User user = find(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "관리자 계정은 정지할 수 없습니다.");
        }
        user.suspend(reason.trim());
        // 커밋 뒤 본인에게 사유·이의 제기 안내 푸시(AccountStatusPushDispatcher). 실패해도 정지는 그대로.
        eventPublisher.publishEvent(new UserSuspensionChangedEvent(user.getId(), true, user.getSuspendedReason()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse unsuspend(Long userId) {
        User user = find(userId);
        boolean wasSuspended = user.isSuspended();
        user.unsuspend();
        if (wasSuspended) { // 정지 중이 아니었으면 알릴 것이 없다
            eventPublisher.publishEvent(new UserSuspensionChangedEvent(user.getId(), false, null));
        }
        return toResponse(user);
    }

    /** 관리자로 지정한다. 정지된 회원은 먼저 해제해야 한다. 이미 관리자면 그대로 돌려준다. */
    @Transactional
    public AdminUserResponse grantAdmin(Long userId) {
        User user = find(userId);
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정지된 회원은 관리자로 지정할 수 없어요. 먼저 정지를 해제해 주세요.");
        }
        user.changeRole(UserRole.ADMIN);
        return toResponse(user);
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
            return toResponse(user);
        }
        user.changeRole(UserRole.USER);
        return toResponse(user);
    }

    /**
     * 재가입 회원의 탈퇴 전 기록 전체(스냅숏). 보관 중인 기록이 없으면 404.
     * 회원이 지금 존재하지 않아도 404(탈퇴한 회원 자체의 기록은 회원 id 로 찾을 수 없다 — 소셜 id 해시로만 묶인다).
     */
    @Transactional(readOnly = true)
    public AdminUserPriorHistoryResponse priorHistory(Long userId) {
        find(userId);
        WithdrawRetention record = withdrawRetentionService.findActiveForUser(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "탈퇴 전 이력이 없는 회원입니다."));
        RetentionSnapshot snapshot = withdrawRetentionService.readSnapshot(record);
        return new AdminUserPriorHistoryResponse(userId, AdminUserPriorHistory.of(record), snapshot.withdrawals());
    }

    private AdminUserResponse toResponse(User user) {
        return AdminUserResponse.of(user,
                withdrawRetentionService.findActiveForUser(user.getId()).map(AdminUserPriorHistory::of).orElse(null));
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
    }
}
