package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.mapdata.dto.AdminMapFacility;
import com.hongmap.hongmapbackend.mapdata.dto.MapAffiliationBenefit;
import com.hongmap.hongmapbackend.mapdata.dto.MapBuilding;
import com.hongmap.hongmapbackend.mapdata.dto.MapExhibition;
import com.hongmap.hongmapbackend.mapdata.dto.MapFacility;
import com.hongmap.hongmapbackend.mapdata.dto.MapLink;
import com.hongmap.hongmapbackend.mapdata.dto.MapPartner;
import com.hongmap.hongmapbackend.partner.entity.Partner;
import com.hongmap.hongmapbackend.partner.entity.PartnerAffiliation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 엔티티 → 지도 데이터 응답 모양. 연관(소속·건물)은 호출하는 쪽이 fetch join 으로 미리 읽어 둔다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapDataMapper {

    private final JsonMapper jsonMapper;

    public MapBuilding building(Building b) {
        return new MapBuilding(
                b.getId(),
                b.getCode(),
                b.getMapName(),
                toDouble(b.getLatitude()),
                toDouble(b.getLongitude()),
                b.getColor(),
                b.getMapCategory(),
                b.getType(),
                b.getFloors(),
                b.getBasementFloors(),
                b.getHours(),
                b.getDescription(),
                json(b.getFacilities(), b, "facilities"),
                b.getContact(),
                MapLink.of(b.getLinkLabel(), b.getLinkUrl()),
                json(b.getBoundary(), b, "boundary"),
                json(b.getExtraBoundaries(), b, "extra_boundaries"),
                json(b.getEntrances(), b, "entrances"));
    }

    public MapFacility facility(CampusFacility f) {
        return new MapFacility(f.getCode(), f.getKind(), f.getBuilding().getMapName(), f.getFloor(), f.getNote(),
                toDouble(f.getLatitude()), toDouble(f.getLongitude()));
    }

    public AdminMapFacility adminFacility(CampusFacility f) {
        return new AdminMapFacility(f.getCode(), f.getKind(), f.getBuilding().getCode(), f.getBuilding().getMapName(),
                f.getFloor(), f.getNote(), toDouble(f.getLatitude()), toDouble(f.getLongitude()));
    }

    public MapExhibition exhibition(Exhibition e) {
        return new MapExhibition(e.getId(), e.getFacilityCode(), e.getTitle(), e.getStartsOn().toString(),
                e.getEndsOn().toString(), e.getHours(), e.getDescription(), MapLink.of(e.getLinkLabel(), e.getLinkUrl()));
    }

    public MapPartner partner(Partner p) {
        List<PartnerAffiliation> affiliations = p.getAffiliations().stream()
                .sorted(Comparator.comparing(PartnerAffiliation::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<String> names = affiliations.stream().map(PartnerAffiliation::getAffiliation).toList();
        List<MapAffiliationBenefit> exceptions = affiliations.stream()
                .filter(a -> a.getBenefit() != null)
                .map(a -> new MapAffiliationBenefit(a.getAffiliation(), a.getBenefit()))
                .toList();
        return new MapPartner(
                partnerId(p),
                p.getName(),
                p.getCategory(),
                names.isEmpty() ? null : names,
                p.getMapIcon(),
                toDouble(p.getLatitude()),
                toDouble(p.getLongitude()),
                p.getBenefit(),
                exceptions.isEmpty() ? null : exceptions,
                p.getAddress(),
                p.getHours(),
                p.getContact(),
                MapLink.of(p.getLinkLabel(), p.getLinkUrl()));
    }

    /** code 가 없는 행(동기화 SQL 전, 또는 예전 POST /partners 로 넣은 행)은 DB id 로 대신한다 — 관리자 화면에서는 고칠 수 없다. */
    static String partnerId(Partner p) {
        return p.getCode() != null ? p.getCode() : "legacy-" + p.getId();
    }

    private JsonNode json(String raw, Building b, String column) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = jsonMapper.readTree(raw);
            if (node == null || node.isNull() || node.isMissingNode() || (node.isArray() && node.isEmpty())) {
                return null;
            }
            return node;
        } catch (JacksonException e) {
            // 잘못 들어간 값 하나 때문에 지도 전체가 안 뜨지 않게 그 필드만 뺀다.
            log.warn("건물 JSON 컬럼 파싱 실패 → 생략 (buildingId={}, column={})", b.getId(), column);
            return null;
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
