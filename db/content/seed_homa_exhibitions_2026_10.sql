-- ============================================
-- 홍익대학교 현대미술관(HoMA) 10월 전시 7건을 장소별 전시 일정(exhibitions)으로 등록 — 2026-10-06 작성
-- 출처: HoMA 공지에 첨부된 2026 전시 일정표 PDF(제1관 문헌관 4층: articleNo=148964, 2026-02-26 게시 /
--       제2관 홍문관 2층: articleNo=146601, 2025-12-23 게시). 연초 계획표라 바뀔 수 있다 — 설명에 그렇게 적었다.
-- 장소: hi-mh-4f-exhibition(문헌관 MH 4층, HoMA 1관), hi-r-2f-exhibition(홍문관 R 2층, HoMA 2관).
-- 날짜는 KST 달력 날짜(양 끝 포함). 제보(reports)·알림은 만들지 않는다.
-- 선행: db/create_exhibitions_table.sql, 그리고 지도 데이터 동기화(db/sync_map_data_*.sql) 뒤에 실행한다.
--       (외래키는 없지만 장소가 campus_facilities 에 없으면 GET /map/data 에 나오지 않는다.)
-- 다시 실행해도 같은 장소·제목·시작일의 행은 넣지 않는다. 이후 수정·삭제는 관리자 화면(/admin/map/exhibitions)에서.
-- ============================================
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO exhibitions (facility_code, title, starts_on, ends_on, hours, description, created_at, updated_at)
SELECT s.facility_code, s.title, s.starts_on, s.ends_on, s.hours, s.description, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
  SELECT 'hi-mh-4f-exhibition' AS facility_code,
         '일반대학원 디자인계열(디자인·공예) 2학기 석사학위청구전' AS title,
         DATE '2026-10-05' AS starts_on, DATE '2026-10-09' AS ends_on,
         '평일 10:00~18:00 (토·일 휴관)' AS hours,
         '일반대학원 디자인·공예 전공 석사과정의 2학기 학위청구전이에요. 문헌관(MH) 4층 현대미술관 제1관.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.' AS description
  UNION ALL SELECT 'hi-mh-4f-exhibition',
         '2026 산업미술대학원 2학기 석사학위청구전(공예·디자인)',
         DATE '2026-10-12', DATE '2026-10-16',
         '평일 10:00~18:00 (토·일 휴관)',
         '산업미술대학원 공예·디자인 전공 석사과정의 2학기 학위청구전이에요. 문헌관(MH) 4층 현대미술관 제1관.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.'
  UNION ALL SELECT 'hi-mh-4f-exhibition',
         '2026 홍익패션위크',
         DATE '2026-10-19', DATE '2026-10-23',
         '평일 10:00~18:00 (토·일 휴관)',
         '홍익대학교 패션 전공 학생들의 작품을 소개하는 홍익패션위크 전시예요. 문헌관(MH) 4층 현대미술관 제1관.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요. 패션쇼 일정은 홍익패션위크 공식 안내를 확인해 주세요.'
  UNION ALL SELECT 'hi-mh-4f-exhibition',
         '산업디자인전공 졸업전시',
         DATE '2026-10-26', DATE '2026-10-30',
         '평일 10:00~18:00',
         '미술대학 산업디자인전공 졸업전시예요. 문헌관(MH) 4층 현대미술관 제1관.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요. 학과 졸업전시 안내와 날짜가 다를 수 있으니 방문 전에 확인해 주세요.'
  UNION ALL SELECT 'hi-r-2f-exhibition',
         '이유진 박사학위청구전: SUPERPOSITION(중첩)',
         DATE '2026-10-07', DATE '2026-10-11',
         '수~일 10:00~18:00 (월·화 휴관)',
         '이유진 작가의 박사학위청구전이에요. 홍문관(R) 2층 현대미술관 제2관 1실.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.'
  UNION ALL SELECT 'hi-r-2f-exhibition',
         '이다은·김성은 박사학위청구전: 오아리호',
         DATE '2026-10-21', DATE '2026-10-25',
         '수~일 10:00~18:00 (월·화 휴관)',
         '이다은·김성은 작가의 박사학위청구전이에요. 홍문관(R) 2층 현대미술관 제2관 1실.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.'
  UNION ALL SELECT 'hi-r-2f-exhibition',
         '송규호 박사학위청구전',
         DATE '2026-10-28', DATE '2026-11-01',
         '수~일 10:00~18:00 (월·화 휴관)',
         '송규호 작가의 박사학위청구전이에요. 홍문관(R) 2층 현대미술관 제2관 1실.\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.'
) s
WHERE NOT EXISTS (SELECT 1 FROM exhibitions x
                  WHERE x.facility_code = s.facility_code AND x.title = s.title AND x.starts_on = s.starts_on);

COMMIT;

-- 확인: SELECT id, facility_code, title, starts_on, ends_on FROM exhibitions ORDER BY facility_code, starts_on;
-- 되돌리기(이 파일로 넣은 7건): 위 확인 쿼리로 id 를 본 뒤 관리자 화면에서 지우거나 id 로 지운다.
