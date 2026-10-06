package com.hongmap.hongmapbackend.partner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Entity
@Table(name = "partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "category", length = 30, nullable = false)
    private String category;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "benefit", length = 255)
    private String benefit;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "hours", length = 100)
    private String hours;

    @Column(name = "contact", length = 50)
    private String contact;

    @Column(name = "map_icon", length = 20)
    private String mapIcon;

    @Column(name = "link_label", length = 50)
    private String linkLabel;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 앱에서 쓰는 업체 식별자(slug, 예: cafe-sunny-house). GET /map/data 의 partner.id. 동기화 전 기존 행은 NULL. */
    @Column(name = "code", length = 100, unique = true)
    private String code;

    /** GET /map/data 정렬 순서(작은 값 먼저, 같으면 id 순) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "partner", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PartnerAffiliation> affiliations = new ArrayList<>();

    @Builder
    public Partner(String name, String category, BigDecimal latitude, BigDecimal longitude,
                    String benefit, String address, String roadAddress, String hours,
                    String contact, String mapIcon, String linkLabel, String linkUrl,
                    String code, Integer sortOrder) {
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.benefit = benefit;
        this.address = address;
        this.roadAddress = roadAddress;
        this.hours = hours;
        this.contact = contact;
        this.mapIcon = mapIcon;
        this.linkLabel = linkLabel;
        this.linkUrl = linkUrl;
        this.code = code;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /** 관리자 지도 편집(PUT /admin/map/partners/{code}) — code·sortOrder·roadAddress 는 그대로 둔다. */
    public void updateMapInfo(String name, String category, BigDecimal latitude, BigDecimal longitude,
                              String benefit, String address, String hours, String contact, String mapIcon,
                              String linkLabel, String linkUrl) {
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.benefit = benefit;
        this.address = address;
        this.hours = hours;
        this.contact = contact;
        this.mapIcon = mapIcon;
        this.linkLabel = linkLabel;
        this.linkUrl = linkUrl;
    }

    /**
     * 소속 목록을 통째로 바꾼다. 이미 있는 소속은 행을 지우지 않고 혜택만 고친다 — 지우고 다시 넣으면
     * Hibernate 가 INSERT 를 DELETE 보다 먼저 보내 (partner_id, affiliation) UNIQUE 에 걸린다.
     *
     * @param benefits 소속 → 소속 전용 혜택(null 이면 기본 혜택). 순서대로 들어간다.
     */
    public void replaceAffiliations(LinkedHashMap<String, String> benefits) {
        this.affiliations.removeIf(a -> !benefits.containsKey(a.getAffiliation()));
        for (var entry : benefits.entrySet()) {
            PartnerAffiliation existing = this.affiliations.stream()
                    .filter(a -> a.getAffiliation().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.changeBenefit(entry.getValue());
            } else {
                addAffiliation(entry.getKey(), entry.getValue());
            }
        }
    }

    // benefit이 null이면 이 업체의 기본 benefit을 따른다.
    public void addAffiliation(String affiliation, String benefit) {
        this.affiliations.add(new PartnerAffiliation(this, affiliation, benefit));
    }

    public void removeAffiliation(String affiliation) {
        this.affiliations.removeIf(a -> a.getAffiliation().equals(affiliation));
    }
}
