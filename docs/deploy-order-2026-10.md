# 홍익온 백엔드 PR 배포 가이드 (10-02 밤 기준)

- **main에 머지된 PR**: #4, #5, #6, #7, #8, #9, #10, #11
- **남은 순서**: **#13 → #14 → #15 → #16 → #17**, **#18 은 독립(아무 때나)** (#12는 이 문서라 아무 때나)
- 이 순서로 5개를 한꺼번에 합쳐 `./gradlew test` **240개가 모두 통과**했어요. 아래 "손으로 풀 충돌"에 적은 것만 고쳤어요.
- 로컬 MySQL에서 확인한 것
  1. main 스키마에 남은 SQL 4개를 순서대로 실행
  2. 5개 PR을 합친 서버를 `ddl-auto=validate`로 기동 → 정상
  3. #15 SQL은 두 번 실행해도 안전
- 운영 DB가 `ddl-auto=validate`라서, **각 PR의 SQL은 그 PR을 배포하기 전에 RDS에서 먼저 실행**해야 서버가 떠요.
- 각 PR 본문에 같은 틀(요약·왜·API·DB·env·충돌·테스트·배포·앱·한계)로 자세히 적어 두었어요.

## 한눈에 보기

| 순서 | PR | 내용 | 먼저 실행할 SQL | env | 비고 |
|---|---|---|---|---|---|
| 1 | #13 | 작성자 숨기기 `authorKey`, 신고 사유 `PRIVACY`, 회원 정지·관리자 지정, 탈퇴 시 카카오 연결 끊기, 크롤러 UA | `db/alter_users_add_status.sql` | **`AUTHOR_KEY_SECRET`, `KAKAO_ADMIN_KEY`를 배포 전에** | main 병합 완료. 머지 뒤 #15의 base를 main으로 |
| 2 | #14 | 관리자 알림(승인 대기·새 문의·자동 숨김), 승인 대기 리마인드, **승인된 제보도 신고 누적 자동 숨김**, 스케줄러 4스레드 | `db/alter_user_notification_settings_add_admin_alerts.sql`, `db/alter_reports_add_admin_reminder.sql` | 선택: `SCHEDULING_POOL_SIZE`(4), `PUSH_ADMIN_*` | #17과 테스트 충돌(아래) |
| 3 | #15 | 공개 회원 번호 `member_code`(영문·숫자 10자리) | `db/alter_users_add_member_code.sql` | 없음 | **RDS 스냅샷 → SQL → 머지 → 바로 배포** |
| 4 | #16 | 내 제보 내역 `GET /users/me/reports`, 신고 검토 중 삭제 잠금 | 없음 | 없음 | |
| 5 | #17 | 예정 제보(시작 14일 전까지, 진행 최대 7일), `include=upcoming`, 시작 시각 새 제보 알림 | 없음 | 선택: `REPORT_STARTS_AT_MAX_DAYS`(14), `REPORT_MAX_DURATION_DAYS`(7), `PUSH_REPORT_START_CRON` | 운영 `.env`의 `REPORT_ENDS_AT_MAX_DAYS`는 안 쓰임 |
| (독립) | #18 | 크롤러 누락 게시판 10개 — 새 보드 4개(기초과학과·자율전공·디자인엔지니어링전공·바이오헬스융합학부) + 전공 5개를 상위 학부 게시판으로 연결(`SOURCE_ALIASES`), 구독 400 해소 | 없음 | 없음 | `docs/worklog.md` 끝만 충돌(양쪽 유지). 배포 후 `POST /crawler/trigger`(ADMIN)로 바로 수집. 조소과·기초과학과 게시판은 지금 글이 0건 |

## 배포 전에 꼭 확인할 것

- **#13 전에 `AUTHOR_KEY_SECRET`을 넣어요.** `openssl rand -base64 32`로 만들고, **한 번 정하면 바꾸지 않아요.**
  - 비우면 `JWT_SECRET`에서 파생해요.
  - 나중에 넣거나 바꾸면 사용자 기기의 "이 사용자 숨기기" 목록이 모두 풀려요.
- **#13 전에 `KAKAO_ADMIN_KEY`를 넣어요.** 카카오 디벨로퍼스 > 앱 > 앱 키 > Admin 키예요.
  - 처리방침에 "탈퇴 시 카카오 연결 해제"를 적었어요.
  - 비우면 연결 끊기를 건너뛰어요(WARN 로그).
