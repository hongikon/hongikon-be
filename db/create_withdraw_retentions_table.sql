-- ============================================
-- withdraw_retentions: 탈퇴 회원의 부정 이용 방지 기록(분리 보관, 탈퇴일로부터 1년) (2026-10-04)
--   - 대상: 탈퇴 시점에 이용 정지 상태였거나 정지된 적이 있는(suspended_at 있음) 회원, 또는 신고를 1건 이상 받은 제보를 쓴 회원.
--     그 밖의 회원은 지금처럼 즉시 삭제하고 이 테이블에 아무것도 남기지 않는다.
--   - 소셜 계정 id 원문은 저장하지 않는다. social_id_hash = HMAC-SHA256(키, "<social_type>:<social_id>") 의 hex(64자).
--     키는 WITHDRAW_RETENTION_KEY_SECRET(app.withdraw-retention.key-secret), 비어 있으면 JWT_SECRET 에서 파생한다.
--     키를 바꾸면 기존 기록과 재가입자를 대조할 수 없게 된다 — 바꾸지 않는다.
--   - snapshot: 제보·받은 신고·남이 쓴 제보에 단 신고의 JSON 스냅숏(닉네임·이메일·Apple 토큰은 넣지 않음).
--   - retained_image_keys: S3 에 복사해 둔 제보 사진(retained/<원래 키>) 키 목록(JSON 배열). 만료되면 함께 지운다.
--   - users 행은 탈퇴로 지워지므로 users 를 FK 로 참조하지 않는다. rejoined_user_id 도 FK 없는 일반 컬럼이다
--     (재가입한 회원이 다시 탈퇴하면 WithdrawRetentionService 가 비운다).
--   - 만료(retain_until 지남) 행은 WithdrawRetentionService.purgeExpired 가 매일 지운다(app.withdraw-retention.purge-cron).
-- ddl-auto=validate 이므로 feat/withdraw-retention 배포 전에 실행한다.
-- ============================================

CREATE TABLE IF NOT EXISTS withdraw_retentions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    social_type          VARCHAR(20)  NOT NULL,
    social_id_hash       VARCHAR(64)  NOT NULL,
    was_suspended        BOOLEAN      NOT NULL,
    suspended_reason     VARCHAR(200) NULL,
    suspended_at         DATETIME     NULL,
    report_count         INT          NOT NULL,
    flagged_report_count INT          NOT NULL,
    snapshot             LONGTEXT     NOT NULL,
    retained_image_keys  TEXT         NULL,
    withdrawn_at         DATETIME     NOT NULL,
    retain_until         DATETIME     NOT NULL,
    rejoined_user_id     BIGINT       NULL,
    rejoined_at          DATETIME     NULL,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    UNIQUE KEY uq_withdraw_retentions_social (social_type, social_id_hash),
    INDEX idx_withdraw_retentions_retain_until (retain_until),
    INDEX idx_withdraw_retentions_rejoined_user (rejoined_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
