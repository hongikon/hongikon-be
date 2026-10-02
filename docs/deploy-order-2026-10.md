# 홍익온 백엔드 PR #4–#11 배포 가이드

열려 있는 PR 8개를 아래 순서로 머지하면 손으로 풀어야 할 충돌은 두 군데뿐이에요. 이 순서로 8개를 한꺼번에 합쳐 본 결과 테스트 163개가 모두 통과했어요.

운영 DB가 `ddl-auto=validate`라서, **각 PR의 SQL은 그 PR을 배포하기 전에 RDS에서 먼저 실행**해야 서버가 떠요.

## 배포 전에 꼭 확인할 것

- `JWT_SECRET`이 32바이트 이상이어야 해요. 짧으면 #10 이후 서버가 시작되지 않아요.
- #7 이후 prod는 Apple 키(`APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_TOKEN_ENC_KEY`)가 없으면 시작되지 않아요. 키가 아직 없으면 `APPLE_CLIENT_IDS=`(빈 값)로 두세요. Apple 로그인만 꺼진 채로 떠요.
- S3 설정 전이면 `AWS_S3_BUCKET`을 비워 두세요. 사진 없이 제보하는 방식으로 정상 동작해요.

## 머지·배포 순서

| 순서 | PR | 내용 | 먼저 실행할 SQL | 비고 |
|---|---|---|---|---|
| 1 | #4 | 소식 장학 분류 개선, 빈 401 오류 수정 | `db/update_news_category_2026_10_01.sql` | |
| 2 | #8 | 회원탈퇴 정리 | 없음 | **출시 차단 이슈.** 지금 main은 분야 알림을 바꾼 사용자가 탈퇴하면 FK 오류로 실패해요 |
| 3 | #5 | 게시판 구독 기반 푸시 | `db/create_user_board_subscriptions.sql` | |
| 4 | #6 | 제보 승인·반려 알림, 캠퍼스 새 제보 알림 | `db/create_user_notification_settings.sql` | #5 위에 쌓인 PR이라 #5 다음 |
| 5 | #7 | Sign in with Apple | `db/alter_users_add_apple_columns.sql`, `db/create_apple_pending_revocations.sql` | env 필요. `tmp` 커밋(4d5c312)이 있으니 squash merge 권장 |
| 6 | #10 | 보안 점검 반영 (PKCE, 세션·JWT, 관리자 접속기록, nginx) | 없음 | 배포 후 nginx 설정 적용 |
| 7 | #9 | 제보 사진 S3 업로드 | `db/alter_add_report_image_key.sql` | 충돌 1곳 |
| 8 | #11 | 앱 닉네임, 공개 작성자 이름 가리기 | `db/alter_users_add_app_nickname.sql` | 충돌 2곳 |

### PR별 메모

- **#8**: 배포 후 `SHOW CREATE TABLE notification_categories;`로 FK 동작을 한 번 확인해 주세요.
- **#10**: `deploy/nginx/hongikon-api.conf`를 적용한 뒤 `nginx -t && systemctl reload nginx`를 실행해요. IP로 직접 들어오는 HTTPS를 막는 블록은 주석 처리돼 있어요. nginx 1.18에서는 동작하지 않으니 버전을 확인한 뒤 풀어 주세요.

### 손으로 풀어야 하는 충돌

1. **#9 머지 시 `AdminReportService.java`**: 양쪽을 다 남겨요.
   - 필드 `eventPublisher`와 `reportImageService`를 둘 다 둬요.
   - `ReportModeratedEvent` 발행과 REJECTED·DELETED 때 사진 삭제도 둘 다 남겨요.
2. **#11 머지 시 `ReportResponse.java`, `ReportSummaryResponse.java`**:
   - `.authorNickname(report.getUser().getDisplayName())`로 바꿔요.
   - #9가 넣은 `.imageUrl(imageUrl)` 줄은 그대로 둬요.

## 새 환경변수

