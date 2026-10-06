package com.hongmap.hongmapbackend.feedback.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 100) String contact,
        /** 참고 사진 키(POST /reports/images 로 받은 것, 최대 3장, 로그인 필요). 공식 계정 신청의 확인 자료 등. */
        @Size(max = 3, message = "사진은 최대 3장까지 붙일 수 있어요.") List<String> imageKeys
) {
}
