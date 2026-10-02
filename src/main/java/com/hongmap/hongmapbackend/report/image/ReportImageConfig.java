package com.hongmap.hongmapbackend.report.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** AWS_S3_BUCKET 이 있으면 S3, 없으면 기능을 끈 구현을 쓴다. */
@Configuration
public class ReportImageConfig {

    @Bean(destroyMethod = "")
    public ReportImageStorage reportImageStorage(
            @Value("${app.report-image.bucket:}") String bucket,
            @Value("${app.report-image.region:ap-northeast-2}") String region
    ) {
        if (bucket == null || bucket.isBlank()) {
            return new DisabledReportImageStorage();
        }
        Region awsRegion = Region.of(region);
        // 자격 증명은 기본 체인(환경 변수 → ~/.aws → 컨테이너/EC2 인스턴스 역할) 순서로 찾는다.
        S3Client s3 = S3Client.builder().region(awsRegion).build();
        S3Presigner presigner = S3Presigner.builder().region(awsRegion).build();
        return new S3ReportImageStorage(s3, presigner, bucket.trim());
    }
}
