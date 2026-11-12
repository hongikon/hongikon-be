package com.hongmap.hongmapbackend.walkroute;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 길찾기 경로망 버전(11-10). 경로 조사 도구가 올린 경로망을 초안으로 저장하면서 검사·비교하고, 운영에 적용한 하나(ACTIVE)를 길찾기가 쓴다.
 * ACTIVE 가 없으면 저장소 파일(resources/walkroute/survey-graph-v3.json). 적용하면 배포 없이 바로 바뀌고, 예전 버전을 다시 적용해 되돌린다.
 */
@Service
public class WalkGraphService {

    /** 경로망·백업 JSON 한 개의 최대 글자 수. 지금 경로망은 약 13만 자. */
    static final int MAX_JSON_CHARS = 5_000_000;
    static final int MAX_VERTICES = 20_000;
    static final int MAX_EDGES = 60_000;
    /** 적용 중인 것 말고 남겨 두는 버전 수(오래된 것부터 지운다). */
    static final int KEEP_VERSIONS = 30;
    /** 비교표에 적는 크게 바뀐 길 수. */
    static final int TOP_CHANGES = 20;
    /** ACTIVE 가 바뀌었는지 DB 를 다시 보는 간격(직접 SQL 로 바꾼 경우 등). 조사 도구에서 적용하면 바로 반영된다. */
    private static final long ACTIVE_RECHECK_NANOS = 60_000_000_000L;

    public record ActiveGraph(Long versionId, SurveyGraph graph) {
        /** 길찾기 캐시 열쇠: 저장소 파일이면 "file", 아니면 "v{id}". */
        String key() {
            return versionId == null ? "file" : "v" + versionId;
        }
    }

    public record UploadRequest(String sourceName, JsonNode graph, JsonNode backup) {
    }

    /** 검사·비교 결과. 조사 도구에 그대로 보여 준다. */
    public record Report(Checks checks, Comparison comparison) {
    }

    public record Checks(int vertices, int edges, int detachedVertices, List<String> detachedSample, List<String> unresolvedBuildingCodes,
                         List<String> buildingsWithoutEntrances) {
    }

    /** 운영(ACTIVE 또는 저장소 파일) 경로망과 건물 쌍마다(층 안 고름) 가장 빠른 길 비교. 초 단위. */
    public record Comparison(String against, int pairs, int same, int faster, int slower, int becameEstimate, int becameReal,
                             List<Change> biggest) {
    }

    public record Change(String from, String to, double activeSeconds, double draftSeconds, boolean activeEstimate, boolean draftEstimate) {
    }

    public record VersionView(Long id, WalkGraphVersion.Status status, String sourceName, String exportedAt, int vertexCount, int edgeCount,
                              LocalDateTime createdAt, LocalDateTime activatedAt, Report report) {
    }

    public record VersionList(String activeSource, String bundledExportedAt, List<WalkGraphVersionRepository.Summary> versions) {
    }

    private record CachedGraph(long id, SurveyGraph graph) {
    }

    private final WalkGraphVersionRepository repository;
    private final CampusBuildings campusBuildings;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final SurveyGraph bundled;
    /** 비교 계산(건물 쌍 전부, 수 초)을 한 번에 하나만. */
    private final Semaphore analyzeSlot = new Semaphore(1, true);
    private volatile ActiveGraph active;
    private volatile long activeCheckedAt;
    private volatile CachedGraph lastLoaded;

    public WalkGraphService(WalkGraphVersionRepository repository, CampusBuildings campusBuildings, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.campusBuildings = campusBuildings;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.bundled = SurveyGraph.load(jsonMapper);
    }