| 변수 | PR | 설명 |
|---|---|---|
| `APPLE_TOKEN_ENC_KEY` | #7 | Apple 리프레시 토큰 암호화 키. `openssl rand -base64 32`로 만들고 비밀로 보관해요 |
| `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY` | #7 | Apple Developer에서 발급받는 .p8 키 정보. prod에서 필수가 됐어요 |
| `APPLE_CLIENT_IDS` | #7 | 빈 값이면 Apple 로그인이 꺼져요 |
| `APPLE_REVOCATION_RETRY_CRON` | #7 | 선택. 기본값은 매시 17분(`0 17 * * * *`) |
| `AWS_S3_BUCKET`, `AWS_REGION` | #9 | 제보 사진 버킷과 리전(`ap-northeast-2`). 비우면 사진 업로드만 꺼져요 |
| `JWT_SECRET` | #10 | 기존 값이 32바이트 이상인지 확인해요 |

## S3 설정 (#9, 사진 기능을 켤 때)

1. **버킷 생성**: `ap-northeast-2`, ACL 비활성화, 퍼블릭 액세스 차단 4개 모두 켜기, SSE-S3 암호화.
2. **CORS**
   - AllowedOrigins: `https://hongikon.com`, `https://www.hongikon.com`
   - AllowedMethods: `PUT`, `GET`
   - AllowedHeaders: `content-type`
   - MaxAge: 3000
3. **수명 주기 규칙**: 접두사 `reports/`, 30일 뒤 만료, 미완료 멀티파트 업로드는 1일 뒤 중단.
4. **IAM 역할**
   - `arn:aws:s3:::<BUCKET>/reports/*`에 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
   - 버킷에 `s3:ListBucket` (조건 `s3:prefix` = `reports/*`)
   - 접근 키는 만들지 않아요.
5. **EC2에 역할 연결**: Actions → Security → Modify IAM role.
6. **메타데이터 hop limit을 2로 올리기**: Docker 컨테이너가 역할 권한을 받으려면 필요해요.

   ```bash
   aws ec2 modify-instance-metadata-options --instance-id <i-...> \
     --http-tokens required --http-put-response-hop-limit 2 --http-endpoint enabled
   ```

7. **`.env`에 추가**: `AWS_S3_BUCKET=<BUCKET>`, `AWS_REGION=ap-northeast-2`
8. **확인**
   - `POST /reports/images`가 201이면 정상이에요. 503이면 버킷 env가 빠진 거예요.
   - 사진을 붙여 제보한 뒤 관리자 화면에서 보이는지 확인해요.
   - 승인한 뒤 다른 계정에서도 보이는지 확인해요.

되돌리기: `AWS_S3_BUCKET`을 지우면 사진 없이 제보하는 방식으로 돌아가요.

## 운영·보안 점검 (출시 전)

- [ ] **네이버 지도 Client Secret 재발급.** 공개 저장소의 git 기록에 노출된 적이 있어요. 사용 가능한 곳을 hongikon.com, hongmap12.netlify.app, 앱 번들 ID로만 제한해요.
- [ ] **`ADMIN_AUDIT` 로그(관리자 작업 기록) 보관.** CloudWatch나 마운트한 파일로 보내 1년 이상 보관해요. 개인정보의 안전성 확보조치 기준 제8조 요건이에요.
- [ ] **Docker 로그 크기 제한.** 지금은 제한이 없고, 재배포하면 사라져요.
- [ ] **접속 기록 보관 기간.** 기간을 정해 nginx logrotate를 거기에 맞추고, 그 숫자를 처리방침에도 적어요.
- [ ] **RDS 확인.** 데이터 암호화와 자동 백업 보존 기간을 확인하고, 보존 기간을 처리방침에 적어요.
- [ ] **AWS 계정 보안.** IMDSv2 강제, 루트 계정 MFA, CloudTrail 켜기.

## 참고: 앱(FE)과의 관계

앱은 이 PR들이 배포되기 전에도 문제없이 동작하도록 이미 OTA로 나가 있어요.

- 사진, 닉네임, PKCE는 서버가 배포되면 앱 업데이트 없이 자동으로 켜져요.
- Apple 로그인은 서버 배포와 함께 새 네이티브 빌드도 필요해요.
