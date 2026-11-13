package com.hongmap.hongmapbackend.notice;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notice.dto.NoticeListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 공지사항(설정 > 공지사항, 앱을 켤 때 팝업). 게스트도 본다 — 공개 공지만 담는다. */
@Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final AppNoticeService service;

    @Operation(summary = "앱 공지사항", description = "게시 시각이 지난 공지 최근 50개, 최근 게시 순. "
            + "popup 은 지금 앱을 켤 때 띄울 공지에만 붙는다. 시각은 UTC(존 없음).")
    @GetMapping("/notices")
    public NoticeListResponse notices() {
        return service.published();
    }
}
