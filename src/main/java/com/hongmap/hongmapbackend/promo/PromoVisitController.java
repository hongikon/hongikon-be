package com.hongmap.hongmapbackend.promo;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 홍보 링크. hongikon.com/app/club → (Netlify 프록시) GET /promo/club → 방문 수 +1 → 앱 받기 안내(hongikon.com/app)로 302.
 * 모르는 경로여도 안내 페이지로 보낸다(세지만 않는다).
 */
@RestController
@RequiredArgsConstructor
public class PromoVisitController {

    static final URI APP_PAGE = URI.create("https://hongikon.com/app");

    private final PromoVisitService promoVisitService;

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "홍보 링크 이동", description = "방문 수를 세고 앱 받기 안내로 보냅니다. 인증 불필요.")
    @GetMapping("/promo/{source}")
    public ResponseEntity<Void> visit(@PathVariable String source,
                                      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        promoVisitService.record(source, userAgent);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(APP_PAGE)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /**
     * 앱 받기 안내 페이지(hongikon.com/app)가 열렸다. 페이지의 보이지 않는 이미지가 부른다(자바스크립트 없이 동작,
     * 링크 미리보기 봇은 보통 이미지를 받지 않는다). 숫자만 올리고 204 — 이미지가 아니어도 페이지에는 영향이 없다.
     */
    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "앱 받기 안내 페이지 방문", description = "hongikon.com/app 방문 수를 셉니다(source=app). 인증 불필요.")
    @GetMapping("/promo/{source}/seen")
    public ResponseEntity<Void> seen(@PathVariable String source,
                                     @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        promoVisitService.recordPageView(source, userAgent);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "홍보 링크 방문 수", description = "경로·날짜(KST)별 방문 수. ADMIN 전용.")
    @GetMapping("/admin/promo-visits")
    public List<Map<String, Object>> list() {
        return promoVisitService.list();
    }
}
