# 배포 절차서 — 제휴 종료일과 2026-2학기 제휴 데이터

기간 한정 제휴가 종료일 다음 날부터 지도(`GET /map/data`)에서 자동으로 빠지게 하고, 총학생회·미술대학·경영대학 2학기 제휴를 넣는다.
운영 배포는 GitHub Actions `Deploy` 워크플로(`docs/ci-cd.md` 5절)로 한다.

`ddl-auto=validate` 라 **1단계 SQL 을 빠뜨리면 서버가 기동하지 않는다.** 컬럼만 더하므로(NULL 허용) SQL 을 먼저 실행해도 지금 서버는 영향이 없다.

| 단계 | 할 일 | 누가 |
|---|---|---|
| 0 | 앞선 배포(#56, 취업진로 크롤링) 승인이 끝났는지 확인 | 운영자 |
| 1 | RDS 스냅샷 → `db/alter_add_partner_ends_on.sql` | RDS 접근 권한자 |
| 2 | dev → main 머지 → Deploy 수동 실행(`sql_applied=true`) → 승인 | 운영자 |
| 3 | `db/sync_partners_2026_10_13.sql` | RDS 접근 권한자 |
| 4 | 확인 | 운영자 |
| 5 | 앱 OTA(제휴 시트의 "제휴 기간" 표시) | 프론트 |

---

## 0. 사전 확인

- [ ] Deploy 워크플로에 승인 대기 중인 실행(#56 릴리스)이 있으면 먼저 승인해 끝낸다. 그 배포는 DB 변경이 없다.
- [ ] 이미 적용됐는지:

```sql
SHOW COLUMNS FROM partners LIKE 'ends_on';
SHOW COLUMNS FROM partners LIKE 'usage_note';
SHOW COLUMNS FROM partner_affiliations LIKE 'ends_on';   -- 셋 다 있으면 1단계는 건너뛴다(다시 돌리면 Duplicate column 오류)
```

## 1. RDS 에 SQL 실행 (배포 전)

1. **RDS 스냅샷**을 만든다.
2. `db/alter_add_partner_ends_on.sql` 실행 — `partners.ends_on`·`partner_affiliations.ends_on`(`DATE NULL`), `partners.usage_note`(`VARCHAR(255) NULL`, 업체별 이용 방법) 추가.

## 2. 서버 배포 (Deploy 워크플로)

`db/` 가 바뀐 커밋이라 main 에 머지되면 Deploy 의 guard 가 멈춘다(의도된 동작).

1. dev → main 머지 → `Publish image` 가 `sha-xxxxxxx` 이미지를 올린다.
2. Actions → **Deploy** → Run workflow — `image_tag` = 그 태그, `dry_run` = **해제**, `sql_applied` = **체크**.
3. Environment `production` 승인 → 배포 후 확인까지 초록이면 끝.

서버 환경변수(.env) 변경은 없다. API 는 추가만 있다(partner 의 `endsOn`·`affiliationEnds`·`usageNote`, 관리자 본문의 `endsOn`·`usageNote`) — 구버전 앱은 무시한다.

## 3. 제휴 데이터 넣기 (배포 후)

`db/sync_partners_2026_10_13.sql` 실행. **다시 실행해도 안전하다**(이미 있는 code·소속은 건너뛴다).

- 총학생회: 새 업체 8곳(월리를 찾아라 체험관은 `ends_on` 2026-12-31), 보그헤어 상수역점 혜택·영업시간 갱신, 에버랜드는 총학생회 게시물 링크 버튼과 업체별 이용 방법(`usage_note`: QR코드 스캔 → 인증코드 입력 → 이용권 구매).
- 미술대학(노바랑, ~12/31): 새 업체 25곳(`ends_on` 2026-12-31) + 이미 등록된 17곳에 미술대학 소속·전용 혜택(소속 `ends_on` 2026-12-31).
- 경영대학(~12/14): 티하오 홍대입구점 추가, **기존 경영대학 소속 전체에 소속 `ends_on` 2026-12-14**.
- 카페·음식·주점 혜택 문구의 '품목' → '메뉴'.
- 업체별 이용 방법(`usage_note`) 4곳: 에버랜드, 보그헤어 상수역점, 베이바이허슬기 2곳(네이버 예약 방식).

관리자 API 가 아닌 SQL 이라 지도 데이터 캐시는 **최대 5분 뒤**에 바뀐다.

## 4. 확인

```sql
SELECT COUNT(*) FROM partners;                                             -- 154 (120 + 8 + 25 + 1)
SELECT COUNT(*) FROM partners WHERE ends_on IS NOT NULL;                   -- 27
SELECT affiliation, COUNT(*) FROM partner_affiliations WHERE ends_on IS NOT NULL GROUP BY affiliation;   -- 미술대학 17, 경영대학 19
SELECT COUNT(*) FROM partners WHERE usage_note IS NOT NULL;               -- 4
```

- `GET https://api.hongikon.com/map/data` 의 partners 가 154곳이고, 월리를 찾아라 체험관에 `"endsOn":"2026-12-31"`, 소코아 홍대점에 `affiliationEnds` 가 있다.
- 숫자가 다르면(운영 데이터가 그사이 바뀐 경우) 실패가 아니다 — 새 code 가 들어갔는지만 본다: `SELECT code FROM partners WHERE code IN ('food-schedule-hapjeong','cafe-tihao-hongdae','etc-pactory');`

## 5. 알아 둘 것

- **12/15**: 경영대학 소속이 전부 빠진다. 경영대학 제휴뿐인 업체(약 10곳)는 지도에서도 사라진다 — 다음 학기 제휴가 확정되면 소속 `ends_on` 을 새 날짜로 고친다.
- **2027-01-01**: 미술대학 소속과 미술대학 새 업체 25곳, 월리를 찾아라 체험관이 빠진다.
- 행은 지워지지 않는다. 되살리려면 `ends_on` 을 NULL 이나 새 날짜로 `UPDATE` 하면 5분 안에 다시 나온다.
- 소속별 종료일은 아직 관리자 화면에서 넣거나 고칠 수 없다(SQL 로만). 업체 종료일은 관리 > 제휴업체 "제휴 종료일" 칸으로 고칠 수 있다(프론트 OTA 뒤).
- 넣지 않은 곳: 훈훈스튜디오·런드리고아파트·어반플루토(좌표 미확인), 구공스테이(위치 없음).
- 롤백: 서버는 이전 이미지 태그로 Deploy. 컬럼은 남겨 둬도 이전 서버에 영향 없다. 데이터는 1단계 스냅샷 기준.
