package com.hongmap.hongmapbackend.news;

/**
 * 소식 첨부파일 메타데이터(이름 + URL). 파일 자체는 내려받지 않고 원문 링크만 들고 있는다.
 * 크롤러의 {@code crawler.parser.Attachment}와 필드가 같지만, news 도메인이 crawler 패키지에
 * 의존하지 않도록(의존 방향은 crawler → news 여야 한다) 별도로 둔다 — 변환은
 * NewsCrawlStorageService가 담당.
 */
public record NewsAttachment(String name, String url) {
}
