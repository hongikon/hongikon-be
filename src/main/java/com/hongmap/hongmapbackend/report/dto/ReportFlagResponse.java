package com.hongmap.hongmapbackend.report.dto;

/**
 * POST /reports/{id}/flags 응답. 예전엔 그 제보의 전체 신고 수(flagCount)를 돌려줬지만 앱은 쓰지 않고,
 * 다른 사람들이 몇 번 신고했는지를 신고자에게 알릴 이유가 없어 접수 여부만 돌려준다(관리자 화면은 따로 신고 수를 본다).
 */
public record ReportFlagResponse(
        boolean flagged
) {
}