- 운영 `.env`에 옛 `CRAWLER_USER_AGENT`가 있으면 지워요(#13의 새 기본 UA를 쓰게).
- (이미 main에 있는 것) `JWT_SECRET`은 32바이트 이상이어야 해요. Apple 키가 없으면 `APPLE_CLIENT_IDS=`(빈 값)로 둬요. S3를 아직 설정하지 않았으면 `AWS_S3_BUCKET`을 비워요.

## PR별 SQL과 env

### #13
- SQL: `db/alter_users_add_status.sql`
  - 추가 컬럼: `users.status varchar(20) NOT NULL DEFAULT 'ACTIVE'`, `suspended_reason varchar(200) NULL`, `suspended_at datetime NULL`
  - 확인: `SHOW COLUMNS FROM users LIKE 'status';`
  - 되돌리기: `ALTER TABLE users DROP COLUMN status, DROP COLUMN suspended_reason, DROP COLUMN suspended_at;`
- env: `AUTHOR_KEY_SECRET`(권장, 첫 배포 전), `KAKAO_ADMIN_KEY`(운영 필수), `CRAWLER_USER_AGENT`(지우기)

### #14
- SQL 2개(둘 다 필수)
  1. `db/alter_user_notification_settings_add_admin_alerts.sql`: `admin_alerts_enabled boolean NOT NULL DEFAULT TRUE`
  2. `db/alter_reports_add_admin_reminder.sql`: `admin_reminder_count TINYINT NOT NULL DEFAULT 0`, `admin_reminded_at DATETIME NULL`
  - 확인: `SHOW COLUMNS FROM reports LIKE 'admin_remind%';`
  - 되돌리기: `ALTER TABLE user_notification_settings DROP COLUMN admin_alerts_enabled; ALTER TABLE reports DROP COLUMN admin_reminder_count, DROP COLUMN admin_reminded_at;`
- env(모두 선택)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SCHEDULING_POOL_SIZE` | 4 | `@Scheduled` 스레드. 1이면 정각 크롤링이 리마인드·Apple 재시도·예정 제보 시작 알림을 막아요 |
| `PUSH_ADMIN_ALERT_WINDOW_SECONDS` | 120 | |
| `PUSH_ADMIN_REMINDER_CRON` | `0 */10 * * * *` | |
| `PUSH_ADMIN_REMINDER_AFTER_MINUTES` | 30 | |
| `PUSH_ADMIN_REMINDER_REPEAT_AFTER_MINUTES` | 120 | |
| `PUSH_ADMIN_REMINDER_QUIET_START_HOUR` / `_END_HOUR` | 0 / 8 | KST |
| `REPORT_FLAG_THRESHOLD` | 3 | 이제 "마지막 관리자 검토 뒤 들어온 신고 수"에 적용돼요 |

- 배포 직후 첫 회차(10분 안)에 30분 넘게 대기 중인 제보가 있으면 리마인드가 1건 나갈 수 있어요. 정상이에요.

### #15
- SQL: `db/alter_users_add_member_code.sql`. 다시 실행해도 안전한 단일 스크립트예요.
  - 하는 일: 컬럼·유니크 인덱스 생성 → 기존 회원 전원에 번호 채우기 → 확인 SELECT → NOT NULL
  - 확인 SELECT의 `still_null`, `duplicated`, `bad_format`이 **모두 0**이어야 해요. 관리자 번호도 함께 조회돼요(관리자에게 알려 주기).
  - 순서: **RDS 스냅샷 → SQL → 머지 → 바로 배포**. SQL과 배포 사이에는 옛 서버의 **새 가입만** 실패해요.
  - 되돌리기: 서버를 되돌린 뒤 `ALTER TABLE users DROP INDEX uq_users_member_code, DROP COLUMN member_code;`
- env: 없음

### #16
- SQL·env 없음

### #17
- SQL 없음
- env(선택): `REPORT_STARTS_AT_MAX_DAYS`(14), `REPORT_MAX_DURATION_DAYS`(7), `PUSH_REPORT_START_CRON`(`30 * * * * *`)
- `REPORT_ENDS_AT_MAX_DAYS`는 지워도 돼요.

## 손으로 풀 충돌

1. **`AdminAlertDispatcherTest` 167행 (#14와 #17)**
   - #14 테스트가 고정된 과거 `"startsAt": "2026-10-01T08:00:00.000Z"`로 제보를 올려요. #17의 시작 시각 검증 때문에 400이 돼요.
   - **#14·#17 중 나중에 머지하는 쪽에서** 아래처럼 바꾸고 `./gradlew test`를 돌려요.
   ```java
   // 전: "startsAt": "2026-10-01T08:00:00.000Z", "endsAt": "%s"} … .formatted(building.getId(), Instant.now().plus(Duration.ofHours(2)).toString())
   // 후:
   "title": "붕어빵 트럭", "startsAt": "%s", "endsAt": "%s"}
   """.formatted(building.getId(), Instant.now().toString(), Instant.now().plus(Duration.ofHours(2)).toString());
   ```
2. **`src/test/resources/application-test.properties` 끝 (#14와 #17)**: 두 줄을 모두 남겨요.
   ```properties
   push.admin-reminder-cron=-
   push.report-start-cron=-
   ```
3. **`docs/worklog.md`** (#12·#13·#14·#15·#16·#17): 각 PR이 자기 섹션을 붙였을 뿐이에요. **양쪽을 모두 남겨요**(충돌 표시 줄만 지우기).
4. **#15의 base**: #13을 머지한 뒤 GitHub에서 #15의 base를 `main`으로 바꿔요. #15에는 최신 #13이 이미 병합돼 있어서 추가 충돌은 없어요.
5. 그 밖의 코드 파일은 자동으로 병합돼요. 예: `ReportService`(#14 `create`·`flag` / #16 `delete`), `ReportFlagRepository`, `ReportRepository`, `AdminApiIntegrationTest`.

## 배포 뒤 스모크 체크리스트

각 PR을 배포한 직후 해당 항목을 확인해요. 마지막(#17) 배포 뒤 전체를 한 번 더 돌려요.

- [ ] **기동**: 로그에 `Started HongmapBackendApplication`이 찍히고, `Schema-validation` 오류가 없는지. `GET /status`가 200인지
- [ ] **#13**
  - [ ] `GET /reports` 항목에 `authorKey`(16자)가 있는지
  - [ ] 관리 탭 > 회원 검색(닉네임·앱 닉네임·id)이 되는지
  - [ ] 테스트 계정을 정지하면 `POST /reports`가 403이고, 해제하면 다시 되는지
  - [ ] 카카오 테스트 계정 탈퇴 → 로그에 `카카오 연결 끊기 완료`
- [ ] **#14**
  - [ ] 일반 계정으로 제보 → 관리자 폰에 `[관리] 새 제보 승인 대기`
  - [ ] 문의 → `[관리] 새 문의`
  - [ ] 테스트 제보를 **승인한 뒤** 다른 계정 3개로 신고 → 제보가 HIDDEN이 되고 `[관리] 신고 누적으로 자동 숨김`
  - [ ] 설정의 관리자 알림 토글로 끄고 켜기
  - [ ] 스레드 이름 `sched-1..4`가 로그에 보이는지(정각 크롤링 중에도 리마인드가 정시에 도는지)
- [ ] **#15**
  - [ ] 새 계정 가입
  - [ ] 설정 > 계정에 10자리 회원 번호
  - [ ] 관리 탭에서 그 번호를 소문자로 검색해도 찾는지
- [ ] **#16**
  - [ ] 설정 > 내 제보 내역에 상태·반려 사유가 보이는지
  - [ ] `GET /users/me/reports/count`
  - [ ] 반려된 제보를 삭제하면 204인지
- [ ] **#17**
  - [ ] 내일 시작하는 제보를 등록하고 승인하면 작성자에게 "…부터 지도에 보여요"
  - [ ] `GET /reports`에는 없고 `?include=upcoming`에는 있는지
  - [ ] 시작 시각 직후 로그에 `예정 제보 시작 알림: reportId=…`
  - [ ] 15일 뒤 시작이나 8일짜리 기간은 400인지
- [ ] **공통**: 앱(preview OTA)에서 지도·제보 등록·사진·알림 탭을 한 바퀴 돌아요. ERROR 로그가 새로 생기지 않았는지도 봐요.

## 앱(FE)과의 관계

남은 5개 PR의 앱 쪽은 모두 이미 OTA(preview 채널)로 나가 있고, 서버 배포 전에도 안전하게 동작해요.

| PR | 서버 배포 전 앱 동작 |
|---|---|
| #13 | `authorKey`가 없으면 숨기기 메뉴를 감춰요. `PRIVACY`가 400이면 `ETC`로 다시 보내요 |
| #14 | 설정에 `adminAlerts`가 없으면 관리자 토글을 숨겨요 |
| #15 | 회원 번호 대신 `#id`를 보여 줘요 |
| #16 | API가 404·405면 "내 제보 내역" 메뉴를 숨겨요 |
| #17 | 시작 전 제보를 승인하면 바로 "지도에 올라갔어요"와 새 제보 알림이 가요(아직 지도에 없음). #17 배포로 해소돼요 |

- **FE 후속**: #16 배포 뒤에는 운영진이 검토해 숨긴 제보를 작성자가 지울 수 있어요. 앱은 아직 HIDDEN이면 항상 삭제 버튼을 감추니, 삭제 버튼을 보여 주고 409일 때만 안내하도록 바꿔요.
- **FE 확인**: 로그아웃할 때 `DELETE /users/me/devices/{id}`를 부르는지 확인해요. 안 부르면 로그아웃한 관리자 기기에 `[관리]` 푸시가 계속 가요.

## 참고: main에 이미 들어간 PR(#4–#11)의 운영 설정

### Apple 키 (#7)
1. Apple Developer → Identifiers에 `com.hongikon.app` 등록(Sign In with Apple, Push Notifications). `com.hongikon.app.preview`는 "Group with an existing primary App ID"로 등록해요.
2. Keys → Sign in with Apple 키 생성 → .p8은 한 번만 받을 수 있어요. Key ID와 Team ID를 확인해요.
3. env를 넣어요.
   - `APPLE_TEAM_ID`, `APPLE_KEY_ID`
   - `APPLE_PRIVATE_KEY`: .p8 줄바꿈을 `\n`으로 바꾼 한 줄
   - `APPLE_TOKEN_ENC_KEY`: `openssl rand -base64 32`, 바꾸지 않아요
   - `APPLE_CLIENT_IDS`: 비우면 Apple 로그인 끔
   - `APPLE_REVOCATION_RETRY_CRON`: 선택, 기본 매시 17분

### S3 (#9)
- 버킷 설정
  - `ap-northeast-2`, ACL 비활성화, 퍼블릭 액세스 차단 4개, SSE-S3
  - CORS: `PUT`·`GET`, origin `https://hongikon.com`·`https://www.hongikon.com`, header `content-type`
  - 수명 주기: `reports/` 30일 만료, 미완료 멀티파트 1일
- 권한
  - IAM 역할: `reports/*`에 Put·Get·Delete, 버킷에 ListBucket(prefix `reports/*`)
  - EC2에 역할을 연결하고 메타데이터 hop limit을 2로 올려요.
- env: `AWS_S3_BUCKET`, `AWS_REGION=ap-northeast-2`
- 확인: `POST /reports/images`가 201인지. 되돌리기는 `AWS_S3_BUCKET`을 비우면 돼요.

### 기타
- #8: `SHOW CREATE TABLE notification_categories;`로 FK를 확인해요.
- #10: `deploy/nginx/hongikon-api.conf`를 적용하고 `nginx -t && systemctl reload nginx`를 실행해요. IP 직접 접속 차단 블록은 nginx 버전을 확인한 뒤 풀어요.

## 운영·보안 점검 (출시 전)

- [ ] 네이버 지도 Client Secret을 재발급하고, 사용처를 hongikon.com·hongmap12.netlify.app·앱 번들 ID로 제한해요.
- [ ] `ADMIN_AUDIT` 로그를 1년 이상 보관해요(CloudWatch 또는 마운트한 파일).
- [ ] Docker 로그 크기를 제한하고 재배포해도 보관되게 해요.
- [ ] 접속 기록 보관 기간과 nginx logrotate를 맞추고, 처리방침에 적어요.
- [ ] RDS 암호화와 자동 백업 보존 기간을 확인하고, 처리방침에 적어요. `report_flags`의 FK가 ON DELETE CASCADE인지도 확인해요.
- [ ] AWS: IMDSv2 강제, 루트 MFA, CloudTrail
