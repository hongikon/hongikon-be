package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AskTranscriptTest {

    @Test
    void 경로가_있으면_한_줄씩_덧붙인다(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/ask.txt");
        AskTranscript transcript = new AskTranscript(file.toString());

        transcript.record(new AskRouteRequest("한 학기\n쉬고 싶어", null, null), "leave-of-absence/start");
        transcript.record(new AskRouteRequest("그럼 복학은?", null,
                List.of(new AskRouteRequest.Turn("휴학 신청", "leave-of-absence/general"))), "return/start");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} KST\t한 학기 쉬고 싶어\t→ leave-of-absence/start");
        assertThat(lines.get(1)).endsWith("\t그럼 복학은?\t→ return/start\t(앞 대화: 휴학 신청 → leave-of-absence/general)");
    }

    @Test
    void 경로가_비면_아무것도_쓰지_않는다(@TempDir Path dir) throws Exception {
        new AskTranscript("").record(new AskRouteRequest("질문", null, null), "none");
        try (var files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }
}
