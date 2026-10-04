-- ============================================
-- 공개 회원 번호 users.member_code (2026-10-02). 영문 대문자·숫자 10자리, 예: K7Q2M9XA4D
--   순번인 users.id 를 화면에 보여 주면 가입자 수가 드러나고 남의 번호를 추측하기 쉬워서,
--   설정 화면·관리자 콘솔에는 이 무작위 번호를 보여 준다. users.id 는 그대로 PK·JWT sub.
--   코드: User.memberCode, MemberCodeGenerator, MemberCodeAssigner
--
-- ■ 순서: RDS 스냅샷 → 이 스크립트 전체 실행(확인 SELECT 확인) → PR 머지 → 배포
--   ddl-auto=validate 라 이 SQL 보다 앱을 먼저 배포하면 서버가 뜨지 않는다.
--   기존 회원(관리자 id 1, 2 포함) 전원의 번호를 이 스크립트가 직접 채운다 — 앱 쪽 백필 러너는 없다.
--   스크립트 실행 ~ 새 서버 배포 사이에는 옛 서버로 "새 가입"만 실패한다(member_code 를 넣지 않으므로).
--   기존 회원 로그인·이용은 영향 없음. 그 틈을 짧게 하려고 실행 직후 바로 배포한다.
--
-- ■ 처음부터 끝까지 다시 실행해도 안전하다(컬럼·인덱스는 없을 때만 만들고, 이미 채운 행은 건드리지 않음).
--
-- ■ 번호 만들기: RANDOM_BYTES(8)(MySQL 5.6.17+, OpenSSL 난수. RAND() 는 예측 가능해서 쓰지 않음)를 36진수로 바꿔
--   끝 10자리 = 64비트 난수 mod 36^10. CONV 결과는 대문자 A–Z·0–9 라 서버가 만드는 번호와 형식이 같다.
--   (편향 36^10/2^64 ≈ 0.02% 로 무시 가능.) 충돌 재시도: 유니크 인덱스를 먼저 걸고 UPDATE IGNORE 로 채우면
--   겹치는 행만 NULL 로 남고, 다음 UPDATE IGNORE 가 새 난수로 다시 채운다.
-- ============================================

-- 1) member_code 컬럼(NULL 허용) — 없을 때만
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'member_code');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `users` ADD COLUMN `member_code` varchar(10) COLLATE utf8mb4_unicode_ci NULL',
  'SELECT ''member_code 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 유니크 인덱스 — 없을 때만. NULL 은 여러 개 허용되므로 채우기 전에 걸어도 된다(충돌 재시도에 필요).
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uq_users_member_code');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `users` ADD UNIQUE INDEX `uq_users_member_code` (`member_code`)',
  'SELECT ''uq_users_member_code 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 기존 회원 전원 채우기(충돌 재시도 3회). 번호 공간이 약 3.6×10^15 라 사실상 첫 줄에서 끝나고 나머지는 0 rows.
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;

-- 4) 확인 — 아래 세 값이 모두 0 이어야 5) 로 넘어간다. still_null 이 0 이 아니면 3) 의 한 줄을 다시 실행.
SELECT
  (SELECT COUNT(*) FROM `users` WHERE `member_code` IS NULL) AS `still_null`,
  (SELECT COUNT(*) FROM (SELECT `member_code` FROM `users` WHERE `member_code` IS NOT NULL
                         GROUP BY `member_code` HAVING COUNT(*) > 1) AS d) AS `duplicated`,
  (SELECT COUNT(*) FROM `users` WHERE NOT (`member_code` COLLATE utf8mb4_bin REGEXP '^[A-Z0-9]{10}$')) AS `bad_format`;
-- 관리자(id 1, 2) 번호 확인 — 관리자에게 알려 줄 값
SELECT `id`, `role`, `member_code` FROM `users` WHERE `id` IN (1, 2) OR `role` = 'ADMIN' ORDER BY `id`;

-- 5) NOT NULL. NULL 이 남아 있으면 이 문장이 오류로 끝나고 아무것도 바뀌지 않는다(안전장치) → 3) 을 다시 실행.
--    UNIQUE 는 2) 에서 이미 걸었다(SHOW INDEX FROM users WHERE Key_name = 'uq_users_member_code' 로 확인).
ALTER TABLE `users`
  MODIFY COLUMN `member_code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL;

-- 되돌리기(서버를 이전 버전으로 돌린 뒤 실행. 이 컬럼은 이전 버전 서버가 모르는 컬럼이라 남겨 둬도 동작은 하지만,
-- NOT NULL 이라 이전 서버의 새 가입이 실패하므로 지운다):
-- ALTER TABLE `users` DROP INDEX `uq_users_member_code`, DROP COLUMN `member_code`;
