package com.hongmap.hongmapbackend.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지 번호(offset) 기반 목록 응답 공통 형태. Spring의 Page를 그대로 직렬화하면 pageable/sort 등
 * 내부 필드가 노출되고 버전마다 모양이 달라질 수 있어, 프론트가 쓰는 값만 골라 고정된 스키마로 내보낸다.
 *
 * @param content       이번 페이지 항목
 * @param page          현재 페이지 번호(0부터)
 * @param size          페이지 크기(요청값, 상한 적용 후)
 * @param totalElements 전체 건수
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
