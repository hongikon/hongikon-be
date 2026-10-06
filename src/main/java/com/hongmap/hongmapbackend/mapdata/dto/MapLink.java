package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 외부 링크(앱 ExternalLink). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapLink(String label, String url) {

    /** url 이 없으면 링크 없음(null). */
    public static MapLink of(String label, String url) {
        return url == null || url.isBlank() ? null : new MapLink(label, url);
    }
}
