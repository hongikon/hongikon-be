package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.user.dto.AppNicknameRequest;
import com.hongmap.hongmapbackend.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserMeController {

    private final AppNicknameService appNicknameService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 정보", description = "앱 닉네임과, 제보 등에서 다른 사람에게 보이는 이름(displayName)을 조회합니다.")
    @GetMapping("/users/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return appNicknameService.getMe(userId);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "앱 닉네임 설정", description = "2~12자 한글·영문·숫자·밑줄. 빈 값이면 지우고 가린 로그인 닉네임으로 돌아갑니다. "
            + "규칙 위반 400, 중복 409, 하루 변경 한도 초과 429.")
    @PutMapping("/users/me/nickname")
    public MeResponse change(@AuthenticationPrincipal Long userId, @RequestBody AppNicknameRequest request) {
        return appNicknameService.change(userId, request.nickname());
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "앱 닉네임 지우기", description = "앱 닉네임을 지워 로그인 닉네임 첫 글자만 보이게 가린 이름으로 돌아갑니다.")
    @DeleteMapping("/users/me/nickname")
    public MeResponse clear(@AuthenticationPrincipal Long userId) {
        return appNicknameService.change(userId, null);
    }
}
