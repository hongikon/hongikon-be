package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminUserListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserPriorHistory;
import com.hongmap.hongmapbackend.admin.dto.AdminUserPriorHistoryResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminUserResponse;
import com.hongmap.hongmapbackend.user.MemberCodes;
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

import java.util.LinkedHashMap;
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
     * q 로 회원을 찾는다. q 가 비어 있으면 정지된 회원 목록. 아래에 해당하는 것을 모두 모아 이 순서로 돌려준다.
     * <ul>
     *   <li>회원 번호(영문·숫자 10자리, 대소문자 무시): "K7Q2M9XA4D", "k7q2m9xa4d"</li>
     *   <li>숫자면 회원 id</li>
     *   <li>숫자가 아니면 앱 닉네임 일부(10글자 앱 닉네임이 회원 번호 형식과 겹칠 수 있어 함께 찾는다)</li>
     * </ul>
     * 로그인(카카오/Apple) 닉네임으로는 찾지 않는다. 실명일 수 있는 값을 검색어로 쓰면 결과 유무만으로 "이 사람이 가입했는지"가
     * 드러나므로, 운영진도 회원 번호·id·앱에 보이는 이름으로만 회원을 가리킨다(개인정보 보호법 제3조 최소 처리).
     * 앱 닉네임이 없는 회원(가린 이름 "홍**")은 회원 번호나 id 로 찾는다 — 신고·문의·제보 화면에 회원 번호가 함께 보인다.
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse search(String q) {
        if (q == null || q.isBlank()) {
            return withPriorHistory(userRepository.findTop200ByStatusOrderBySuspendedAtDesc(UserStatus.SUSPENDED));
        }
        String query = q.trim();
        Map<Long, User> found = new LinkedHashMap<>();
        MemberCodes.parse(query)
                .flatMap(userRepository::findByMemberCode)
                .ifPresent(user -> found.put(user.getId(), user));
        if (query.matches("\\d{1,18}")) {
            userRepository.findById(Long.parseLong(query)).ifPresent(user -> found.putIfAbsent(user.getId(), user));
        } else {
            userRepository.findTop50ByAppNicknameContainingOrderByIdDesc(query)
                    .forEach(user -> found.putIfAbsent(user.getId(), user));
        }
        return withPriorHistory(List.copyOf(found.values()));
    }

    /** 탈퇴 기록이 남은 재가입 회원이면 그 요약을 함께 싣는다. */
    private AdminUserListResponse withPriorHistory(List<User> users) {
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

    /**
     * 공식 계정으로 인증해 공식 이름을 붙인다(학생회 등, 문의로 신청받아 운영진이 확인한 뒤). 이름은 앞뒤 공백을 지우고 2~30자,
     * 다른 계정의 공식 이름과 겹치면 409. 이미 붙어 있으면 새 이름으로 바꾼다.
     */
    @Transactional
    public AdminUserResponse setOfficialName(Long userId, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        int length = name.codePointCount(0, name.length());
        if (length < 2 || length > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공식 이름은 2~30자로 입력해 주세요.");
        }
        User user = find(userId);
        if (userRepository.existsByOfficialNameAndIdNot(name, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 공식 이름을 쓰는 계정이 이미 있습니다.");
        }
        user.changeOfficialName(name);
        return toResponse(user);
    }

    /** 공식 인증을 뗀다(원래 이름으로 돌아간다). */
    @Transactional
    public AdminUserResponse clearOfficialName(Long userId) {
        User user = find(userId);
        user.changeOfficialName(null);
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
