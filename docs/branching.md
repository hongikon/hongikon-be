# 브랜치 전략 (2026-10-06)

`main` ← `dev` ← 작업 브랜치. 작업 브랜치는 모두 `dev` 에서 갈라 `dev` 로 합치고(PR base = `dev`), `main` 은 `dev` 를 합칠 때만 바뀐다.
운영 서버 배포는 `main` 기준이다.

> **2026-10-06 변경:** 평소 작업은 `dev` 에서 바로 커밋한다(작업마다 브랜치를 만들지 않는다). 규모가 크거나 위험한 작업, 팀원 검토가 필요한 작업만 아래 접두사 브랜치를 `dev` 에서 갈라 쓴다. `main` 은 여전히 `dev` 를 릴리스로 합칠 때만 바뀐다.


| 접두사 | 쓰는 때 |
|---|---|
| `feat/` | 새 기능 |
| `fix/` | 버그 수정 |
| `ui/` | 응답 문구 등 사용자에게 보이는 것만 |
| `hotfix/` | 운영 급한 수정 — `dev` 경유 후 바로 `main` |
| `chore/` · `docs/` | 설정·의존성 / 문서 |

순서
1. `git switch dev && git pull` (브랜치를 따로 쓸 때만 `git switch -c feat/이름`) → 작업·`./gradlew test` → push → **PR base `dev`**
2. 릴리스: `dev → main` PR(merge commit). 배포 전 그 사이에 들어온 `db/*.sql` 과 환경변수를 `docs/deploy-runbook-*.md` 순서대로 적용.
3. 백엔드 변경은 `docs/worklog.md` 에도 남긴다. `ddl-auto=validate` 라 엔티티에 컬럼·테이블을 더하면 SQL 파일을 같이 둔다.
