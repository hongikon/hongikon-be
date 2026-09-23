package com.hongmap.hongmapbackend.news.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongmap.hongmapbackend.news.NewsAttachment;
import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** `List<NewsAttachment>` ↔ TEXT(JSON 배열) 컬럼 변환. News.attachments(첨부파일 이름+URL 목록)에 쓴다. */
@Slf4j
public class NewsAttachmentListJsonConverter implements AttributeConverter<List<NewsAttachment>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<NewsAttachment>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<NewsAttachment> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.warn("첨부파일 목록 직렬화 실패, 저장하지 않음: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<NewsAttachment> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            log.warn("첨부파일 목록 역직렬화 실패, 빈 목록으로 대체: {}", e.getMessage());
            return List.of();
        }
    }
}
