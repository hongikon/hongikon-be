package com.hongmap.hongmapbackend.user.dto;

/** GET /users/me/member-code. 예: {"memberCode":"HIU-482913"} */
public record MemberCodeResponse(String memberCode) {
}
