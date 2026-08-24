package com.hongmap.hongmapbackend.crawler;

/** 목록/상세 페이지 요청이 재시도 끝에도 실패했을 때 던진다. */
public class CrawlerFetchException extends RuntimeException {

    public CrawlerFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
