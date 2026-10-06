-- ============================================
-- 홍익대학교 현대미술관(HoMA) 10월 전시를 지도 제보(배지 "전시")로 등록 — 2026-10-06 작성
-- 출처: HoMA 공지에 첨부된 2026 전시 일정표 PDF(제1관 문헌관 4층: articleNo=148964, 2026-02-26 게시 /
--       제2관 홍문관 2층: articleNo=146601, 2025-12-23 게시). 연초 계획표라 바뀔 수 있다 — 본문에 그렇게 적었다.
-- 시간은 DB 규칙대로 UTC 로 넣는다(KST 10:00 = 01:00 UTC, KST 18:00 = 09:00 UTC).
-- 작성자: 관리자 계정(@author). 다른 계정(학생회 공식 계정 등)으로 올리려면 @author 만 바꾼다.
-- 이미 시작한 전시는 published_at 을 비워 새 제보 알림을 보내지 않는다.
-- 시작 전 전시는 시작 시각에 ReportStartPushScheduler 가 published_at 을 채우며 새 제보 알림을 한 번 보낸다(평소와 같음).
-- 선행: db/alter_reports_add_place_label.sql, db/alter_reports_content_2000.sql 실행 후.
-- 다시 실행해도 같은 제목·시작 시각의 행은 넣지 않는다.
-- ============================================
SET NAMES utf8mb4;
SET @author := (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1);
SET @r := (SELECT id FROM buildings WHERE code = 'hongik_r');
SET @mh := (SELECT id FROM buildings WHERE code = 'hongik_mh');

START TRANSACTION;

INSERT INTO reports (user_id, building_id, floor, lat, lng, category, custom_category_label, place_label, title, content,
                     starts_at, ends_at, status, reviewed_at, admin_reminder_count, created_at, updated_at)
SELECT @author, s.building_id, s.floor, s.lat, s.lng, 'ETC', '전시', s.place_label, s.title, s.content,
       s.starts_at, s.ends_at, 'ACTIVE', UTC_TIMESTAMP(6), 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
  SELECT @mh AS building_id, 4 AS floor, 37.5506803 AS lat, 126.9259832 AS lng,
         '문헌관 MH동 4층 현대미술관(HoMA) 1관' AS place_label,
         '일반대학원 디자인계열(디자인·공예) 2학기 석사학위청구전' AS title,
         '일반대학원 디자인·공예 전공 석사과정의 2학기 학위청구전이에요.\n\n관람: 평일 10:00~18:00 (토·일 휴관)\n장소: 문헌관(MH) 4층 현대미술관 제1관\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.' AS content,
         '2026-10-05 01:00:00' AS starts_at, '2026-10-09 09:00:00' AS ends_at
  UNION ALL SELECT @mh, 4, 37.5506803, 126.9259832, '문헌관 MH동 4층 현대미술관(HoMA) 1관',
         '2026 산업미술대학원 2학기 석사학위청구전(공예·디자인)',
         '산업미술대학원 공예·디자인 전공 석사과정의 2학기 학위청구전이에요.\n\n관람: 평일 10:00~18:00 (토·일 휴관)\n장소: 문헌관(MH) 4층 현대미술관 제1관\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.',
         '2026-10-12 01:00:00', '2026-10-16 09:00:00'
  UNION ALL SELECT @mh, 4, 37.5506803, 126.9259832, '문헌관 MH동 4층 현대미술관(HoMA) 1관',
         '2026 홍익패션위크',
         '홍익대학교 패션 전공 학생들의 작품을 소개하는 홍익패션위크 전시예요.\n\n관람: 평일 10:00~18:00 (토·일 휴관)\n장소: 문헌관(MH) 4층 현대미술관 제1관\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요. 패션쇼 일정은 홍익패션위크 공식 안내를 확인해 주세요.',
         '2026-10-19 01:00:00', '2026-10-23 09:00:00'
  UNION ALL SELECT @mh, 4, 37.5506803, 126.9259832, '문헌관 MH동 4층 현대미술관(HoMA) 1관',
         '산업디자인전공 졸업전시',
         '미술대학 산업디자인전공 졸업전시예요.\n\n관람: 평일 10:00~18:00\n장소: 문헌관(MH) 4층 현대미술관 제1관\n\n홍익대학교 현대미술관 2026 전시 일정표 기준(10/26~10/30)이에요. 학과 졸업전시 안내와 날짜가 다를 수 있으니 방문 전에 확인해 주세요.',
         '2026-10-26 01:00:00', '2026-10-30 09:00:00'
  UNION ALL SELECT @r, 2, 37.5527515, 126.9250927, '홍문관 R동 2층 현대미술관(HoMA) 2관 1실',
         '이유진 박사학위청구전: SUPERPOSITION(중첩)',
         '이유진 작가의 박사학위청구전이에요.\n\n관람: 수~일 10:00~18:00 (월·화 휴관)\n장소: 홍문관(R) 2층 현대미술관 제2관 1실\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.',
         '2026-10-07 01:00:00', '2026-10-11 09:00:00'
  UNION ALL SELECT @r, 2, 37.5527515, 126.9250927, '홍문관 R동 2층 현대미술관(HoMA) 2관 1실',
         '이다은·김성은 박사학위청구전: 오아리호',
         '이다은·김성은 작가의 박사학위청구전이에요.\n\n관람: 수~일 10:00~18:00 (월·화 휴관)\n장소: 홍문관(R) 2층 현대미술관 제2관 1실\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.',
         '2026-10-21 01:00:00', '2026-10-25 09:00:00'
  UNION ALL SELECT @r, 2, 37.5527515, 126.9250927, '홍문관 R동 2층 현대미술관(HoMA) 2관 1실',
         '송규호 박사학위청구전',
         '송규호 작가의 박사학위청구전이에요.\n\n관람: 수~일 10:00~18:00 (월·화 휴관)\n장소: 홍문관(R) 2층 현대미술관 제2관 1실\n\n홍익대학교 현대미술관 2026 전시 일정표 기준이라 실제 일정은 바뀔 수 있어요.',
         '2026-10-28 01:00:00', '2026-11-01 09:00:00'
) s
WHERE @author IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM reports x WHERE x.title = s.title AND x.starts_at = s.starts_at);

COMMIT;

-- 확인: SELECT id, title, starts_at, ends_at, status FROM reports WHERE custom_category_label = '전시' ORDER BY starts_at;
-- 되돌리기: DELETE FROM reports WHERE custom_category_label = '전시' AND place_label LIKE '%현대미술관(HoMA)%' AND created_at >= '2026-10-06';
