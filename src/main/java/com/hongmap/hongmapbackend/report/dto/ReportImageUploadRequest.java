package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 올릴 사진의 MIME 타입. image/jpeg 또는 image/png. */
public record ReportImageUploadRequest(
        @NotBlank
        @Size(max = 50)
        String contentType
) {
}
