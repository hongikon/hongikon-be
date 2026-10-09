package com.hongmap.hongmapbackend.auth.demo;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /auth/demo — 앱 심사용 데모 로그인(DemoLoginService). 응답은 /auth/token/exchange·/auth/apple 과 같은 TokenResponse.
 *
 * 본문을 DTO + @Valid 로 받지 않고 문자열로 받아 켜져 있는지 먼저 확인한 뒤 읽는다. 꺼진 상태에서 잘못된 본문이 400 으로 돌아오면
 * 엔드포인트가 있다는 것이 드러나기 때문이다(꺼져 있으면 본문과 상관없이 404). 켜진 상태의 잘못된 본문은 빈 자격 증명으로 보고 401.
 */
@RestController
@RequiredArgsConstructor
public class DemoLoginController {

    private static final int MAX_BODY_LENGTH = 4096;
    private static final int MAX_FIELD_LENGTH = 256;

    private final DemoLoginService demoLoginService;
    private final JsonMapper jsonMapper;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "앱 심사용 데모 로그인", description = "body {username, password}. 서버에서 켠 동안만 동작(꺼져 있으면 404). "
            + "틀리면 401, 접속 IP 마다 10분에 10번 넘게 시도하면 429. 성공 응답은 토큰 교환과 같은 access/refresh 토큰.")
    @PostMapping("/auth/demo")
    public TokenResponse demoLogin(@RequestBody(required = false) String body, HttpServletRequest request) {
        demoLoginService.requireActive();
        String username = null;
        String password = null;
        if (body != null && body.length() <= MAX_BODY_LENGTH) {
            try {
                JsonNode node = jsonMapper.readTree(body);
                username = textField(node, "username");
                password = textField(node, "password");
            } catch (JacksonException e) {
                // 형식이 틀린 본문 — 빈 자격 증명으로 처리(401). 본문은 로그에 남기지 않는다.
            }
        }
        return demoLoginService.login(username, password, request.getRemoteAddr());
    }

    private static String textField(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(name);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString();
        return text.length() > MAX_FIELD_LENGTH ? null : text;
    }
}
