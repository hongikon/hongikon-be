-- ============================================
-- partner_affiliations 재설계: @ElementCollection(Set<String>) → PartnerAffiliation 엔티티
-- - 대리키 id 추가, (partner_id, affiliation)은 PK 대신 UNIQUE로 유지
-- - 소속별 혜택 덮어쓰기용 benefit 컬럼 추가 (NULL이면 partners.benefit 사용)
--
-- 기존 테이블은 복합 PK(partner_id, affiliation)라 ALTER로 PK를 바꾸려면
-- PK 드롭 → id 컬럼 추가 → AUTO_INCREMENT PK 지정을 순서대로 해야 해서 번거롭고,
-- 기존 데이터가 테스트 1건뿐이라 DROP 후 재생성한다.
-- ⚠ 이 테이블의 기존 행은 모두 삭제된다. 운영 DB에 실행하기 전 데이터 유무를 먼저 확인할 것.
-- ddl-auto=validate 환경이므로 새 코드 배포 전에 이 DDL을 실제 DB에 직접 실행해야 함.
-- ============================================

DROP TABLE IF EXISTS partner_affiliations;

CREATE TABLE partner_affiliations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    affiliation VARCHAR(50) NOT NULL COMMENT '총학생회 / 미술대학 / 공과대학 ... — 1단 필터',
    benefit VARCHAR(255) NULL COMMENT '이 소속 전용 혜택. NULL이면 partners.benefit(기본 혜택)을 사용',
    PRIMARY KEY (id),
    CONSTRAINT uq_partner_affiliation UNIQUE (partner_id, affiliation),
    KEY ix_affiliation (affiliation),
    CONSTRAINT fk_partner_affiliation_partner FOREIGN KEY (partner_id) REFERENCES partners (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
