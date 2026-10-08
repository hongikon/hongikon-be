package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapBuilding;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapBuildingListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapExhibitionListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapExhibitionRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacility;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacilityListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacilityRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapPartnerListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapPartnerRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AffiliationBenefitRequest;
import com.hongmap.hongmapbackend.mapdata.dto.MapExhibition;
import com.hongmap.hongmapbackend.mapdata.dto.MapPartner;
import com.hongmap.hongmapbackend.partner.entity.Partner;
import com.hongmap.hongmapbackend.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * 관리자 지도 데이터 편집(/admin/map/**). 쓰기가 커밋되면 GET /map/data 캐시를 바로 비운다.
 * 허용 값 검사는 MapDataRules — 실패하면 400 + ErrorResponse(message).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMapService {

    private static final int CODE_ATTEMPTS = 5;

    /** 전시를 걸 수 있는 편의시설 종류. */
    static final String EXHIBITION_FACILITY_KIND = "행사·전시";

    private final PartnerRepository partnerRepository;
    private final CampusFacilityRepository facilityRepository;
    private final BuildingRepository buildingRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final MapDataMapper mapper;
    private final MapDataService mapDataService;

    // ── 제휴업체 ─────────────────────────────────────────

    public AdminMapPartnerListResponse partners() {
        return new AdminMapPartnerListResponse(partnerRepository.findAllForMap().stream().map(mapper::partner).toList());
    }

    @Transactional
    public MapPartner createPartner(AdminMapPartnerRequest request) {
        PartnerFields f = partnerFields(request);
        String code = newCode(request.id(), MapCodes.PARTNER_PREFIX, partnerRepository::existsByCode);
        Partner partner = Partner.builder()
                .code(code)
                .sortOrder(partnerRepository.findMaxSortOrder() + 1)
                .name(f.name).category(f.category).latitude(f.lat).longitude(f.lng)
                .benefit(f.benefit).address(f.address).hours(f.hours).contact(f.contact)
                .mapIcon(f.mapIcon).linkLabel(f.linkLabel).linkUrl(f.linkUrl)
                .build();
        partner.replaceAffiliations(f.affiliations);
        Partner saved = partnerRepository.save(partner);
        mapDataService.invalidateAfterCommit();
        return mapper.partner(saved);
    }

    @Transactional
    public MapPartner updatePartner(String code, AdminMapPartnerRequest request) {
        Partner partner = partnerRepository.findWithAffiliationsByCode(code)
                .orElseThrow(() -> notFound("존재하지 않는 제휴업체예요."));
        PartnerFields f = partnerFields(request);
        partner.updateMapInfo(f.name, f.category, f.lat, f.lng, f.benefit, f.address, f.hours, f.contact,
                f.mapIcon, f.linkLabel, f.linkUrl);
        partner.replaceAffiliations(f.affiliations);
        partnerRepository.flush();
        mapDataService.invalidateAfterCommit();
        return mapper.partner(partner);
    }

    @Transactional
    public void deletePartner(String code, String confirmName) {
        Partner partner = partnerRepository.findWithAffiliationsByCode(code)
                .orElseThrow(() -> notFound("존재하지 않는 제휴업체예요."));
        // 관리자 화면이 업체 이름을 다시 입력받는다. 서버도 확인해 다른 클라이언트·실수 호출로 지워지지 않게 한다.
        if (confirmName == null || !confirmName.trim().equals(partner.getName().trim())) {
            throw badRequest("업체 이름이 일치하지 않아요.");
        }
        partnerRepository.delete(partner);
        mapDataService.invalidateAfterCommit();
    }

    private record PartnerFields(String name, String category, BigDecimal lat, BigDecimal lng, String benefit,
                                 String address, String hours, String contact, String mapIcon,
                                 String linkLabel, String linkUrl, LinkedHashMap<String, String> affiliations) {
    }

    private PartnerFields partnerFields(AdminMapPartnerRequest r) {
        String category = r.category().trim();
        if (!MapDataRules.PARTNER_CATEGORIES.contains(category)) {
            throw badRequest("지원하지 않는 분류예요.");
        }
        String mapIcon = blankToNull(r.mapIcon());
        if (mapIcon != null && !MapDataRules.PARTNER_MAP_ICONS.contains(mapIcon)) {
            throw badRequest("지원하지 않는 지도 아이콘이에요.");
        }
        String benefit = blankToNull(r.benefit());

        LinkedHashMap<String, String> affiliations = new LinkedHashMap<>();
        if (r.affiliations() != null) {
            for (String raw : r.affiliations()) {
                String a = raw == null ? "" : raw.trim();
                if (!MapDataRules.AFFILIATIONS.contains(a)) {
                    throw badRequest("지원하지 않는 소속이에요.");
                }
                if (affiliations.containsKey(a)) {
                    throw badRequest("같은 소속이 두 번 들어 있어요.");
                }
                affiliations.put(a, null);
            }
        }
        if (r.affiliationBenefits() != null) {
            for (AffiliationBenefitRequest ab : r.affiliationBenefits()) {
                String a = ab.affiliation().trim();
                if (!affiliations.containsKey(a)) {
                    throw badRequest("소속 혜택은 고른 소속에만 넣을 수 있어요.");
                }
                if (affiliations.get(a) != null) {
                    throw badRequest("같은 소속의 혜택이 두 번 들어 있어요.");
                }
                String exception = ab.benefit().trim();
                // 기본 혜택과 같으면 예외가 아니다 — NULL(기본 혜택 따름)로 둔다.
                affiliations.put(a, exception.equals(benefit) ? null : exception);
            }
        }

        String linkLabel = r.link() == null ? null : r.link().label().trim();
        String linkUrl = r.link() == null ? null : r.link().url().trim();
        return new PartnerFields(r.name().trim(), category, coord(r.lat()), coord(r.lng()), benefit,
                blankToNull(r.address()), blankToNull(r.hours()), blankToNull(r.contact()), mapIcon,
                linkLabel, linkUrl, affiliations);
    }

    // ── 편의시설 ─────────────────────────────────────────

    public AdminMapFacilityListResponse facilities() {
        return new AdminMapFacilityListResponse(facilityRepository.findAllForMap().stream().map(mapper::adminFacility).toList());
    }

    @Transactional
    public AdminMapFacility createFacility(AdminMapFacilityRequest request) {
        FacilityFields f = facilityFields(request);
        String code = newCode(request.id(), MapCodes.FACILITY_PREFIX, facilityRepository::existsByCode);
        CampusFacility saved = facilityRepository.save(CampusFacility.builder()
                .code(code).kind(f.kind).building(f.building).floor(f.floor).note(f.note)
                .latitude(f.lat).longitude(f.lng)
                .sortOrder(facilityRepository.findMaxSortOrder() + 1)
                .build());
        mapDataService.invalidateAfterCommit();
        return mapper.adminFacility(saved);
    }

    @Transactional
    public AdminMapFacility updateFacility(String code, AdminMapFacilityRequest request) {
        CampusFacility facility = facilityRepository.findByCodeWithBuilding(code)
                .orElseThrow(() -> notFound("존재하지 않는 편의시설이에요."));
        FacilityFields f = facilityFields(request);
        facility.update(f.kind, f.building, f.floor, f.note, f.lat, f.lng);
        facilityRepository.flush();
        mapDataService.invalidateAfterCommit();
        return mapper.adminFacility(facility);
    }

    @Transactional
    public void deleteFacility(String code) {
        CampusFacility facility = facilityRepository.findByCodeWithBuilding(code)
                .orElseThrow(() -> notFound("존재하지 않는 편의시설이에요."));
        facilityRepository.delete(facility);
        mapDataService.invalidateAfterCommit();
    }

    private record FacilityFields(String kind, Building building, Integer floor, String note,
                                  BigDecimal lat, BigDecimal lng) {
    }

    private FacilityFields facilityFields(AdminMapFacilityRequest r) {
        String kind = r.kind().trim();
        if (!MapDataRules.FACILITY_KINDS.contains(kind)) {
            throw badRequest("지원하지 않는 편의시설 종류예요.");
        }
        if ((r.lat() == null) != (r.lng() == null)) {
            throw badRequest("위도와 경도는 함께 입력해 주세요.");
        }
        Building building = buildingRepository.findByCode(r.buildingCode().trim())
                .orElseThrow(() -> badRequest("존재하지 않는 건물이에요."));
        return new FacilityFields(kind, building, r.floor(), blankToNull(r.note()), coord(r.lat()), coord(r.lng()));
    }

    // ── 전시(장소별 일정) ───────────────────────────────

    public AdminMapExhibitionListResponse exhibitions() {
        return new AdminMapExhibitionListResponse(
                exhibitionRepository.findAllByOrderByStartsOnDescIdDesc().stream().map(mapper::exhibition).toList());
    }

    @Transactional
    public MapExhibition createExhibition(AdminMapExhibitionRequest request) {
        ExhibitionFields f = exhibitionFields(request);
        Exhibition saved = exhibitionRepository.save(Exhibition.builder()
                .facilityCode(f.facilityCode).title(f.title).startsOn(f.startsOn).endsOn(f.endsOn)
                .hours(f.hours).description(f.description).linkLabel(f.linkLabel).linkUrl(f.linkUrl)
                .build());
        mapDataService.invalidateAfterCommit();
        return mapper.exhibition(saved);
    }

    @Transactional
    public MapExhibition updateExhibition(Long id, AdminMapExhibitionRequest request) {
        Exhibition exhibition = exhibitionRepository.findById(id)
                .orElseThrow(() -> notFound("존재하지 않는 전시예요."));
        ExhibitionFields f = exhibitionFields(request);
        exhibition.update(f.facilityCode, f.title, f.startsOn, f.endsOn, f.hours, f.description, f.linkLabel, f.linkUrl);
        exhibitionRepository.flush();
        mapDataService.invalidateAfterCommit();
        return mapper.exhibition(exhibition);
    }

    @Transactional
    public void deleteExhibition(Long id) {
        Exhibition exhibition = exhibitionRepository.findById(id)
                .orElseThrow(() -> notFound("존재하지 않는 전시예요."));
        exhibitionRepository.delete(exhibition);
        mapDataService.invalidateAfterCommit();
    }

    private record ExhibitionFields(String facilityCode, String title, LocalDate startsOn, LocalDate endsOn,
                                    String hours, String description, String linkLabel, String linkUrl) {
    }

    private ExhibitionFields exhibitionFields(AdminMapExhibitionRequest r) {
        String facilityCode = r.facilityId().trim();
        CampusFacility facility = facilityRepository.findByCodeWithBuilding(facilityCode)
                .orElseThrow(() -> badRequest("존재하지 않는 장소예요."));
        if (!EXHIBITION_FACILITY_KIND.equals(facility.getKind())) {
            throw badRequest("전시는 '" + EXHIBITION_FACILITY_KIND + "' 장소에만 등록할 수 있어요.");
        }
        String title = r.title().trim();
        LocalDate startsOn = date(r.startsOn(), "시작일");
        LocalDate endsOn = date(r.endsOn(), "종료일");
        if (startsOn.isAfter(endsOn)) {
            throw badRequest("종료일은 시작일과 같거나 뒤여야 해요.");
        }
        String linkLabel = r.link() == null ? null : r.link().label().trim();
        String linkUrl = r.link() == null ? null : r.link().url().trim();
        return new ExhibitionFields(facilityCode, title, startsOn, endsOn, blankToNull(r.hours()),
                blankToNull(r.description()), linkLabel, linkUrl);
    }

    private static LocalDate date(String value, String name) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeException e) {
            throw badRequest(name + "이 올바른 날짜가 아니에요.");
        }
    }

    // ── 건물(선택 목록) ─────────────────────────────────

    public AdminMapBuildingListResponse buildings() {
        List<AdminMapBuilding> buildings = buildingRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(b -> new AdminMapBuilding(b.getId(), b.getCode(), b.getMapName()))
                .toList();
        return new AdminMapBuildingListResponse(buildings);
    }

    // ── 공통 ─────────────────────────────────────────────

    /** 요청 id 가 있으면 그대로(이미 있으면 409), 없으면 prefix + 무작위 8자(겹치면 다시). */
    private static String newCode(String requested, String prefix, Predicate<String> exists) {
        String code = blankToNull(requested);
        if (code != null) {
            if (exists.test(code)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 있는 id 예요.");
            }
            return code;
        }
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String generated = MapCodes.generate(prefix);
            if (!exists.test(generated)) {
                return generated;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "id 를 만들지 못했어요. 다시 시도해 주세요.");
    }

    /** DECIMAL(10,7) 에 맞춰 소수 7자리로. */
    private static BigDecimal coord(BigDecimal value) {
        return value == null ? null : value.setScale(7, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
