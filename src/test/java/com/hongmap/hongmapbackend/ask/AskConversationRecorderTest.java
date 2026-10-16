package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 물어보기 기록 보관 기간(365일) 지난 행 삭제. */
@SpringBootTest
@ActiveProfiles("test")
class AskConversationRecorderTest {

    @Autowired AskConversationRecorder recorder;
    @Autowired AskConversationLogRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 보관_기간이_지난_기록만_지운다() {
        Long old = recorder.record(new AskRouteRequest("오래된 질문", null, null), null, AskConversationLog.Outcome.FAILED,
                List.of(), AskUsage.ZERO, 0);
        Long fresh = recorder.record(new AskRouteRequest("새 질문", null, null), null, AskConversationLog.Outcome.FAILED,
                List.of(), AskUsage.ZERO, 0);
        jdbc.update("UPDATE ask_conversation_logs SET created_at = ? WHERE id = ?", LocalDateTime.now().minusDays(366), old);

        recorder.purgeExpired();

        assertThat(repository.findById(old)).isEmpty();
        assertThat(repository.findById(fresh)).isPresent();
    }
}
