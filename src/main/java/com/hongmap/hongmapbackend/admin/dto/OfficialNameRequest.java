package com.hongmap.hongmapbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param name 공식 이름(예: "경영대학 학생회", 2~30자). 앱 닉네임 대신 보이고 공식 배지가 붙는다. */
public record OfficialNameRequest(
        @NotBlank(message = "공식 이름을 입력해 주세요.") @Size(max = 30, message = "공식 이름은 30자 이내로 입력해 주세요.") String name
) {
}
