package com.hongmap.hongmapbackend.notification;

/** 제보 키워드 알림 후보 한 줄(유저 + 그 유저의 키워드 하나). 로그에 남기지 않는다. */
public record ReportKeywordCandidate(Long userId, String keyword) {
}
