package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminLoginNameResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 화면에서 기본으로 가려 둔 회원 개인정보를 필요할 때만 따로 보여 준다.
 * 지금은 로그인(카카오/Apple) 닉네임 원문 하나 — 실명인 경우가 많아 다른 관리자 응답에는 싣지 않는다(개인정보 보호법 제3조).
 *
 * 열람 기록은 별도 테이블 없이 서버 로그 한 줄로만 남긴다(오너 결정, 「개인정보의 안전성 확보조치 기준」 제8조 접속기록을
 * 최소한으로 충족). ADMIN_AUDIT 로거(AdminAuditInterceptor 와 같은 로거)에 관리자 id·대상 id 만 쓰고, 닉네임 값은 절대 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminPiiAccessService {

    private static final Logger AUDIT = LoggerFactory.getLogger("ADMIN_AUDIT");

    private final UserRepository userRepository;

    /** 로그인 닉네임 원문. 없는 회원이면 404 이고 로그도 남기지 않는다(열람한 값이 없으므로). */
    @Transactional(readOnly = true)
    public AdminLoginNameResponse revealLoginName(Long adminId, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        AUDIT.info("admin-login-name-view adminId={} targetUserId={}", adminId, targetUserId);
        return new AdminLoginNameResponse(user.getId(), user.getNickname(), user.getSocialType().name());
    }
}
