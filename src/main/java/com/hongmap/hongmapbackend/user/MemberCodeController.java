package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.user.dto.MemberCodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 내 공개 회원 번호. 앱 설정 화면의 "회원 번호" 줄에 쓴다(문의할 때 불러 주는 값). */
@RestController
@RequiredArgsConstructor
public class MemberCodeController {

    private final UserRepository userRepository;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 회원 번호", description = "공개용 회원 번호(예: HIU-482913)를 조회합니다. 내부 id 대신 화면·문의에 씁니다.")
    @GetMapping("/users/me/member-code")
    @Transactional(readOnly = true)
    public MemberCodeResponse memberCode(@AuthenticationPrincipal Long userId) {
        return userRepository.findById(userId)
                .map(user -> new MemberCodeResponse(user.getMemberCode()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
