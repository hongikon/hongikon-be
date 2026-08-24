package com.hongmap.hongmapbackend.partner.controller;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.partner.dto.*;
import com.hongmap.hongmapbackend.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "제휴업체 목록 조회", description = "카테고리, 제휴 유형으로 필터링된 제휴업체 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<PartnerListResponse> findAll(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String affiliation
    ) {
        return ResponseEntity.ok(partnerService.findAll(category, affiliation));
    }

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "제휴업체 상세 조회", description = "제휴업체 id로 특정 제휴업체의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(partnerService.findById(id));
    }

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "지도 범위 내 제휴업체 조회", description = "지도 화면의 좌표 범위(남서/북동) 안에 위치한 제휴업체 목록을 조회합니다.")
    @GetMapping("/map")
    public ResponseEntity<PartnerMapResponse> findWithinBounds(
        @RequestParam BigDecimal swLat,
        @RequestParam BigDecimal neLat,
        @RequestParam BigDecimal swLng,
        @RequestParam BigDecimal neLng
    ) {
        return ResponseEntity.ok(
            partnerService.findWithinBounds(swLat, neLat, swLng, neLng)
        );
    }

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "제휴업체 등록", description = "새로운 제휴업체 정보를 등록합니다.")
    @PostMapping
    public ResponseEntity<PartnerResponse> create(@Valid @RequestBody PartnerCreateRequest request) {
        PartnerResponse response = partnerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "제휴업체 삭제", description = "제휴업체 정보를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
