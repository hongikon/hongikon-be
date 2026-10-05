package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.community.dto.AuthorNotifyRequest;
import com.hongmap.hongmapbackend.community.dto.AuthorNotifyResponse;
import com.hongmap.hongmapbackend.community.dto.FireResponse;
import com.hongmap.hongmapbackend.community.dto.FollowResponse;
import com.hongmap.hongmapbackend.community.dto.ViewResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 제보 🔥·관심·작성자 알림·조회 수. 조회 기록만 게스트도, 나머지는 로그인 필요(SecurityConfig). */
@Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
@RestController
@RequiredArgsConstructor
public class ReportCommunityController {

    public static final String INSTALL_ID_HEADER = "X-Install-Id";

    private final ReportCommunityService communityService;
    private final ReportHotService hotService;

    /** GET /reports 중 sort=hot 만 여기로 온다(params 조건이 더 구체적). 나머지 GET /reports 는 ReportController. */
    @Operation(summary = "HOT 제보 목록", description = "지금 진행 중인 공개 제보 중 🔥가 있는 것만 최근 60분 🔥 수 → 전체 🔥 수 → 최신 순으로 "
            + "최대 20개. 항목 모양은 GET /reports 와 같다(hot 이 true 면 HOT 배지).")
    @GetMapping(value = "/reports", params = "sort=hot")
    public ReportListResponse hot(@AuthenticationPrincipal Long userId) {
        return hotService.hotReports(userId);
    }

    @Operation(summary = "🔥 누르기", description = "남의 공개 제보에 🔥(한 사람 한 번, 다시 눌러도 그대로). 내 제보 400, 끝난 제보 409, "
            + "🔥·관심·👍 합쳐 1분 20번 넘으면 429. 응답의 hot 은 최근 60분 🔥 5개 이상.")
    @PutMapping("/reports/{reportId}/fire")
    public FireResponse fire(@AuthenticationPrincipal Long userId, @PathVariable Long reportId) {
        return communityService.setFire(userId, reportId, true);
    }

    @Operation(summary = "🔥 끄기")
    @DeleteMapping("/reports/{reportId}/fire")
    public FireResponse unfire(@AuthenticationPrincipal Long userId, @PathVariable Long reportId) {
        return communityService.setFire(userId, reportId, false);
    }

    @Operation(summary = "관심 제보 등록", description = "시작할 때·끝나기 30분 전·새 댓글(30분 묶음) 알림을 받는다. 내 제보 400, "
            + "끝난 제보 또는 관심 100개 초과 409. 제보가 끝나거나 내려가면 자동으로 풀린다.")
    @PutMapping("/reports/{reportId}/follow")
    public FollowResponse follow(@AuthenticationPrincipal Long userId, @PathVariable Long reportId) {
        return communityService.setFollow(userId, reportId, true);
    }

    @Operation(summary = "관심 제보 해제")
    @DeleteMapping("/reports/{reportId}/follow")
    public FollowResponse unfollow(@AuthenticationPrincipal Long userId, @PathVariable Long reportId) {
        return communityService.setFollow(userId, reportId, false);
    }

    @Operation(summary = "이 제보 알림 켜기·끄기(작성자)", description = "내 제보의 댓글·답글·🔥 이정표(10·50·100) 알림. 기본 켜짐. "
            + "설정의 \"내 제보 결과 알림\"을 끄면 이 값과 상관없이 오지 않는다. 남의 제보 403.")
    @PutMapping("/reports/{reportId}/notifications")
    public AuthorNotifyResponse setAuthorNotify(@AuthenticationPrincipal Long userId, @PathVariable Long reportId,
                                                @Valid @RequestBody AuthorNotifyRequest request) {
        return communityService.setAuthorNotify(userId, reportId, request.enabled());
    }

    @Operation(summary = "제보 조회 기록", description = "게스트도 가능. 로그인 사용자는 계정, 아니면 X-Install-Id 헤더(앱 설치마다 만든 "
            + "8~64자 영문·숫자·-·_)로 하루(KST) 한 번만 센다. 둘 다 없으면 세지 않고 현재 수만 준다. 공개되지 않은 제보 404.")
    @PostMapping("/reports/{reportId}/views")
    public ViewResponse view(@AuthenticationPrincipal Long userId, @PathVariable Long reportId,
                             @RequestHeader(value = INSTALL_ID_HEADER, required = false) String installId) {
        return communityService.recordView(userId, installId, reportId);
    }
}
