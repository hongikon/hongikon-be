package com.hongmap.hongmapbackend.report.image;

import java.time.Duration;
import java.util.Optional;

/** AWS_S3_BUCKET 이 비어 있을 때. 사진 기능만 꺼지고 제보 자체는 그대로 동작한다. */
public class DisabledReportImageStorage implements ReportImageStorage {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Long contentLength, Duration ttl) {
        throw new IllegalStateException("report image storage is disabled");
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> get(String key, long maxBytes) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String contentType, byte[] bytes) {
        throw new IllegalStateException("report image storage is disabled");
    }

    @Override
    public String presignView(String key, Duration ttl) {
        return null;
    }

    @Override
    public void delete(String key) {
        // 저장소가 없으니 지울 것도 없다.
    }
}
