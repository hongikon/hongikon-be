package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ParserType → BoardParser 구현체 조회. hongmap(프론트) scripts/crawler/parsers.mjs의 getParser()와 대응. */
@Component
public class BoardParserRegistry {

    private final Map<ParserType, BoardParser> parsers;

    public BoardParserRegistry(List<BoardParser> parsers) {
        this.parsers = parsers.stream().collect(Collectors.toMap(BoardParser::type, Function.identity()));
    }

    public BoardParser resolve(ParserType type) {
        BoardParser parser = parsers.get(type);
        if (parser == null) {
            throw new IllegalStateException("등록되지 않은 파서: " + type);
        }
        return parser;
    }
}
