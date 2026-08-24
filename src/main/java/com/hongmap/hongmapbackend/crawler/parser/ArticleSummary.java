package com.hongmap.hongmapbackend.crawler.parser;

/**
 * 목록 페이지 한 행. hongmap(프론트) scripts/crawler/parse-list.mjs가 만드는 요약 객체와 대응.
 *
 * @param articleNo     게시판 CMS 내부 게시글 번호(URL의 articleNo/idx 등). 사이트마다 형식이 달라 String으로 둔다.
 * @param title         제목
 * @param boardCategory 행에 분류 라벨이 붙는 게시판(대학공지 등)에서만 값이 있다. 없으면 빈 문자열.
 * @param date          "yyyy.MM.dd" 형식 원문. 못 읽었으면 빈 문자열.
 * @param views         조회수. 없으면 null.
 * @param hasAttachment 첨부파일 아이콘 존재 여부
 * @param link          상세 페이지 절대 URL
 */
public record ArticleSummary(
        String articleNo,
        String title,
        String boardCategory,
        String date,
        Integer views,
        boolean hasAttachment,
        String link
) {
}
