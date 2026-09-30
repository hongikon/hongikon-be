package com.hongmap.hongmapbackend.partner.service;

import com.hongmap.hongmapbackend.partner.dto.*;
import com.hongmap.hongmapbackend.partner.entity.Partner;
import com.hongmap.hongmapbackend.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Partner partner = partnerRepository.findWithAffiliationsById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제휴업체입니다. id=" + id));
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
            Set<String> seen = new HashSet<>();
            for (PartnerAffiliationRequest affiliation : request.affiliations()) {
                if (!seen.add(affiliation.affiliation())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "중복된 소속입니다: " + affiliation.affiliation());
                }
                partner.addAffiliation(affiliation.affiliation(), affiliation.benefit());
            }
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제휴업체입니다. id=" + id));
    }
}
