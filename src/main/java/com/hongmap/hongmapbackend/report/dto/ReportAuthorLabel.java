package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.Report;

/**
 * 공개 제보 응답의 작성자 표시. GET /reports 는 비로그인에게도 열려 있는데, 닉네임은 카카오 프로필 이름
 * (대개 실명)을 그대로 가져온 값이라 남에게 그대로 내보내면 처리방침에 없는 공개가 된다.
 * 그래서 본인 제보에만 닉네임을 싣고, 다른 사람에게는 고정 문구만 보낸다. 관리자 화면은 AdminReportResponse 로 따로 본다.
 */
public final class ReportAuthorLabel {

    public static final String ANONYMOUS = "익명";

    private ReportAuthorLabel() {
    }

    public static boolean isMine(Report report, Long requesterId) {
        return requesterId != null && requesterId.equals(report.getUser().getId());
    }

    public static String of(Report report, Long requesterId) {
        return isMine(report, requesterId) ? report.getUser().getNickname() : ANONYMOUS;
    }
}
