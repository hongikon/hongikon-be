package com.hongmap.hongmapbackend.report.image;

import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.dto.ReportImageUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 제보 사진 정책.
 * <ol>
 *   <li>앱이 {@code POST /reports/images} 로 업로드 URL 을 받는다 — 키는 서버가 {@code reports/{uuid}.jpg|png} 로 정한다.</li>
 *   <li>앱이 그 URL 로 S3 에 직접 PUT 한다(EC2 를 거치지 않음).</li>
 *   <li>{@code POST /reports} 에 {@code imageKey} 를 실어 보내면, 서버가 키 형식·실제 업로드 여부·크기·형식을 확인하고,
 *       본문을 받아 매직 바이트 확인과 메타데이터 제거를 한 뒤 새 키로 저장한다({@link #validateForAttach}).</li>
 * </ol>
 * 보기 URL 은 응답할 때마다 presigned GET 으로 새로 만든다(버킷 비공개).
 */
@Slf4j
@Service
public class ReportImageService {

    public static final String KEY_PREFIX = "reports/";
    /** 서버가 발급한 키만 받는다. 다른 접두사·경로 조작(../)·임의 확장자는 여기서 걸러진다. */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^reports/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");
    /** 앱은 ImagePicker 에서 JPEG 로 압축해 보낸다. PNG 는 웹·스크린숏 대비. */
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png");
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final ReportImageStorage storage;
    private final ReportRepository reportRepository;
    private final long maxBytes;
    private final Duration uploadUrlTtl;
    private final Duration viewUrlTtl;
    private final int uploadLimitPerHour;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> issuedByUser = new ConcurrentHashMap<>();

    @Autowired
    public ReportImageService(
            ReportImageStorage storage,
            ReportRepository reportRepository,
            @Value("${app.report-image.max-bytes:5242880}") long maxBytes,
            @Value("${app.report-image.upload-url-ttl-seconds:300}") long uploadUrlTtlSeconds,
            @Value("${app.report-image.view-url-ttl-seconds:3600}") long viewUrlTtlSeconds,
            @Value("${app.report-image.upload-limit-per-hour:20}") int uploadLimitPerHour
    ) {
        this(storage, reportRepository, maxBytes, Duration.ofSeconds(uploadUrlTtlSeconds),
                Duration.ofSeconds(viewUrlTtlSeconds), uploadLimitPerHour, Clock.systemUTC());
    }

    ReportImageService(ReportImageStorage storage, ReportRepository reportRepository, long maxBytes,
                       Duration uploadUrlTtl, Duration viewUrlTtl, int uploadLimitPerHour, Clock clock) {
        this.storage = storage;
        this.reportRepository = reportRepository;
        this.maxBytes = maxBytes;
        this.uploadUrlTtl = uploadUrlTtl;
        this.viewUrlTtl = viewUrlTtl;
        this.uploadLimitPerHour = uploadLimitPerHour;
        this.clock = clock;
    }

    public ReportImageUploadResponse issueUploadUrl(Long userId, String rawContentType) {
        return issueUploadUrl(userId, rawContentType, null);
    }

    /**
     * @param contentLength 올릴 바이트 수(선택). 주면 presigned PUT 에 Content-Length 를 서명해 S3 가 다른 크기를 거절한다.
     */
    public ReportImageUploadResponse issueUploadUrl(Long userId, String rawContentType, Long contentLength) {
        if (!storage.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "사진 첨부를 아직 사용할 수 없어요.");
        }
        String contentType = rawContentType == null ? "" : rawContentType.trim().toLowerCase(Locale.ROOT);
        String extension = EXTENSION_BY_TYPE.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPEG 또는 PNG 사진만 올릴 수 있어요.");
        }
        if (contentLength != null && (contentLength <= 0 || contentLength > maxBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 용량이 너무 커요.");
        }
        acquireQuota(userId);

        String key = KEY_PREFIX + UUID.randomUUID() + "." + extension;
        ReportImageStorage.PresignedUpload upload = storage.presignUpload(key, contentType, contentLength, uploadUrlTtl);
        return new ReportImageUploadResponse(key, upload.url(), "PUT", upload.headers(), upload.expiresAt(), maxBytes);
    }

    /**
     * 제보 등록 직전에 부른다(트랜잭션 안). 키가 서버 발급 형식인지, 실제로 올라갔는지, 크기·형식이 맞는지 확인한 뒤
     * <b>서버에서 메타데이터를 지운 사본을 새 키로 저장하고 그 키를 돌려준다.</b>
     * <ul>
     *   <li>앱이 GPS 를 지워 보내지만 믿지 않는다. 본문을 받아(≤ maxBytes) 매직 바이트(JPEG FFD8FF / PNG 89504E47)를
     *       확인하고 {@link ImageMetadataSanitizer} 로 EXIF·XMP·텍스트 청크 등을 지운다. 형식이 틀리면 원본을 지우고 400.</li>
     *   <li>새 키에 저장하는 이유: 앱이 받은 presigned PUT URL(최대 5분 유효)은 원래 키에만 쓸 수 있다.
     *       같은 키에 덮어쓰면 검사 뒤에 원본을 다시 올려 바꿔치기할 수 있지만, 새 키는 서버만 쓸 수 있다.</li>
     *   <li>커밋되면 원래 키를 지우고, 롤백되면 새 사본을 지운다(원래 키로 다시 시도 가능). 실패는 로그만 남기고
     *       S3 수명 주기 규칙(30일)이 마저 정리한다.</li>
     * </ul>
     * 실패하면 400 — 앱은 사진 없이 다시 올리거나 사진을 다시 고르게 안내한다.
     */
    public String validateForAttach(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (!storage.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 첨부를 아직 사용할 수 없어요.");
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 사진 정보예요.");
        }
        if (reportRepository.existsByImageKey(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 다른 제보에 붙은 사진이에요.");
        }
        ReportImageStorage.StoredObject stored = storage.head(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진이 아직 올라가지 않았어요."));
        if (stored.contentLength() <= 0 || stored.contentLength() > maxBytes) {
            deleteQuietly(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 용량이 너무 커요.");
        }
        String type = stored.contentType() == null ? "" : stored.contentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSION_BY_TYPE.get(type);
        if (extension == null || !key.endsWith("." + extension)) {
            deleteQuietly(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JPEG 또는 PNG 사진만 올릴 수 있어요.");
        }

        byte[] original = storage.get(key, maxBytes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진이 아직 올라가지 않았어요."));
        if (original.length > maxBytes) { // HEAD 이후 더 큰 파일로 바뀐 경우
            deleteQuietly(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 용량이 너무 커요.");
        }
        byte[] sanitized;
        try {
            sanitized = ImageMetadataSanitizer.sanitize(original, type);
        } catch (ImageMetadataSanitizer.InvalidImageException e) {
            log.info("report image rejected key={}: {}", key, e.getMessage());
            deleteQuietly(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 파일을 읽을 수 없어요. 다른 사진을 골라 주세요.");
        }

        String sanitizedKey = KEY_PREFIX + UUID.randomUUID() + "." + extension;
        storage.put(sanitizedKey, type, sanitized);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    deleteQuietly(status == STATUS_COMMITTED ? key : sanitizedKey);
                }
            });
        } else {
            deleteQuietly(key);
        }
        return sanitizedKey;
    }

    /** 응답용 보기 URL. 사진이 없거나 기능이 꺼져 있으면 null. 서명 실패가 목록 전체를 망치지 않게 삼킨다. */
    public String viewUrl(String key) {
        if (key == null || !storage.isEnabled()) {
            return null;
        }
        try {
            return storage.presignView(key, viewUrlTtl);
        } catch (RuntimeException e) {
            log.warn("report image view url signing failed: {}", e.toString());
            return null;
        }
    }

    /** DB 커밋이 끝난 뒤 S3 객체를 지운다(롤백되면 지우지 않음). 실패는 로그만 — S3 수명 주기 규칙이 마저 정리한다. */
    public void deleteAfterCommit(String key) {
        if (key == null || !storage.isEnabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        } else {
            deleteQuietly(key);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("report image delete failed key={}: {}", key, e.toString());
        }
    }

    /** 사용자별 1시간 발급 한도. 서버 1대라 메모리로 충분하다(재시작하면 초기화). */
    private void acquireQuota(Long userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(RATE_WINDOW);
        Deque<Instant> issued = issuedByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (issued) {
            while (!issued.isEmpty() && issued.peekFirst().isBefore(windowStart)) {
                issued.pollFirst();
            }
            if (issued.size() >= uploadLimitPerHour) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "사진을 너무 자주 올리고 있어요. 잠시 후 다시 시도해 주세요.");
            }
            issued.addLast(now);
        }
        if (issuedByUser.size() > 10_000) {
            issuedByUser.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    Instant last = entry.getValue().peekLast();
                    return last == null || last.isBefore(windowStart);
                }
            });
        }
    }
}
