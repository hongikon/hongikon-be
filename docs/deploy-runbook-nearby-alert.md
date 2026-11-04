# 배포 절차서 — 근처 제휴 알림(서버 쪽)

관리자가 고른 제휴업체 근처에 가면 앱이 알림을 띄우는 기능의 서버 부분이다. **위치는 서버로 오지 않는다** — 앱이 기기 안에서만 판단한다.
서버는 ① 업체별 "근처 알림 대상" 표시, ② 긴급 끄기 스위치, ③ 광고성 정보 수신 동의 시각만 다룬다.
운영 배포는 GitHub Actions `Deploy` 워크플로(`docs/ci-cd.md` 5절)로 한다.

`ddl-auto=validate` 라 **1단계 SQL 을 빠뜨리면 서버가 기동하지 않는다.** 컬럼만 더하므로 SQL 을 먼저 실행해도 지금 서버는 영향이 없다.

| 단계 | 할 일 | 누가 |
|---|---|---|
| 0 | 사전 확인 | 운영자 |
| 1 | RDS 스냅샷 → `db/alter_partners_nearby_alert_2026_10_26.sql` | RDS 접근 권한자 |
| 2 | dev → main 머지 → Deploy 수동 실행(`sql_applied=true`) → 승인 | 운영자 |
| 3 | 확인 | 운영자 |
| 4 | 기능 켜기(긴급 스위치 `NEARBY_ALERTS_ENABLED=true`) — 앱 출시·개인정보처리방침 갱신 뒤 | 운영자 |

---

## 0. 사전 확인

- [ ] Deploy 워크플로에 승인 대기 중인 실행이 있으면 먼저 끝낸다.
- [ ] 이미 적용됐는지(이 스크립트는 다시 돌려도 안전 — 이미 있으면 건너뛴다):

```sql
SHOW COLUMNS FROM partners LIKE 'nearby_alert';
SHOW COLUMNS FROM user_notification_settings LIKE 'partner_ad_consent_at';
```

## 1. RDS 에 SQL 실행 (배포 전)

1. **RDS 스냅샷**을 만든다.
2. `db/alter_partners_nearby_alert_2026_10_26.sql` 실행:
   - `partners.nearby_alert` (`BOOLEAN NOT NULL DEFAULT FALSE`) — 기존 업체는 모두 꺼짐.
   - `user_notification_settings.partner_ad_consent_at` (`DATETIME(6) NULL`, UTC) — 기존 유저는 모두 미동의.

## 2. 서버 배포 (Deploy 워크플로)

`db/` 가 바뀐 커밋이라 main 에 머지되면 Deploy 의 guard 가 멈춘다(의도된 동작).

1. dev → main 머지 → `Publish image` 가 `sha-xxxxxxx` 이미지를 올린다.
2. Actions → **Deploy** → Run workflow — `image_tag` = 그 태그, `dry_run` = **해제**, `sql_applied` = **체크**.
3. Environment `production` 승인 → 배포 후 확인까지 초록이면 끝.

서버 환경변수(.env)는 **바꾸지 않는다** — `NEARBY_ALERTS_ENABLED` 가 없으면 꺼짐(false)이다.
API 는 추가만 있다 — 구버전 앱은 무시한다:

- `GET /map/data`: 최상위 `nearbyAlertsEnabled`(boolean, 항상), partner 의 `nearbyAlert`(true 일 때만).
  필드가 하나 늘어 version(ETag) 이 한 번 바뀐다 → 앱이 지도 데이터를 한 번 새로 받는다(정상).
- 관리자 `POST/PUT /admin/map/partners`: 본문 `nearbyAlert`(선택. POST 에서 생략 = false, PUT 에서 생략 = 지금 값 유지).
- `GET/PATCH /users/me/notification-settings`: 응답 `partnerAdConsentAt`(KST ISO-8601 또는 null), PATCH 본문 `partnerAdConsent`(true/false).

## 3. 확인

```sql
SELECT COUNT(*) FROM partners WHERE nearby_alert;                                        -- 0 (관리자가 고르기 전)
SELECT COUNT(*) FROM user_notification_settings WHERE partner_ad_consent_at IS NOT NULL;  -- 0 (앱 출시 전)
```

- `GET https://api.hongikon.com/map/data` 응답에 `"nearbyAlertsEnabled":false` 가 있고, partner 에는 `nearbyAlert` 가 없다.

## 4. 기능 켜기 / 긴급 끄기

- 켜기 전에: 앱(근처 알림·동의 화면) 출시, 개인정보처리방침·이용약관에 광고성 정보 수신 동의 문구 반영, 관리자가 대상 업체 체크.
- 켜기: 서버 `.env` 에 `NEARBY_ALERTS_ENABLED=true` 를 넣고 같은 이미지로 Deploy 를 다시 돌린다(컨테이너 재기동).
  version 이 바뀌어 앱이 다음 지도 데이터 요청에서 바로 받는다(앱 캐시·304 와 상관없이).
- **긴급 끄기**: `NEARBY_ALERTS_ENABLED=false`(또는 줄 삭제) 후 재기동. 업체별로 끄려면 관리 > 제휴업체에서 "근처 알림 대상" 체크 해제(캐시 즉시 갱신).

## 5. 알아 둘 것

- 서버는 위치를 받지도 저장하지도 않는다. 어느 업체 근처에 갔는지도 서버에 남지 않는다.
- 동의 시각은 처음 동의한 시각을 유지한다(다시 동의해도 바뀌지 않음). 철회하면 NULL 로 지운다. 회원탈퇴 시 행째 지워진다.
- 정보통신망법상 동의 2년마다 재확인 안내가 필요하다 — `partner_ad_consent_at` 기준. 안내 발송 기능은 아직 없다.
- 롤백: 서버는 이전 이미지 태그로 Deploy. 컬럼은 남겨 둬도 이전 서버에 영향 없다.
