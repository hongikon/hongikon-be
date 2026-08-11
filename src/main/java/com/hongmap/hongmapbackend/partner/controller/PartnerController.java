package com.hongmap.hongmapbackend.partner.controller;

import com.hongmap.hongmapbackend.partner.dto.*;
import com.hongmap.hongmapbackend.partner.service.PartnerService;
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

    @GetMapping
    public ResponseEntity<PartnerListResponse> findAll(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String affiliation
    ) {
        return ResponseEntity.ok(partnerService.findAll(category, affiliation));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(partnerService.findById(id));
    }

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

    @PostMapping
    public ResponseEntity<PartnerResponse> create(@Valid @RequestBody PartnerCreateRequest request) {
        PartnerResponse response = partnerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
