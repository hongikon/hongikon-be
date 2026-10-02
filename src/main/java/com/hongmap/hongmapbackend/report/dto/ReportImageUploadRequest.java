package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 올릴 사진의 MIME 타입(image/jpeg 또는 image/png)과 바이트 수(선택).
 * contentLength 를 보내면 presigned PUT 에 Content-Length 가 서명돼, 응답 headers 의 content-length 와 같은 크기로만 올릴 수 있다.
 */
public record ReportImageUploadRequest(
        @NotBlank
        @Size(max = 50)
        String contentType,
        @Positive
        Long contentLength
) {
}
