# 배포 절차서 — 관리자 콘솔 · 소식 게시판 출처 (2026-09-30 작성)

운영 서버(`api.hongikon.com`)는 **2026-09-17 빌드**로 떠 있다(`GET /status` 의 `buildTime`).
그 뒤 `main` 에 쌓인 변경과 PR #2, #3 을 한 번에 내보내는 순서다. EC2 SSH·RDS 접근 권한이 있는 사람이 진행한다.

이번 배포로 바뀌는 것:

| 출처 | 내용 | 필요한 DB 변경 |
|---|---|---|
| `main` (9/23) | 소식 이미지·첨부·조회수 저장, `departmentName`·`preview`, 운영 Swagger 차단 | `alter_add_news_media_columns.sql` |
| `main` (9/30) | 학과 시드, 소식 학과/건물 백필 API, CORS | `seed_departments.sql` |
| PR #2 | 소식 게시판 출처 `sourceId` (대학공지 학사/장학 등 구독 복구) | `alter_add_news_source_id_column.sql` |
| PR #3 | 관리자 권한·제보 승인·문의·대시보드, 웹 관리자 로그인 | `alter_admin_console.sql` |

`ddl-auto=validate` 라 **SQL 을 빠뜨리면 서버가 기동하지 않는다.** 반드시 1 → 2 → 3 순서.

---

## 0. 사전 확인

- [ ] PR #2, #3 머지 (둘은 충돌 없이 합쳐지는 것 확인함)
- [ ] 현재 RDS 에 이미 적용된 게 뭔지 확인 — 아래 결과로 1단계에서 건너뛸 SQL 을 고른다

```sql
SHOW COLUMNS FROM news LIKE 'images';        -- 있으면 alter_add_news_media_columns 적용됨
SHOW COLUMNS FROM news LIKE 'source_id';     -- 있으면 alter_add_news_source_id_column 적용됨
SHOW COLUMNS FROM users LIKE 'role';         -- 있으면 alter_admin_console 적용됨
SELECT COUNT(*) FROM departments;            -- 43 이면 seed_departments 적용됨
```

## 1. RDS 에 SQL 실행 (배포 전)

Workbench 라면 먼저 `SET SQL_SAFE_UPDATES = 0;`

1. `db/alter_add_news_media_columns.sql` (미적용 시)
2. `db/seed_departments.sql` (미적용 시)
3. `db/alter_add_news_source_id_column.sql` — 컬럼 추가 + 기존 소식 백필. 여러 번 돌려도 안전
4. `db/alter_admin_console.sql`

확인:
```sql
SELECT source_id, COUNT(*) FROM news GROUP BY source_id ORDER BY 2 DESC;  -- 학사/장학/학과명들이 보여야 함
SHOW COLUMNS FROM reports LIKE 'reviewed_at';
SHOW TABLES LIKE 'feedback';
```

## 2. 백엔드 재배포 (EC2)

```bash
cd ~/hongikon-be && git checkout main && git pull
./gradlew bootJar -x test          # t3.micro 라 테스트는 로컬/CI 에서
docker tag hongikon-be hongikon-be:2026-09-17   # 되돌리기용으로 지금 이미지를 남겨 둔다(이미지 이름은 실제 것으로)
docker stop <컨테이너> && docker rm <컨테이너>
docker build -t hongikon-be .
docker run -d --name <컨테이너> --restart unless-stopped --env-file .env -p 127.0.0.1:8080:8080 hongikon-be
docker logs -f <컨테이너>          # "Started HongmapBackendApplication" 확인
```

- `-p 127.0.0.1:8080:8080` — 8080 을 외부에 직접 열지 않는다(지금도 보안그룹으로 막혀 있지만 이중으로).
- 메모리 68%·스왑 사용 중이고 "System restart required" 상태였으니, 컨테이너를 내린 김에 `sudo reboot` 후 올려도 된다(`--restart unless-stopped` 라 재부팅 뒤 자동 기동).
- **서버 시간대**: 앱·관리자 화면은 서버의 존 없는 시각을 **UTC** 로 읽는다. `eclipse-temurin` 이미지 기본값이 UTC 라 그대로 두면 된다. 컨테이너에 `TZ=Asia/Seoul` 같은 걸 **넣지 않는다**.
  확인: `docker exec <컨테이너> date` → `UTC` 로 나와야 함.

