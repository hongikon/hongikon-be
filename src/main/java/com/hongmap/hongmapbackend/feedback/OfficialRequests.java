package com.hongmap.hongmapbackend.feedback;

/**
 * 공식 계정(학생회 등) 신청. 앱은 별도 API 없이 문의(POST /feedback)로 보내고 내용 첫 줄 머리말로 구분한다
 * (FE OfficialRequestModal — 첫 줄 "[공식 계정 신청] 단체 이름", 다음 줄부터 회원 번호·확인 자료).
 * 머리말은 FE src/constants/feedback.ts 의 OFFICIAL_REQUEST_PREFIX 와 같아야 한다 — 바꾸면 이전 신청을 못 알아본다.
 *
 * 운영진은 이 신청을 보낸 회원에게만 공식 이름을 붙인다(AdminUserService.setOfficialName).
 */
public final class OfficialRequests {

    public static final String PREFIX = "[공식 계정 신청]";

    private OfficialRequests() {
    }

    public static boolean isRequest(String content) {
        return content != null && content.startsWith(PREFIX);
    }

    /** 첫 줄 머리말 뒤에 적힌 단체 이름. 신청이 아니거나 비어 있으면 null. */
    public static String requestedName(String content) {
        if (!isRequest(content)) {
            return null;
        }
        String name = content.substring(PREFIX.length()).lines().findFirst().orElse("").strip();
        return name.isEmpty() ? null : name;
    }
}
