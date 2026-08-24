package com.hongmap.hongmapbackend.crawler.parser;

import java.util.List;

/**
 * 상세 페이지 파싱 결과. hongmap(프론트) scripts/crawler/parse-view.mjs가 만드는 객체와 대응.
 *
 * @param title       상세 페이지의 제목(목록과 다를 수 있어 별도로 둔다)
 * @param writer      작성 부서. 없으면 빈 문자열.
 * @param date        "yyyy.MM.dd" 형식 원문. 없으면 빈 문자열.
 * @param views       조회수. 없으면 null.
 * @param content     본문 전체 텍스트(태그 제거됨)
 * @param images      본문에 박힌 이미지 절대 URL 목록
 * @param attachments 첨부파일 목록
 */
public record ArticleDetail(
        String title,
        String writer,
        String date,
        Integer views,
        String content,
        List<String> images,
        List<Attachment> attachments
) {
}
