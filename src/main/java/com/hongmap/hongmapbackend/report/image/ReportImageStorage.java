package com.hongmap.hongmapbackend.report.image;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 제보 사진 저장소(S3) 얇은 어댑터. 키 규칙·크기·형식 같은 정책은 {@link ReportImageService} 가 정한다.
 * 버킷이 설정되지 않은 환경(로컬·테스트·S3 준비 전 운영)에서는 {@link #isEnabled()} 가 false 다.
 */
public interface ReportImageStorage {

    boolean isEnabled();

    /**
     * 이 키로 PUT 할 수 있는 presigned URL. {@code headers} 는 클라이언트가 PUT 에 그대로 실어야 한다(서명에 포함됨).
     * {@code contentLength} 가 있으면 Content-Length 도 서명해 그 크기의 본문만 받는다(null 이면 서명하지 않음).
     */
    PresignedUpload presignUpload(String key, String contentType, Long contentLength, Duration ttl);

    /** 업로드 확인용 HeadObject. 객체가 없으면 empty. */
    Optional<StoredObject> head(String key);

    /** 객체 본문. 최대 {@code maxBytes + 1} 바이트까지만 읽는다(호출자가 초과 여부를 판단). 없으면 empty. */
    Optional<byte[]> get(String key, long maxBytes);

    /** 서버가 정리한 사진을 저장한다. */
    void put(String key, String contentType, byte[] bytes);

    /** 보기용 presigned GET URL. */
    String presignView(String key, Duration ttl);

    void delete(String key);

    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
    }

    record StoredObject(long contentLength, String contentType) {
    }
}
