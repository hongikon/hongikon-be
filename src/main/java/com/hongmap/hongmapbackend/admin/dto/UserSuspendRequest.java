package com.hongmap.hongmapbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reason 정지 사유(필수, 200자 이내). 관리자 기록용이며 사용자에게는 보이지 않는다. */
public record UserSuspendRequest(
        @NotBlank(message = "정지 사유를 입력해 주세요.") @Size(max = 200) String reason
) {
}
