-- ============================================
-- 공개 회원 번호 users.member_code (2026-10-02). 영문 대문자·숫자 10자리, 예: K7Q2M9XA4D
--   순번인 users.id 를 화면에 보여 주면 가입자 수가 드러나고 남의 번호를 추측하기 쉬워서,
--   설정 화면·관리자 콘솔에는 이 무작위 번호를 보여 준다. users.id 는 그대로 PK·JWT sub.
--   코드: User.memberCode, MemberCodeGenerator, MemberCodeAssigner
--
-- 실행 순서 (ddl-auto=validate 라 컬럼이 없으면 새 서버가 뜨지 않는다)
--   A. 배포 전  : 1단계(컬럼+유니크 인덱스) → 2단계(기존 회원 채우기) → 확인 SELECT 가 0
--   B. 배포 직후: 2단계를 한 번 더(A 와 배포 사이에 옛 서버로 가입한 회원 채우기) → 확인 SELECT 0 → 3단계(NOT NULL)
--   3단계를 배포 전에 하면 배포까지 옛 서버의 가입 INSERT 가 member_code 없이 실패하므로 배포 뒤에 한다.
--   validate 는 NULL 허용 여부를 검사하지 않아 B 전에도 새 서버는 정상이다(새 가입자는 서버가 번호를 채운다).
--
-- 번호 만들기: RANDOM_BYTES(8)(MySQL 5.6.17+, OpenSSL 난수, RAND() 는 예측 가능해서 쓰지 않음)를 36진수로 바꿔
--   끝 10자리를 쓴다 = 64비트 난수 mod 36^10. CONV 결과는 대문자 A–Z·0–9 라 앱 서버가 만드는 번호와 형식이 같다.
--   (편향은 36^10/2^64 ≈ 0.02% 수준이라 무시해도 된다.)
-- 2단계는 몇 번을 실행해도 안전하다(이미 채운 행은 건드리지 않음).
-- ============================================

-- 1단계 [배포 전]: NULL 허용 컬럼과 유니크 인덱스. MySQL 유니크 인덱스는 NULL 여러 개를 허용한다.
ALTER TABLE `users`
  ADD COLUMN `member_code` varchar(10) COLLATE utf8mb4_unicode_ci NULL AFTER `id`,
  ADD UNIQUE INDEX `uq_users_member_code` (`member_code`);

-- 2단계 [배포 전, 배포 직후 한 번 더]: 비어 있는 회원 채우기.
-- UPDATE IGNORE 는 유니크 인덱스와 겹치는 행만 건너뛰고(NULL 로 남김) 나머지는 채운다.
-- 겹쳐서 남은 행은 다음 줄이 새 난수로 다시 시도한다 — 같은 문장을 세 번 둔 것이 "충돌 재시도"다.
-- 번호 공간이 약 3.6×10^15 라 사실상 첫 줄에서 끝나고 나머지 줄은 0 rows affected.
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;
UPDATE IGNORE `users` SET `member_code` = LPAD(RIGHT(CONV(HEX(RANDOM_BYTES(8)), 16, 36), 10), 10, '0') WHERE `member_code` IS NULL;

-- 확인: 둘 다 0 이어야 한다. still_null 이 0 이 아니면 위 UPDATE IGNORE 한 줄을 0 이 될 때까지 다시 실행한다.
SELECT COUNT(*) AS `still_null` FROM `users` WHERE `member_code` IS NULL;
SELECT COUNT(*) AS `bad_format` FROM `users` WHERE NOT (`member_code` COLLATE utf8mb4_bin REGEXP '^[A-Z0-9]{10}$');

-- 3단계 [배포 직후, 2단계 재실행·확인 뒤]: NOT NULL.
-- NULL 이 남아 있으면 이 문장이 오류로 끝나고 아무것도 바뀌지 않는다(안전장치) → 2단계를 다시 실행.
ALTER TABLE `users`
  MODIFY COLUMN `member_code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL;

-- 되돌리기(서버를 이전 버전으로 돌린 뒤):
-- ALTER TABLE `users` DROP INDEX `uq_users_member_code`, DROP COLUMN `member_code`;
