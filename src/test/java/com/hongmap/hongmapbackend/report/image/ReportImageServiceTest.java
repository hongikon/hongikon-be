package com.hongmap.hongmapbackend.report.image;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportImageServiceTest {

    private S3ReportImageStorage s3Storage() {
        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret"));
        Region region = Region.AP_NORTHEAST_2;
        return new S3ReportImageStorage(
                S3Client.builder().region(region).credentialsProvider(credentials).build(),
                S3Presigner.builder().region(region).credentialsProvider(credentials).build(),
                "hongikon-test");
    }

    @Test
    void presigned_PUT은_키와_content_type을_서명하고_GET도_서명한다() {
        S3ReportImageStorage storage = s3Storage();

        ReportImageStorage.PresignedUpload upload =
                storage.presignUpload("reports/abc.jpg", "image/jpeg", null, Duration.ofMinutes(5));
        assertThat(upload.url()).startsWith("https://hongikon-test.s3.ap-northeast-2.amazonaws.com/reports/abc.jpg?")
                .contains("X-Amz-Signature=").contains("X-Amz-Expires=300").contains("content-type");
        assertThat(upload.headers()).containsEntry("content-type", "image/jpeg").doesNotContainKey("host")
                .doesNotContainKey("content-length");

        assertThat(storage.presignView("reports/abc.jpg", Duration.ofHours(1)))
                .contains("reports/abc.jpg?").contains("X-Amz-Expires=3600");
    }

    @Test
    void contentLength를_주면_presigned_PUT이_Content_Length도_서명한다() {
        ReportImageStorage.PresignedUpload upload =
                s3Storage().presignUpload("reports/abc.jpg", "image/jpeg", 123_456L, Duration.ofMinutes(5));
        // X-Amz-SignedHeaders 에 content-length 가 들어가 S3 가 다른 크기의 PUT 을 서명 불일치(403)로 거절한다.
        assertThat(upload.url()).containsPattern("X-Amz-SignedHeaders=[^&]*content-length");
        assertThat(upload.headers()).containsEntry("content-length", "123456");
    }

    @Test
    void 사용자별_시간당_발급_한도를_넘으면_429_한시간_지나면_다시_된다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-02T00:00:00Z"));
        ReportImageService service = new ReportImageService(s3Storage(), null, 5_242_880,
                Duration.ofMinutes(5), Duration.ofHours(1), 2, clock);

        service.issueUploadUrl(1L, "image/jpeg");
        service.issueUploadUrl(1L, "IMAGE/JPEG");
        assertThatThrownBy(() -> service.issueUploadUrl(1L, "image/jpeg"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
        service.issueUploadUrl(2L, "image/png"); // 다른 사용자는 별도

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertThat(service.issueUploadUrl(1L, "image/jpeg").key()).startsWith("reports/");
    }

    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) { this.now = now; }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    @Test
    void 여러장_검증은_장수_빈값_중복_형식을_S3_호출_전에_거른다() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        ReportImageStorage recording = new ReportImageStorage() {
            @Override public boolean isEnabled() { return true; }
            @Override public PresignedUpload presignUpload(String key, String type, Long len, Duration ttl) { throw new AssertionError(); }
            @Override public java.util.Optional<StoredObject> head(String key) { calls.add("head " + key); return java.util.Optional.empty(); }
            @Override public java.util.Optional<byte[]> get(String key, long maxBytes) { calls.add("get " + key); return java.util.Optional.empty(); }
            @Override public void put(String key, String type, byte[] bytes) { calls.add("put " + key); }
            @Override public String presignView(String key, Duration ttl) { return "https://view/" + key; }
            @Override public void delete(String key) { calls.add("delete " + key); }
            @Override public void copy(String from, String to) { calls.add("copy " + from); }
        };
        ReportImageService service = new ReportImageService(recording, null, 5_242_880,
                Duration.ofMinutes(5), Duration.ofHours(1), 30, Clock.systemUTC());
        String k1 = "reports/" + java.util.UUID.randomUUID() + ".jpg";
        String k2 = "reports/" + java.util.UUID.randomUUID() + ".png";

        assertThat(service.validateForAttach((java.util.List<String>) null)).isEmpty();
        assertThat(service.validateForAttach(java.util.List.of())).isEmpty();
        for (java.util.List<String> bad : java.util.List.of(
                java.util.List.of(k1, k2, k1 + "x", k2 + "y"),          // 4장
                java.util.List.of(k1, " " + k1),                          // 앞뒤 공백만 다른 중복
                java.util.Arrays.asList(k1, null),                        // 빈 값
                java.util.List.of(k1, ""),
                java.util.List.of(k1, "reports/../secret.jpg"))) {        // 형식
            assertThatThrownBy(() -> service.validateForAttach(bad))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        }
        assertThat(calls).isEmpty();

        assertThat(service.viewUrls(java.util.List.of(k2, k1))).containsExactly("https://view/" + k2, "https://view/" + k1);
        assertThat(service.viewUrls(null)).isEmpty();
    }
}
