package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 물어보기 서버 분류 기록(개발용). ask.transcript-file 에 경로가 있을 때만 질문과 고른 안내를 txt 한 줄씩 덧붙인다 —
 * 로컬에서 어떤 질문에 무엇을 골랐는지 보며 카탈로그·프롬프트를 다듬는 용도. 운영 기본값은 비어 있어 아무것도 남기지 않는다
 * (운영에서 모으려면 개인정보처리방침 반영이 먼저다).
 *
 * 형식: `2026-10-15 23:40:12 KST<TAB>질문<TAB>→ 고른 id<TAB>(앞 대화: 질문 → id | …)`. 쓰기 실패는 경고만 남기고 응답에는 영향이 없다.
 */
@Slf4j
@Component
public class AskTranscript {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'");

    private final Path file;

    public AskTranscript(@Value("${ask.transcript-file:}") String file) {
        this.file = file == null || file.isBlank() ? null : Path.of(file.trim());
        if (this.file != null) {
            log.info("물어보기 분류 기록을 {} 에 남긴다(개발용).", this.file.toAbsolutePath());
        }
    }

    public synchronized void record(AskRouteRequest request, String result) {
        if (file == null) {
            return;
        }
        String history = request.history() == null || request.history().isEmpty() ? "" : request.history().stream()
                .map(turn -> oneLine(turn.question()) + (turn.nodeId() == null ? "" : " → " + oneLine(turn.nodeId())))
                .collect(Collectors.joining(" | ", "\t(앞 대화: ", ")"));
        String line = ZonedDateTime.now(KST).format(TIME) + '\t' + oneLine(request.question()) + "\t→ " + result + history + '\n';
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("물어보기 분류 기록 쓰기 실패: {}", e.getMessage());
        }
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
