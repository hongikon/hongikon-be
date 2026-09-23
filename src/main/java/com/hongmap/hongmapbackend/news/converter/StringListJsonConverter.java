package com.hongmap.hongmapbackend.news.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * `List<String>` ↔ TEXT(JSON 배열) 컬럼 변환. News.images(본문에 박힌 이미지 URL 목록)에 쓴다.
 * 크게 조회 필터로 쓸 일이 없는 부가 표시용 데이터라 별도 테이블 대신 JSON 컬럼으로 둔다.
 */
@Slf4j
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.warn("이미지 URL 목록 직렬화 실패, 저장하지 않음: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            log.warn("이미지 URL 목록 역직렬화 실패, 빈 목록으로 대체: {}", e.getMessage());
            return List.of();
        }
    }
}
