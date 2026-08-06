package com.hongmap.hongmapbackend.auth;

import com.hongmap.hongmapbackend.auth.dto.TokenExchangeRequest;
import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthorizationCodeStore authorizationCodeStore;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/token/exchange")
    public TokenResponse exchange(@Valid @RequestBody TokenExchangeRequest request) {
        Long userId = authorizationCodeStore.consume(request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 code입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new TokenResponse(accessToken, refreshToken);
    }
}