    /** 길찾기가 쓰는 경로망. */
    public ActiveGraph active() {
        ActiveGraph a = active;
        if (a != null && System.nanoTime() - activeCheckedAt < ACTIVE_RECHECK_NANOS) {
            return a;
        }
        synchronized (this) {
            a = active;
            if (a != null && System.nanoTime() - activeCheckedAt < ACTIVE_RECHECK_NANOS) {
                return a;
            }
            Long id = repository.findFirstByStatusOrderByIdDesc(WalkGraphVersion.Status.ACTIVE).map(WalkGraphVersion::getId).orElse(null);
            if (a == null || !Objects.equals(a.versionId(), id)) {
                a = id == null ? new ActiveGraph(null, bundled) : new ActiveGraph(id, graphOf(id));
            }
            active = a;
            activeCheckedAt = System.nanoTime();
            return a;
        }
    }

    /** 한 버전의 경로망(마지막으로 읽은 것은 기억). */
    SurveyGraph graphOf(long id) {
        CachedGraph c = lastLoaded;
        if (c != null && c.id() == id) {
            return c.graph();
        }
        SurveyGraph graph = SurveyGraph.parse(jsonMapper.readTree(find(id).getGraph()));
        lastLoaded = new CachedGraph(id, graph);
        return graph;
    }

    @Transactional
    public VersionView upload(UploadRequest request) {
        if (request == null || request.graph() == null || !request.graph().isObject()) {
            throw badRequest("graph(경로망 JSON)가 없습니다");
        }
        JsonNode backupNode = request.backup() == null || request.backup().isNull() ? null : request.backup();
        String graphJson = jsonMapper.writeValueAsString(request.graph());
        String backupJson = backupNode == null ? null : jsonMapper.writeValueAsString(backupNode);
        if (graphJson.length() > MAX_JSON_CHARS || (backupJson != null && backupJson.length() > MAX_JSON_CHARS)) {
            throw badRequest("경로망·백업이 너무 큽니다");
        }
        if (backupNode != null && (!"hongikon.pathSurvey".equals(backupNode.path("format").asString(""))
                || backupNode.path("version").asInt() != 3)) {
            throw badRequest("backup 이 경로망 조사 v3 백업(format hongikon.pathSurvey, version 3)이 아닙니다");
        }
        SurveyGraph graph;
        try {
            graph = SurveyGraph.parse(request.graph());
        } catch (RuntimeException e) {
            throw badRequest("경로망 JSON 모양이 맞지 않습니다(exportedAt, vertices, edges, nodeMemos, edgeMemos)");
        }
        if (graph.vertices().isEmpty() || graph.vertices().size() > MAX_VERTICES || graph.edges().size() > MAX_EDGES) {
            throw badRequest("정점·간선 수가 맞지 않습니다(정점 1~" + MAX_VERTICES + ", 간선 ~" + MAX_EDGES + ")");
        }
        Report report = analyze(graph);
        String sourceName = request.sourceName() == null ? null : request.sourceName().strip();
        if (sourceName != null && sourceName.length() > 200) {
            sourceName = sourceName.substring(0, 200);
        }
        WalkGraphVersion saved = repository.save(new WalkGraphVersion(sourceName, graph.exportedAt(), graph.vertices().size(),
                graph.edges().size(), graphJson, backupJson, jsonMapper.writeValueAsString(report), now()));
        prune();
        return view(saved);
    }

