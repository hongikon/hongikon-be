# 배포 절차서 — 2026-10-05 통합 배포 (PR #16 #17 #18 #19 #20 #21 #24 #25 #26 #27 #28)

`main` 에 열한 개 PR 을 한 번에 합쳤다(`release/2026-10-05` → `main`, 전체 테스트 468개 통과).
EC2 SSH·RDS 접근 권한이 있는 사람이 진행한다. 서버 재배포 방법(도커 빌드·실행)은 `deploy-runbook-2026-10.md` 2단계와 같다.

`ddl-auto=validate` 라 **SQL 을 빠뜨리면 서버가 기동하지 않는다.** 1 → 2 → 3 순서. 모든 SQL 은 새 테이블·인덱스만
더하고 옛 서버가 모르는 것이라, 배포 전에 먼저 실행해도 지금 서버에 영향이 없다.

| PR | 내용 | DB 변경 |
|---|---|---|
| #16 | 내 제보 내역 `GET /users/me/reports`, 신고 검토 중 삭제 잠금 | 없음 |
| #17 | 예정 제보(시작 14일 전까지, 진행 최대 7일), `include=upcoming`, 시작 시각 알림 | 없음 |
| #18 | 소식 0건 게시판 10개 수집(새 게시판·별칭) | 없음 |
| #19 | 제보 댓글·답글, 댓글 신고·자동 숨김·관리자 검토, 댓글 금칙어 | `create_report_comments_table.sql` |
| #20 | 제보 공감·HOT·관심 제보·조회 수·댓글 좋아요 | `create_report_community_tables.sql` |
| #21 | 탈퇴 후 부정 이용 방지 기록(1년), 이용 제한 사유 고지 | `create_withdraw_retentions_table.sql` |
| #24 | 자꾸 로그아웃되던 문제 — 기기별 세션(최대 10), 재발급 60초 유예 | `alter_refresh_tokens_multi_session.sql` |
| #25 | 끝난 제보를 '노출 중'으로 세지 않기, 관리자 목록 날짜 조회 | 없음 |
| #26 | 크롤링 최적화(증분·서버별 병렬·실패 게시판 쉬기) | 없음 |
| #27 | 관리자 화면 로그인 닉네임(실명) 숨김, `GET /admin/users/{id}/login-name` | 없음 |
| #28 | 버그 점검(동시 요청 500, 좌표·층 검증, 등록·문의 빈도 제한, 반려·삭제 제보 재공개 차단) | `alter_add_unique_user_lists.sql` |

통합 때 맞춘 것: 회원 검색이 회원 번호(#15)·탈퇴 기록(#21)·실명 숨김(#27)을 함께 쓰고, 지도 목록 300건 상한(#28)을
HOT 목록(#20)에도 걸었으며, 관리자 댓글 응답(#19)도 로그인 닉네임 원문 대신 앱 이름·회원 번호만 싣는다(#27 규칙).

---

## 1. RDS 에 SQL 실행 (배포 전)

Workbench 라면 먼저 `SET SQL_SAFE_UPDATES = 0;`

1. `db/create_report_comments_table.sql`
2. `db/create_report_community_tables.sql` — 1번 다음에(댓글 좋아요가 report_comments 를 가리킨다)
3. `db/create_withdraw_retentions_table.sql`
4. `db/alter_refresh_tokens_multi_session.sql` — 여러 번 돌려도 안전. **이 SQL 과 새 서버 사이에 옛 서버가 오래 돌지 않게**
   (옛 서버는 회원당 토큰 1개를 가정한다) 바로 2단계로 넘어간다.
5. `db/alter_add_unique_user_lists.sql` — 맨 앞 SELECT 두 개로 중복 행 수를 먼저 본다. 중복(북마크·키워드·학과 구독)은
   가장 먼저 만든 행만 남기고 지운 뒤 유니크 인덱스를 건다.

확인:
```sql
SHOW TABLES LIKE 'report_comment%';          -- report_comments, report_comment_flags, report_comment_likes
SHOW TABLES LIKE 'report_%';                 -- report_reactions, report_follows, report_engagement, report_view_marks 포함
SHOW TABLES LIKE 'withdraw_retentions';
SHOW COLUMNS FROM refresh_tokens LIKE 'previous_token_hash';
SHOW INDEX FROM bookmarks WHERE Non_unique = 0;
```

## 2. 서버 환경변수(.env) — 추가

| 변수 | 필요 | 설명 |
|---|---|---|
| `WITHDRAW_RETENTION_KEY_SECRET` | 권장 | 탈퇴 기록 대조용 HMAC 키(32바이트 이상 무작위, `openssl rand -base64 48`). 비우면 `JWT_SECRET` 에서 파생. **한 번 정하면 바꾸지 않는다**(바꾸면 기존 기록과 재가입자를 대조할 수 없다). |
| `REPORT_VIEW_KEY_SECRET` | 선택 | 조회 수 중복 방지 HMAC 키. 비우면 `JWT_SECRET` 사용. |

나머지 새 설정(빈도 제한·크롤러 병렬·세션 수 등)은 모두 기본값이 있어 넣지 않아도 된다(`application.properties` 참고).
서버 시간대는 그대로 **UTC**(컨테이너에 `TZ` 를 넣지 않는다).

## 3. 백엔드 재배포 (EC2)

`deploy-runbook-2026-10.md` 2단계와 같다 — 되돌리기용으로 지금 이미지에 태그를 남기고(`hongikon-be:2026-10-04` 등), `main` 을 받아 빌드·실행.
`docker logs -f` 에서 `Started HongmapBackendApplication` 확인. `Schema-validation: missing table/column` 이 보이면 1단계 SQL 누락이다.

## 4. 배포 후 확인

```bash
curl -s https://api.hongikon.com/status                                    # buildTime 이 오늘
curl -s 'https://api.hongikon.com/reports?include=upcoming' | head -c 300   # 200, 목록
curl -s 'https://api.hongikon.com/reports/1/comments' -o /dev/null -w '%{http_code}\n'   # 200 또는 404(없는 제보)
curl -s -o /dev/null -w '%{http_code}\n' https://api.hongikon.com/admin/users/1/login-name  # 401
```
- 앱: 로그인 → 앱을 껐다 켜도 로그인 유지, 다른 기기에서 로그인해도 첫 기기가 로그아웃되지 않음(#24)
- 관리 탭: 제보 검토 '종료' 표시·기간 조회(#25), 회원 검색에 실명 없음·"로그인 닉네임 보기"(#27)
- 제보 상세: 댓글 쓰기·신고, 공감(#19 #20)
- 다음 크롤링(서버 로그 `크롤링 요약:`)에서 요청 수·소요 시간이 줄었는지(#26)

## 되돌리기

- 서버가 안 뜨면 대부분 SQL 누락 — 로그의 테이블·컬럼 이름으로 해당 SQL 실행 후 재기동.
- 코드를 되돌릴 때는 3단계에서 남긴 이미지로 다시 띄운다. 새 테이블은 옛 코드와 충돌하지 않는다.
  단, `alter_refresh_tokens_multi_session.sql` 뒤 옛 서버로 돌아가면 회원당 여러 행이 있는 사용자는 재발급이 실패해 다시 로그인해야 할 수 있다.
