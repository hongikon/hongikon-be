package com.hongmap.hongmapbackend.report.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 사진 업로드 안내. 앱은 {@code uploadUrl} 로 {@code method}(PUT) 요청을 보내되 {@code headers} 를 그대로 싣고,
 * 성공하면 {@code POST /reports} 의 {@code imageKeys}(최대 3장)에 {@code key} 를 넣는다.
 *
 * @param maxBytes 서버가 받는 최대 크기. 넘으면 등록 때 400.
 */
public record ReportImageUploadResponse(
        String key,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt,
        long maxBytes
) {
}