    /** 운영에 적용(이전 ACTIVE 는 ARCHIVED). 커밋 뒤 길찾기가 바로 이 경로망을 쓴다. */
    @Transactional
    public VersionView activate(long id) {
        WalkGraphVersion version = find(id);
        for (WalkGraphVersion current : repository.findAllByStatus(WalkGraphVersion.Status.ACTIVE)) {
            if (!current.getId().equals(id)) {
                current.archive();
            }
        }
        version.activate(now());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    forgetActive();
                }
            });
        } else {
            forgetActive();
        }
        return view(version);
    }

    private synchronized void forgetActive() {
        active = null;
    }

    @Transactional(readOnly = true)
    public VersionList list() {
        return new VersionList(active().key(), bundled.exportedAt(), repository.findSummaries(PageRequest.of(0, KEEP_VERSIONS + 1)));
    }

    @Transactional(readOnly = true)
    public VersionView get(long id) {
        return view(find(id));
    }

    /** 조사 도구로 다시 불러올 백업 원본. 없으면 404. */
    @Transactional(readOnly = true)
    public String backup(long id) {
        String backup = find(id).getBackup();
        if (backup == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이 버전에는 백업이 없습니다");
        }
        return backup;
    }

    /** 검사(떨어진 조각·서버 건물로 이어지지 않는 code·출입구 없는 건물)와 운영 경로망과의 비교(건물 쌍마다 층 안 고른 가장 빠른 길). */
    Report analyze(SurveyGraph draft) {
        boolean acquired = false;
        try {
            acquired = analyzeSlot.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "다른 경로망을 검사하는 중입니다");
            }
            List<CampusBuilding> buildings = campusBuildings.current().list();
            WalkRouter draftRouter = new WalkRouter(draft, buildings);
            ActiveGraph against = active();
            WalkRouter activeRouter = new WalkRouter(against.graph(), buildings);
            List<String> detached = draftRouter.detachedVertexIds();
            Checks checks = new Checks(draftRouter.vertexCount(), draftRouter.edgeCount(), detached.size(),
                    List.copyOf(detached.subList(0, Math.min(20, detached.size()))), List.copyOf(draftRouter.unresolvedBuildingCodes()),
                    draftRouter.buildingsWithoutEntrances());
            int pairs = 0;
            int same = 0;
            int faster = 0;
            int slower = 0;
            int becameEstimate = 0;
            int becameReal = 0;
            List<Change> changes = new ArrayList<>();
            for (CampusBuilding from : buildings) {
                for (CampusBuilding to : buildings) {
                    if (from == to) {
                        continue;
                    }
                    pairs++;
                    WalkRoute a = activeRouter.routes(from, null, to, null).get(0);
                    WalkRoute d = draftRouter.routes(from, null, to, null).get(0);
                    if (!a.isEstimate() && d.isEstimate()) {
                        becameEstimate++;
                    } else if (a.isEstimate() && !d.isEstimate()) {
                        becameReal++;
                    }
                    double diff = d.totalSeconds() - a.totalSeconds();
                    if (Math.abs(diff) < 1) {
                        same++;
                    } else if (diff < 0) {
                        faster++;
                    } else {
                        slower++;
                    }
                    if (Math.abs(diff) >= 1 || a.isEstimate() != d.isEstimate()) {
                        changes.add(new Change(from.name(), to.name(), round1(a.totalSeconds()), round1(d.totalSeconds()), a.isEstimate(),
                                d.isEstimate()));
                    }
                }
            }
            // 직선 어림으로 바뀐(길이 끊긴) 것 먼저, 그다음 많이 바뀐 순.
            changes.sort(Comparator.comparingInt((Change c) -> c.activeEstimate() != c.draftEstimate() ? 0 : 1)
                    .thenComparingDouble(c -> -Math.abs(c.draftSeconds() - c.activeSeconds())));
            Comparison comparison = new Comparison(against.key(), pairs, same, faster, slower, becameEstimate, becameReal,
                    List.copyOf(changes.subList(0, Math.min(TOP_CHANGES, changes.size()))));
            return new Report(checks, comparison);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "검사가 끊겼습니다");
        } finally {
            if (acquired) {
                analyzeSlot.release();
            }
        }
    }

    private void prune() {
        int kept = 0;
        for (WalkGraphVersionRepository.Summary s : repository.findSummaries(PageRequest.of(0, 1000))) {
            if (s.status() == WalkGraphVersion.Status.ACTIVE) {
                continue;
            }
            if (++kept > KEEP_VERSIONS) {
                repository.deleteById(s.id());
            }
        }
    }

    private WalkGraphVersion find(long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "경로망 버전을 찾을 수 없습니다"));
    }

    private VersionView view(WalkGraphVersion v) {
        Report report = v.getReport() == null ? null : jsonMapper.readValue(v.getReport(), Report.class);
        return new VersionView(v.getId(), v.getStatus(), v.getSourceName(), v.getExportedAt(), v.getVertexCount(), v.getEdgeCount(),
                v.getCreatedAt(), v.getActivatedAt(), report);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
