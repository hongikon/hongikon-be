-- ============================================
-- refresh_tokens: 유저당 활성 refresh 토큰 1개를 해시(SHA-256, hex)로 저장.
-- /auth/reissue 시 이 테이블 기준으로 추가 검증 후 로테이션, /auth/logout 시 삭제.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 DB에 직접 실행해야 함.
-- ============================================

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_user UNIQUE (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
