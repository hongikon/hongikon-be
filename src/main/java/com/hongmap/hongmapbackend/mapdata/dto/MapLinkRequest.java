package com.hongmap.hongmapbackend.mapdata.dto;

import com.hongmap.hongmapbackend.mapdata.MapDataRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MapLinkRequest(
        @NotBlank(message = "링크 이름을 입력해 주세요.")
        @Size(max = 50, message = "링크 이름은 50자 이하로 입력해 주세요.") String label,
        @NotBlank(message = "링크 주소를 입력해 주세요.")
        @Size(max = 500, message = "링크 주소는 500자 이하로 입력해 주세요.")
        @Pattern(regexp = MapDataRules.HTTPS_URL_REGEX, message = "링크 주소는 https:// 로 시작해야 해요.") String url
) {
}
