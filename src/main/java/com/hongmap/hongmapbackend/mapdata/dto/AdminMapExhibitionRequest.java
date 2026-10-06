package com.hongmap.hongmapbackend.mapdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 전시 추가·수정 본문. map/data 의 exhibition 모양에서 id 를 뺀 것(있어도 무시).
 * facilityId 는 kind '행사·전시' 인 편의시설 code. 날짜는 "yyyy-MM-dd"(문자열로 받아 잘못된 날짜도 같은 400 형식으로 돌려준다).
 */
public record AdminMapExhibitionRequest(
        @NotBlank(message = "장소를 골라 주세요.")
        @Size(max = 100, message = "장소 id 는 100자 이하여야 해요.") String facilityId,
        @NotBlank(message = "전시 제목을 입력해 주세요.")
        @Size(max = 150, message = "전시 제목은 150자 이하로 입력해 주세요.") String title,
        @NotBlank(message = "시작일을 입력해 주세요.")
        @Pattern(regexp = DATE_REGEX, message = "시작일은 yyyy-MM-dd 형식이어야 해요.") String startsOn,
        @NotBlank(message = "종료일을 입력해 주세요.")
        @Pattern(regexp = DATE_REGEX, message = "종료일은 yyyy-MM-dd 형식이어야 해요.") String endsOn,
        @Size(max = 100, message = "관람 시간은 100자 이하로 입력해 주세요.") String hours,
        @Size(max = 1000, message = "설명은 1000자 이하로 입력해 주세요.") String description,
        @Valid MapLinkRequest link
) {
    public static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
}
