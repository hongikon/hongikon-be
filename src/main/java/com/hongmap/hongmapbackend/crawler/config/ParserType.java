package com.hongmap.hongmapbackend.crawler.config;

/**
 * 게시판이 쓰는 CMS 종류. 값 하나가 BoardParser 구현체 하나와 대응된다.
 * hongmap(프론트) scripts/crawler/parsers.mjs 의 PARSERS 레지스트리 키와 같은 구분.
 */
public enum ParserType {
    /** 본부·학과 게시판이 쓰는 .do 게시판(가장 흔함). 없으면 이게 기본값. */
    HONGIK,
    /** 건축학부(arch.hongik.ac.kr) PHP CMS. */
    ARCH,
    /** 도시공학과 등 Imweb으로 만든 사이트. */
    IMWEB
}
