package com.hongmap.hongmapbackend.user.dto;

/** GET /users/me/member-code. 예: {"memberCode":"K7Q2M9XA4D"} */
public record MemberCodeResponse(String memberCode) {
}
