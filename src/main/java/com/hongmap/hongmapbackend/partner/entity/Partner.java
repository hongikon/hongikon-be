package com.hongmap.hongmapbackend.partner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @OneToMany(mappedBy = "partner", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PartnerAffiliation> affiliations = new ArrayList<>();

    @Builder
    public Partner(String name, String category, BigDecimal latitude, BigDecimal longitude,
                    String benefit, String address, String roadAddress, String hours,
                    String contact, String mapIcon, String linkLabel, String linkUrl) {
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
    }

    // benefit이 null이면 이 업체의 기본 benefit을 따른다.
    public void addAffiliation(String affiliation, String benefit) {
        this.affiliations.add(new PartnerAffiliation(this, affiliation, benefit));
    }

    public void removeAffiliation(String affiliation) {
        this.affiliations.removeIf(a -> a.getAffiliation().equals(affiliation));
    }
}
