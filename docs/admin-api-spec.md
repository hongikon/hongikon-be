# 관리자(운영) API 스펙

웹 관리자 화면(`https://hongikon.com/admin`)이 쓰는 API. 백엔드 브랜치 `feat/admin-console`.

## 권한

- `users.role` 컬럼(`USER` 기본 / `ADMIN`). 관리자 지정은 DB에서 직접:
  `UPDATE users SET role = 'ADMIN' WHERE id = ?;`
- `/admin/**`, `/crawler/**` 는 ADMIN 만. 역할은 **요청마다 DB에서 확인**한다(토큰에 역할을 싣지 않음) —
  관리자 해제가 즉시 반영되고, 기존 토큰/프론트 저장 방식은 그대로다.
- 응답: 토큰 없음·만료 → `401`, 로그인했지만 ADMIN 아님 → `403`.

## 웹 로그인

앱은 지금처럼 `hongikon://auth/callback` 으로 돌아온다. 웹 관리자는 돌아올 주소를 지정한다.

1. **전체 페이지 이동**(fetch/팝업 아님):
   `GET https://api.hongikon.com/oauth2/authorization/kakao?redirect_uri=https://hongikon.com/admin`
   - 반드시 API 도메인으로 직접 이동해야 한다. Netlify `/api` 프록시를 거치면 세션 쿠키가
     `hongikon.com` 에 붙어 카카오 콜백(`api.hongikon.com`)에서 state 검증이 깨진다.
   - `redirect_uri` 는 서버 허용 목록(`app.oauth2.allowed-redirect-uris`)과 **정확히 일치**해야 한다.
     기본 허용: `hongikon://auth/callback`(앱), `https://hongikon.com/auth/callback`·`https://www.hongikon.com/auth/callback`(웹판 앱),
     `https://hongikon.com/admin`·`https://www.hongikon.com/admin`(관리자).
     로컬 개발은 백엔드 환경변수 `OAUTH2_ALLOWED_REDIRECT_URIS` 에 `http://localhost:8081/admin` 을 추가한다.
     목록에 없거나 생략하면 앱 주소(`hongikon://auth/callback`)로 간다.
2. 로그인 성공 → `302 {redirect_uri}?code=<1회용 코드>`
3. 웹이 `POST /auth/token/exchange {"code": "..."}` → `{accessToken, refreshToken}` (기존 API)
4. 이후 모든 관리자 요청에 `Authorization: Bearer <accessToken>`. 만료 시 `POST /auth/reissue {"refreshToken"}`.

## 공통

- 날짜: `LocalDateTime` ISO 문자열(`2026-09-30T12:34:56`), 존 정보 없음(기존 API와 동일).
- 에러 바디: 기존 `ErrorResponse` 형식.

## 대시보드

`GET /admin/overview`
```json
{
  "server": { "version": "0.0.1-SNAPSHOT", "buildTime": "2026-09-30T05:00:00Z" },
  "reports": { "pending": 3, "active": 5, "hidden": 1, "rejected": 2 },
  "feedback": { "open": 4 },
  "news": { "total": 11350, "missingDepartment": 1200 },
  "crawler": {
    "running": false,
    "lastStartedAt": "2026-09-30T12:00:00",
    "lastFinishedAt": "2026-09-30T12:01:10",
    "lastSavedCount": 12,
    "lastError": null,
    "lastTrigger": "SCHEDULED"
  }
}
```
`crawler.*` 는 서버 메모리에만 있어 재시작 후 첫 실행 전까지 `null` 이다. `lastTrigger`: `SCHEDULED` / `MANUAL`.

## 제보 검토

상태: `PENDING`(승인 대기) · `ACTIVE`(지도 노출) · `REJECTED`(반려) · `HIDDEN`(신고 누적 자동 숨김 또는 관리자 숨김) · `DELETED`

`GET /admin/reports?status=PENDING` — `status` 생략 시 `PENDING`. `ALL` 이면 DELETED 제외 전부. 최신순, 최대 200건.
```json
{
  "reports": [{
    "id": 12, "status": "PENDING",
    "category": "FOOD_TRUCK", "customCategoryLabel": null,
    "title": "...", "content": "...",
    "buildingId": 3, "buildingName": "제4공학관", "floor": 1,
    "lat": 37.55, "lng": 126.92,
    "startsAt": "...", "endsAt": "...", "createdAt": "...",
    "authorId": 7, "authorNickname": "...",
    "flagCount": 0,
    "moderationNote": null, "reviewedAt": null,
    "authorDisplayName": "홍**"
  }]
}
```
`category`: `EVENT` / `PERFORMANCE` / `FOOD_TRUCK` / `BOOTH` / `ETC`

`authorNickname` 은 검토용 로그인(카카오/Apple) 닉네임 원문, `authorDisplayName` 은 앱 사용자에게 보이는 이름(앱 닉네임, 없으면 첫 글자만 남기고 가린 이름)이다.

`GET /admin/reports/{id}/flags`
```json
{ "flags": [{ "id": 1, "reason": "SPAM", "reporterNickname": "...", "createdAt": "..." }] }
```
`reason`: `FALSE_INFO` / `SPAM` / `INAPPROPRIATE` / `ETC`

