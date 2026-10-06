package com.hongmap.hongmapbackend.comment;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopFlagReasonLabelTest {

    @Test
    void 관리자가_사유를_비우면_가장_많은_신고_사유를_쓴다() {
        assertThat(ReportCommentService.topFlagReasonLabel(Map.of("SPAM", 1L, "INAPPROPRIATE", 3L)))
                .isEqualTo("욕설·비하 등 부적절한 내용");
        // 같으면 앞선 사유(FALSE_INFO → SPAM → …)
        assertThat(ReportCommentService.topFlagReasonLabel(Map.of("PRIVACY", 2L, "SPAM", 2L))).isEqualTo("스팸·광고");
    }

    @Test
    void 신고가_없거나_기타뿐이면_null() {
        assertThat(ReportCommentService.topFlagReasonLabel(null)).isNull();
        assertThat(ReportCommentService.topFlagReasonLabel(Map.of())).isNull();
        assertThat(ReportCommentService.topFlagReasonLabel(Map.of("ETC", 5L))).isNull();
    }
}
