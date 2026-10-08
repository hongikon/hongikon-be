package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapBuildingListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapExhibitionListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapExhibitionRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacility;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacilityListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacilityRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapPartnerListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapPartnerRequest;
import com.hongmap.hongmapbackend.mapdata.dto.MapExhibition;
import com.hongmap.hongmapbackend.mapdata.dto.MapPartner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 데이터(제휴업체·편의시설·전시) 관리. ADMIN 전용 — SecurityConfig 의 /admin/** 규칙, 접속 기록은 AdminAuditInterceptor.
 * 쓰기가 성공하면 GET /map/data 가 바로 새 값을 준다.
 */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequestMapping("/admin/map")
@RequiredArgsConstructor
public class AdminMapController {

    private final AdminMapService adminMapService;

    @Operation(summary = "지도 제휴업체 목록", description = "map/data 의 partners 와 같은 모양.")
    @GetMapping("/partners")
    public AdminMapPartnerListResponse partners() {
        return adminMapService.partners();
    }

    @Operation(summary = "지도 제휴업체 추가", description = "id 를 비우면 p-xxxxxxxx 를 만든다. 같은 id 가 있으면 409.")
    @PostMapping("/partners")
    public ResponseEntity<MapPartner> createPartner(@AuthenticationPrincipal Long adminId,
                                                    @Valid @RequestBody AdminMapPartnerRequest request) {
        MapPartner saved = adminMapService.createPartner(request);
        log.info("지도 제휴업체 추가: adminId={}, code={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "지도 제휴업체 수정", description = "경로의 code 로 찾아 본문 내용으로 통째로 바꾼다(본문 id 는 무시).")
    @PutMapping("/partners/{code}")
    public MapPartner updatePartner(@AuthenticationPrincipal Long adminId, @PathVariable String code,
                                    @Valid @RequestBody AdminMapPartnerRequest request) {
        MapPartner saved = adminMapService.updatePartner(code, request);
        log.info("지도 제휴업체 수정: adminId={}, code={}", adminId, code);
        return saved;
    }

    @Operation(summary = "지도 제휴업체 삭제",
            description = "실수 삭제 방지: confirmName 에 업체 이름을 정확히 넣어야 한다(앞뒤 공백만 무시). 다르면 400.")
    @DeleteMapping("/partners/{code}")
    public ResponseEntity<Void> deletePartner(@AuthenticationPrincipal Long adminId, @PathVariable String code,
                                              @RequestParam(required = false) String confirmName) {
        adminMapService.deletePartner(code, confirmName);
        log.info("지도 제휴업체 삭제: adminId={}, code={}", adminId, code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "지도 편의시설 목록", description = "map/data 의 facilities 모양 + buildingCode.")
    @GetMapping("/facilities")
    public AdminMapFacilityListResponse facilities() {
        return adminMapService.facilities();
    }

    @Operation(summary = "지도 편의시설 추가", description = "id 를 비우면 f-xxxxxxxx 를 만든다. 같은 id 가 있으면 409.")
    @PostMapping("/facilities")
    public ResponseEntity<AdminMapFacility> createFacility(@AuthenticationPrincipal Long adminId,
                                                           @Valid @RequestBody AdminMapFacilityRequest request) {
        AdminMapFacility saved = adminMapService.createFacility(request);
        log.info("지도 편의시설 추가: adminId={}, code={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "지도 편의시설 수정", description = "경로의 code 로 찾아 본문 내용으로 통째로 바꾼다(본문 id 는 무시).")
    @PutMapping("/facilities/{code}")
    public AdminMapFacility updateFacility(@AuthenticationPrincipal Long adminId, @PathVariable String code,
                                           @Valid @RequestBody AdminMapFacilityRequest request) {
        AdminMapFacility saved = adminMapService.updateFacility(code, request);
        log.info("지도 편의시설 수정: adminId={}, code={}", adminId, code);
        return saved;
    }

    @Operation(summary = "지도 편의시설 삭제")
    @DeleteMapping("/facilities/{code}")
    public ResponseEntity<Void> deleteFacility(@AuthenticationPrincipal Long adminId, @PathVariable String code) {
        adminMapService.deleteFacility(code);
        log.info("지도 편의시설 삭제: adminId={}, code={}", adminId, code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "지도 전시 목록", description = "지난 전시 포함 전부, 시작일 최근 순. map/data 의 exhibitions 와 같은 모양.")
    @GetMapping("/exhibitions")
    public AdminMapExhibitionListResponse exhibitions() {
        return adminMapService.exhibitions();
    }

    @Operation(summary = "지도 전시 추가", description = "facilityId 는 kind '행사·전시' 인 편의시설 id. 날짜는 yyyy-MM-dd(KST, 양 끝 포함).")
    @PostMapping("/exhibitions")
    public ResponseEntity<MapExhibition> createExhibition(@AuthenticationPrincipal Long adminId,
                                                          @Valid @RequestBody AdminMapExhibitionRequest request) {
        MapExhibition saved = adminMapService.createExhibition(request);
        log.info("지도 전시 추가: adminId={}, id={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "지도 전시 수정", description = "경로의 id 로 찾아 본문 내용으로 통째로 바꾼다.")
    @PutMapping("/exhibitions/{id}")
    public MapExhibition updateExhibition(@AuthenticationPrincipal Long adminId, @PathVariable Long id,
                                          @Valid @RequestBody AdminMapExhibitionRequest request) {
        MapExhibition saved = adminMapService.updateExhibition(id, request);
        log.info("지도 전시 수정: adminId={}, id={}", adminId, id);
        return saved;
    }

    @Operation(summary = "지도 전시 삭제")
    @DeleteMapping("/exhibitions/{id}")
    public ResponseEntity<Void> deleteExhibition(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        adminMapService.deleteExhibition(id);
        log.info("지도 전시 삭제: adminId={}, id={}", adminId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "건물 목록(편의시설 편집용)", description = "{ id, code, name } — name 은 표시 이름.")
    @GetMapping("/buildings")
    public AdminMapBuildingListResponse buildings() {
        return adminMapService.buildings();
    }
}
