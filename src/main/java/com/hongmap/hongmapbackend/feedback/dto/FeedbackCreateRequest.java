package com.hongmap.hongmapbackend.feedback.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 100) String contact,
        /** 참고 사진 키(POST /reports/images 로 받은 것, 최대 3장, 로그인 필요). 공식 계정 신청의 확인 자료 등. */
        @Size(max = 3, message = "사진은 최대 3장까지 붙일 수 있어요.") List<String> imageKeys,
        /** 물어보기에서 넘어온 문의면 그 답 기록(선택, 11-06): 'route:123' 또는 'flow:{clientId}'. 형식이 틀리면 400. */
        @Pattern(regexp = "(route:[0-9]{1,18}|flow:[A-Za-z0-9-]{8,64})", message = "잘못된 물어보기 기록이에요.") String askRef
) {
    public FeedbackCreateRequest(String content, String contact, List<String> imageKeys) {
        this(content, contact, imageKeys, null);
    }
}
