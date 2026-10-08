package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 앱 지도 데이터(건물·편의시설·제휴업체·전시) 한 번에. 비로그인 허용 — SecurityConfig. */
@RestController
@RequiredArgsConstructor
public class MapDataController {

    static final String CACHE_CONTROL = "public, max-age=300";

    private final MapDataService mapDataService;

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "지도 데이터", description = "건물·편의시설·제휴업체 전체 + 전시(오늘 KST 기준 끝나지 않았고 60일 안에 시작하는 것). null 필드는 생략. "
            + "version = 본문(version 제외)의 SHA-256 앞 16 hex, ETag 로도 내려간다. "
            + "If-None-Match 가 같으면 304(본문 없음). 서버는 최대 5분 캐시하고 관리자 수정 시 바로 갱신한다.")
    @GetMapping("/map/data")
    public ResponseEntity<byte[]> mapData(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MapDataService.Snapshot snapshot = mapDataService.current();
        String etag = "\"" + snapshot.version() + "\"";
        if (matches(ifNoneMatch, snapshot.version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(snapshot.body());
    }

    /** If-None-Match: "v1", W/"v2" 또는 * */
    static boolean matches(String ifNoneMatch, String version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String raw : ifNoneMatch.split(",")) {
            String tag = raw.trim();
            if (tag.equals("*")) {
                return true;
            }
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (tag.equals(version)) {
                return true;
            }
        }
        return false;
    }
}
