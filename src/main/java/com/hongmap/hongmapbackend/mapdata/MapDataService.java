package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.mapdata.dto.MapDataPayload;
import com.hongmap.hongmapbackend.mapdata.dto.MapDataResponse;
import com.hongmap.hongmapbackend.partner.repository.PartnerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GET /map/data 본문을 조립해 메모리에 캐시한다(서버 1대 기준).
 * - 쿼리 3번: 건물, 편의시설+건물(fetch join), 제휴업체+소속(fetch join).
 * - 캐시는 최대 ttl(기본 5분). 관리자 쓰기는 커밋 직후 {@link #invalidateAfterCommit()} 로 바로 비운다.
 *   DB 를 SQL 로 직접 고친 경우(동기화 스크립트 등)는 ttl 이 지나면 반영된다.
 * - 조립 도중 무효화되면(세대 번호가 바뀌면) 그 결과는 응답에만 쓰고 캐시에 넣지 않는다 — 커밋 전 값을 5분간 들고 있지 않게.
 */
@Service
public class MapDataService {

    private final BuildingRepository buildingRepository;
    private final CampusFacilityRepository facilityRepository;
    private final PartnerRepository partnerRepository;
    private final MapDataMapper mapper;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate readOnlyTx;
    private final long ttlNanos;

    private final AtomicLong generation = new AtomicLong();
    private final Object buildLock = new Object();
    private volatile Cached cached;

    public MapDataService(BuildingRepository buildingRepository,
                          CampusFacilityRepository facilityRepository,
                          PartnerRepository partnerRepository,
                          MapDataMapper mapper,
                          JsonMapper jsonMapper,
                          PlatformTransactionManager transactionManager,
                          @Value("${map-data.cache-ttl-seconds:300}") long ttlSeconds) {
        this.buildingRepository = buildingRepository;
        this.facilityRepository = facilityRepository;
        this.partnerRepository = partnerRepository;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.ttlNanos = Duration.ofSeconds(Math.min(Math.max(ttlSeconds, 0), 300)).toNanos();
    }

    /** 직렬화된 응답 본문과 그 version. */
    public record Snapshot(String version, byte[] body) {
    }

    private record Cached(Snapshot snapshot, long builtAtNanos, long generation) {
    }

    public Snapshot current() {
        Cached c = cached;
        if (isFresh(c)) {
            return c.snapshot();
        }
        synchronized (buildLock) {
            c = cached;
            if (isFresh(c)) {
                return c.snapshot();
            }
            long gen = generation.get();
            long now = System.nanoTime();
            Snapshot snapshot = build();
            if (generation.get() == gen) {
                cached = new Cached(snapshot, now, gen);
            }
            return snapshot;
        }
    }

    /** 캐시를 바로 비운다. */
    public void invalidate() {
        generation.incrementAndGet();
        cached = null;
    }

    /** 트랜잭션 안이면 커밋 직후에, 밖이면 지금 비운다. 관리자 쓰기 서비스가 부른다. */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private boolean isFresh(Cached c) {
        return c != null && c.generation() == generation.get() && System.nanoTime() - c.builtAtNanos() < ttlNanos;
    }

    private Snapshot build() {
        MapDataPayload payload = readOnlyTx.execute(status -> new MapDataPayload(
                buildingRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(mapper::building).toList(),
                facilityRepository.findAllForMap().stream().map(mapper::facility).toList(),
                partnerRepository.findAllForMap().stream().map(mapper::partner).toList()));
        String version = version(jsonMapper.writeValueAsBytes(payload));
        byte[] body = jsonMapper.writeValueAsBytes(MapDataResponse.of(version, payload));
        return new Snapshot(version, body);
    }

    static String version(byte[] payloadJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payloadJson);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }
}
