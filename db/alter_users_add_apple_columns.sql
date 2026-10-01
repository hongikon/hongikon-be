-- ============================================
-- users: Sign in with Apple 탈퇴 시 Apple 토큰 폐기(revoke)용 컬럼 추가 (2026-10-02)
--   - apple_refresh_token: 로그인 때 authorization code를 교환해 받은 Apple refresh 토큰 (Apple 사용자만, 그 외 NULL)
--     AES-GCM 으로 암호화해 저장한다("v1:" + Base64, 키는 APPLE_TOKEN_ENC_KEY).
--   - apple_client_id: 그 토큰을 받은 client_id(iOS 번들 ID). revoke 때 같은 값을 써야 한다.
-- ddl-auto=validate 환경이므로 배포 순서가 중요하다:
--   1) 이 ALTER를 운영 DB에 먼저 실행
--   2) 그다음 feat/apple-login 이 들어간 백엔드 배포 (먼저 배포하면 컬럼이 없어 기동 시 스키마 검증 실패)
-- 컬럼 추가만 하므로 기존 버전 백엔드가 떠 있는 상태에서 실행해도 안전하다.
-- ============================================

ALTER TABLE users
    ADD COLUMN apple_refresh_token VARCHAR(512) NULL,
    ADD COLUMN apple_client_id VARCHAR(100) NULL;
