package com.hongmap.hongmapbackend.partner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제휴업체-소속 연결. 같은 업체라도 소속마다 혜택이 다를 수 있어
 * benefit을 소속 단위로 덮어쓸 수 있다(null이면 Partner.benefit을 그대로 쓴다).
 */
@Entity
@Table(
        name = "partner_affiliations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_partner_affiliation", columnNames = {"partner_id", "affiliation"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerAffiliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "affiliation", length = 50, nullable = false)
    private String affiliation;

    @Column(name = "benefit", length = 255)
    private String benefit;

    PartnerAffiliation(Partner partner, String affiliation, String benefit) {
        this.partner = partner;
        this.affiliation = affiliation;
        this.benefit = benefit;
    }

    void changeBenefit(String benefit) {
        this.benefit = benefit;
    }

    /** 소속 전용 혜택이 있으면 그것을, 없으면 업체 기본 혜택을 돌려준다. */
    public String getEffectiveBenefit() {
        return benefit != null ? benefit : partner.getBenefit();
    }
}