`.env` 에 추가할 것은 없다. (로컬에서 관리자 화면을 붙일 때만 `OAUTH2_ALLOWED_REDIRECT_URIS` 에 `http://localhost:8081/admin` 추가)

## 3. Nginx 보안 설정 반영

지금 운영 Nginx 는 레포의 `deploy/nginx/hongikon-api.conf` 가 아니라 기본 사이트를 고쳐 쓰고 있어,
IP 로 직접 접속해도 응답하고(`https://54.180.195.51/status` → 200) 응답에 Nginx 버전이 노출된다. 속도 제한도 없다
(이번에 비로그인 `POST /feedback` 이 열리므로 속도 제한이 필요하다).

```bash
cd ~/hongikon-be && sudo EMAIL=<이메일> bash deploy/setup-https.sh
```
스크립트는 여러 번 돌려도 안전하고, 이미 발급된 인증서는 certbot 이 재사용한다. 기존 `sites-enabled/default` 는 비활성화된다(원본은 `sites-available` 에 남음).

## 4. 배포 후 확인

```bash
curl -s https://api.hongikon.com/status                      # buildTime 이 오늘
curl -s -o /dev/null -w '%{http_code}\n' https://api.hongikon.com/v3/api-docs     # 404 (운영 Swagger 차단)
curl -s -o /dev/null -w '%{http_code}\n' https://api.hongikon.com/admin/overview  # 401
curl -s 'https://api.hongikon.com/news' | head -c 300         # sourceId, departmentName 필드 보임
curl -sI https://api.hongikon.com/status | grep -i '^server'  # 버전 번호 없어야 함
```

## 5. 관리자 지정 → 관리자 화면

1. https://hongikon.com/admin 에서 카카오 로그인 → "관리자 권한이 없는 계정입니다" 화면이 나오면 정상(아직 USER)
2. RDS:
   ```sql
   SELECT id, nickname, created_at FROM users ORDER BY created_at DESC LIMIT 10;
   UPDATE users SET role = 'ADMIN' WHERE id = <본인 id>;
   ```
3. 관리자 화면 새로고침 → 대시보드가 보이면 끝
4. **운영 도구 → 소식 학과/건물 백필** 한 번 실행 (학과 매칭이 비어 있는 기존 소식을 채운다).
   오래 걸리면 화면에 504 가 떠도 서버에서는 계속 돈다(Netlify 프록시 제한 ~26초) — 대시보드에서 결과 확인
5. 제보 검토 탭에서 쌓여 있는 `PENDING` 제보 승인/반려

## 되돌리기

- 서버가 안 뜨면 대부분 SQL 누락이다: `docker logs` 에서 `Schema-validation: missing column [...]` 을 찾아 해당 SQL 실행 후 재기동.
- 코드를 되돌려야 하면 2단계에서 남겨 둔 `hongikon-be:2026-09-17` 이미지로 컨테이너를 다시 띄운다.
  추가한 컬럼·테이블은 이전 코드와 충돌하지 않으므로(엔티티에 없는 컬럼은 validate 가 무시) SQL 은 되돌리지 않아도 된다.

## 남은 것 (이번 배포와 별개)

- 카카오 개발자 콘솔 앱 이름이 아직 **'홍대로'** — 로그인 동의 화면에 그대로 뜬다. [앱 설정 → 일반 → 기본 정보]에서 `홍익온` 으로, 아이콘도 등록
- `GET /news` 가 페이지 나눔 없이 전체(1만 건 넘게, 약 2MB)를 한 번에 내려준다 — 페이지 나눔 필요
