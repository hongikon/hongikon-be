-- ============================================
-- apple_pending_revocations: 탈퇴 때 Apple 토큰 폐기(revoke)가 실패한 건의 재시도 대기열 (2026-10-02, 보안 점검 M4)
--   - refresh_token 은 AES-GCM 으로 암호화된 값("v1:..."), 키는 환경 변수 APPLE_TOKEN_ENC_KEY.
--   - AppleRevocationService 가 매시간 다시 시도하고, 성공하거나 72회(약 3일) 실패하면 행을 지운다.
-- ddl-auto=validate 이므로 feat/apple-login 배포 전에 alter_users_add_apple_columns.sql 과 함께 실행한다.
-- ============================================

CREATE TABLE apple_pending_revocations (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refresh_token   VARCHAR(512) NOT NULL,
    client_id       VARCHAR(100) NOT NULL,
    attempts        INT          NOT NULL,
    created_at      DATETIME     NOT NULL,
    next_attempt_at DATETIME     NOT NULL,
    INDEX idx_apple_pending_revocations_next (next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
