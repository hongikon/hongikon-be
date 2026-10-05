-- ============================================
-- refresh_tokens: 유저당 활성 refresh 토큰 1개를 해시(SHA-256, hex)로 저장.
-- /auth/reissue 시 이 테이블 기준으로 추가 검증 후 로테이션, /auth/logout 시 삭제.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 DB에 직접 실행해야 함.
-- ※ 2026-10-04 다중 세션으로 바뀜: 이 파일 다음에 db/alter_refresh_tokens_multi_session.sql 도 실행
--   (uq_refresh_token_user 제거, previous_token_hash·rotated_at 추가, token_hash 유니크).
-- ============================================

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_user UNIQUE (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
