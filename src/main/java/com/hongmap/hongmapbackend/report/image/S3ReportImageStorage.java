package com.hongmap.hongmapbackend.report.image;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
@Slf4j
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
    public PresignedUpload presignUpload(String key, String contentType, Long contentLength, Duration ttl) {
        // presigned PUT 은 "최대 크기"를 서명할 수 없다(그건 POST 정책의 content-length-range 만 가능).
        // 클라이언트가 크기를 미리 알려 주면 정확한 Content-Length 를 서명해 다른 크기의 PUT 을 S3 가 거절하게 한다.
        // 알려 주지 않으면(구버전 앱) 등록 시 HeadObject·본문 읽기로 크기를 확인한다.
        PutObjectRequest.Builder put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);
        if (contentLength != null) {
            put.contentLength(contentLength);
        }
        PresignedPutObjectRequest presigned = presigner.presignPutObject(builder -> builder
                .signatureDuration(ttl)
                .putObjectRequest(put.build()));

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
            // s3:ListBucket 권한이 없으면 없는 키에 404 대신 403 이 온다. 어느 쪽이든 "확인 못 함" → 등록 400.
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                if (e.statusCode() == 403) {
                    log.warn("report image HeadObject 403 key={} — IAM 에 s3:ListBucket(reports/*) 이 있는지 확인", key);
                }
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<byte[]> get(String key, long maxBytes) {
        try {
            // Range 로 상한을 걸어 큰 객체를 통째로 받지 않는다(maxBytes+1 바이트면 초과로 판단).
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(key).range("bytes=0-" + maxBytes).build());
            return Optional.of(bytes.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void put(String key, String contentType, byte[] bytes) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
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

    @Override
    public void copy(String sourceKey, String destinationKey) {
        // 서버 쪽 복사(CopyObject)라 본문이 EC2 를 거치지 않는다. IAM 에 retained/* 쓰기(PutObject)와 reports/* 읽기(GetObject)가 필요하다.
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(sourceKey)
                .destinationBucket(bucket).destinationKey(destinationKey)
                .build());
    }
}
