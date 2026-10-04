package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminLoginNameResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * 관리자 화면에서 기본으로 가려 둔 회원 개인정보를 "필요할 때만, 기록을 남기고" 보여 준다.
 * 지금은 로그인(카카오/Apple) 닉네임 원문 하나 — 실명인 경우가 많아 다른 관리자 응답에는 싣지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPiiAccessService {

    /** 열람 사유 최대 길이(admin_pii_access_logs.purpose 컬럼 길이와 같다). */
    static final int PURPOSE_MAX_LENGTH = 100;

    private final UserRepository userRepository;
    private final AdminPiiAccessLogRepository accessLogRepository;

    /**
     * 로그인 닉네임 원문을 돌려주고, 같은 트랜잭션에서 열람 기록을 남긴다 — 기록 저장이 실패하면 값도 내보내지 않는다.
     * 없는 회원이면 404 이고 기록은 남지 않는다(열람한 값이 없으므로). 사유는 앞뒤 공백을 지우고 100자에서 자른다.
     */
    @Transactional
    public AdminLoginNameResponse revealLoginName(Long adminId, Long targetUserId, String purpose) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        LocalDateTime now = LocalDateTime.now();
        accessLogRepository.save(new AdminPiiAccessLog(adminId, targetUserId,
                AdminPiiAccessLog.FIELD_LOGIN_NICKNAME, normalizePurpose(purpose), now));
        // 운영 로그(ADMIN_AUDIT 접속 기록과 별도)에도 누가·누구의 것을 열람했는지만 남긴다. 값 자체는 남기지 않는다.
        log.info("로그인 닉네임 열람: adminId={}, userId={}", adminId, targetUserId);
        return new AdminLoginNameResponse(user.getId(), user.getNickname(), user.getSocialType().name(), now);
    }

    static String normalizePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return null;
        }
        String trimmed = purpose.strip();
        return trimmed.length() <= PURPOSE_MAX_LENGTH ? trimmed : trimmed.substring(0, PURPOSE_MAX_LENGTH);
    }
}
