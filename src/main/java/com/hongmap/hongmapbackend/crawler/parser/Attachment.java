package com.hongmap.hongmapbackend.crawler.parser;

/** 첨부파일 메타데이터(이름 + URL). 파일 자체는 내려받지 않는다. */
public record Attachment(String name, String url) {
}
