package com.hongmap.hongmapbackend.partner.service;

import com.hongmap.hongmapbackend.partner.dto.*;
import com.hongmap.hongmapbackend.partner.entity.Partner;
import com.hongmap.hongmapbackend.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;

    public PartnerListResponse findAll(String category, String affiliation) {
        List<Partner> partners;
        if (category != null) {
            partners = partnerRepository.findByCategory(category);
        } else if (affiliation != null) {
            partners = partnerRepository.findByAffiliation(affiliation);
        } else {
            partners = partnerRepository.findAll();
        }

        List<PartnerResponse> responses = partners.stream()
            .map(PartnerResponse::from)
            .toList();

        return PartnerListResponse.of(responses);
    }

    public PartnerResponse findById(Long id) {
        Partner partner = getPartnerOrThrow(id);
        return PartnerResponse.from(partner);
    }

    public PartnerMapResponse findWithinBounds(
        BigDecimal swLat, BigDecimal neLat, BigDecimal swLng, BigDecimal neLng
    ) {
        List<PartnerSummaryResponse> responses = partnerRepository
            .findWithinBounds(swLat, neLat, swLng, neLng)
            .stream()
            .map(PartnerSummaryResponse::from)
            .toList();

        return PartnerMapResponse.of(responses);
    }

    @Transactional
    public PartnerResponse create(PartnerCreateRequest request) {
        Partner partner = Partner.builder()
            .name(request.name())
            .category(request.category())
            .latitude(request.latitude())
            .longitude(request.longitude())
            .benefit(request.benefit())
            .address(request.address())
            .roadAddress(request.roadAddress())
            .hours(request.hours())
            .contact(request.contact())
            .mapIcon(request.mapIcon())
            .linkLabel(request.linkLabel())
            .linkUrl(request.linkUrl())
            .build();

        if (request.affiliations() != null) {
            request.affiliations().forEach(partner::addAffiliation);
        }

        Partner saved = partnerRepository.save(partner);
        return PartnerResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Partner partner = getPartnerOrThrow(id);
        partnerRepository.delete(partner);
    }

    private Partner getPartnerOrThrow(Long id) {
        return partnerRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제휴업체입니다. id=" + id));
    }
}