`PATCH /admin/reports/{id}` — 상태 변경
```json
{ "status": "ACTIVE", "note": "관리자 메모(최대 200자). REJECTED 면 필수(반려 사유)" }
```
- 허용 목표 상태: `ACTIVE`(승인·재공개), `REJECTED`(반려), `HIDDEN`(숨김), `DELETED`(삭제)
- 응답: 변경된 제보(위 목록 항목과 같은 형태)
- `ACTIVE` 로 되돌려도 기존 신고 기록은 남는다. 관리자가 한 번 검토(`reviewedAt` 있음)한 제보는 신고가 더 쌓여도 자동 숨김되지 않는다(관리자 판단 우선).

## 문의(피드백)

`POST /feedback` — **공개**(로그인 선택). 앱의 문의하기가 이미 이 경로로 보내고 있다.
```json
{ "content": "1~1000자", "contact": "선택, 최대 100자" }
```
→ `201`(바디 없음). 토큰이 있으면 작성자로 연결.

`GET /admin/feedback?status=OPEN` — `OPEN`(기본) / `RESOLVED` / `ALL`. 최신순, 최대 200건.
```json
{ "feedback": [{ "id": 1, "content": "...", "contact": null, "userId": 7, "userNickname": "...",
                 "status": "OPEN", "createdAt": "...", "resolvedAt": null }] }
```
`userId`/`userNickname` 은 비로그인 문의면 `null`.

`PATCH /admin/feedback/{id}` — `{ "status": "RESOLVED" }` 또는 `OPEN` → 변경된 항목

## 회원

`GET /admin/users?q=` (id 또는 닉네임 일부, 비우면 정지 회원 목록) → `{ "users": [회원] }`, `GET /admin/users/{id}` → 회원.
정지·해제·관리자 지정/해제(`POST /admin/users/{id}/suspend|unsuspend|grant-admin|revoke-admin`)도 같은 회원 형태를 돌려준다.
```json
{
  "id": 31, "nickname": "...", "displayName": "홍**", "socialType": "KAKAO",
  "role": "USER", "status": "ACTIVE", "suspendedReason": null, "suspendedAt": null, "createdAt": "...",
  "priorHistory": {
    "withdrawnAt": "2026-10-04T06:30:00", "retainUntil": "2027-10-04T06:30:00", "rejoinedAt": "2026-10-05T01:00:00",
    "suspendedAt": "2026-10-01T12:00:00", "suspendedReason": "도배",
    "wasSuspendedAtWithdrawal": true, "violationReportCount": 1
  }
}
```
`priorHistory`: 탈퇴 기록(1년 보관)이 남은 계정이 같은 소셜 계정으로 **다시 가입한 경우에만** 채워지고, 아니면 `null`.
기록 대상은 정지 이력(탈퇴 시 정지 중이거나 `suspendedAt` 있음) 또는 **운영진이 위반으로 확정한 제보**(관리자가 `REJECTED`·`DELETED` 처리)가
있는 회원뿐이다. 신고만 받은 제보는 대상이 아니다. `violationReportCount` = 보관 중인 위반 확정 제보 수.
재가입하면 관리자 알림(data.type `ADMIN_MEMBER_REJOINED`, `userId`)도 간다. 자동 정지는 하지 않는다.

`GET /admin/users/{id}/prior-history` — 탈퇴 전 기록 전체. 없으면 `404`.
```json
{
  "userId": 31,
  "priorHistory": { "...": "위와 같음" },
  "withdrawals": [{
    "withdrawnAt": "...", "status": "SUSPENDED", "suspendedReason": "도배", "suspendedAt": "...",
    "violationReports": [{
      "id": 12, "category": "ETC", "customCategoryLabel": "홍보", "title": "...", "content": "본문 앞 200자(넘으면 …)",
      "status": "REJECTED", "createdAt": "...", "moderationNote": "광고성 게시물",
      "flagCount": 2, "flagReasons": ["SPAM", "FALSE_INFO"], "retainedImageKeys": ["retained/reports/....jpg"]
    }]
  }],
  "retainedImageUrls": ["https://...presigned..."]
}
```
`withdrawals` 는 오래된 순(재가입·재탈퇴하면 한 건씩 늘어난다). 위반 확정 제보의 요약만 담는다 — 위치(건물·층·좌표)·기간,
위반이 아닌 제보, 이 회원이 단 신고, 닉네임·이메일·소셜 id 는 없다. `retainedImageUrls` 는 위반 제보 사진 사본의 보기 URL(대개 빈 목록 —
관리자 반려·삭제 시점에 사진을 지우기 때문).

## 운영 도구

- `POST /crawler/trigger` → `{ "savedCount": 12 }` (기존, 이제 ADMIN 전용). 이미 도는 중이면 `409`.
- `POST /admin/news/backfill-location` → `{ "updatedCount": 300 }` (기존, 이제 ADMIN 전용)

## 배포 전 DB

`db/alter_admin_console.sql` 을 RDS에 먼저 실행(`ddl-auto=validate`):
`users.role`, `reports.moderation_note`, `reports.reviewed_at`, `feedback` 테이블.
