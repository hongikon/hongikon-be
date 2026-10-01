package com.hongmap.hongmapbackend.report.image;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S3 구현. 버킷은 비공개(Block Public Access 전부 켬)로 두고, 업로드·보기 모두 presigned URL 로만 접근한다.
 * 자격 증명은 SDK 기본 체인(운영: EC2 인스턴스 역할)을 쓴다.
 */
public class S3ReportImageStorage implements ReportImageStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ReportImageStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(builder -> builder
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build()));

        // 서명에 들어간 헤더(host 제외)를 그대로 돌려줘야 클라이언트가 같은 값을 보낸다.
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> header : presigned.signedHeaders().entrySet()) {
            if (!"host".equalsIgnoreCase(header.getKey())) {
                headers.put(header.getKey(), String.join(",", header.getValue()));
            }
        }
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String presignView(String key, Duration ttl) {
        return presigner.presignGetObject(builder -> builder
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()))
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
