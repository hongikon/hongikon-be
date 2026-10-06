# CI / CD (2026-10-06)

지금 자동으로 도는 것은 **CI(빌드·테스트)** 와 **이미지 업로드(ghcr.io)** 두 가지다. 배포는 아직 자동으로 연결하지 않았고, 서버에서 사람이 직접 한다.

## 1. CI — `.github/workflows/ci.yml`

| 항목 | 내용 |
|---|---|
| 트리거 | `dev`·`main` 으로 향하는 `pull_request`, `dev`·`main` 에 대한 `push` |
| 환경 | `ubuntu-latest`, Java 17(temurin), `gradle/actions/setup-gradle` 캐시 |
| 실행 | `./gradlew build` — 컴파일 + 전체 테스트(건너뛰지 않음) |
| 실패 시 | `build/reports/tests/`, `build/test-results/` 를 `test-reports` 아티팩트로 7일 보관 |

- 테스트는 모두 `@ActiveProfiles("test")` 로 `src/test/resources/application-test.properties` 를 쓴다.
  DB 는 H2 인메모리(MySQL 모드), JWT·카카오·Apple 값은 테스트 전용 고정값, 크롤러·스케줄러는 꺼져 있고 Expo 푸시는 닫힌 포트로 간다.
  그래서 CI 에 **MySQL 서비스 컨테이너나 Secrets 가 필요 없다.** 운영 값은 CI 에 넣지 않는다.
- 새 테스트를 추가할 때도 `@ActiveProfiles("test")` 를 붙이고, 외부 서비스가 필요하면 목(mock)이나 테스트 설정으로 막는다.
- PR 화면의 `CI / build` 체크가 초록이어야 머지한다. (브랜치 보호 규칙에서 필수 체크로 지정하는 것은 저장소 설정에서 따로 한다.)

## 2. 이미지 — `Dockerfile`, `.dockerignore`

서버에서 쓰던 정의 그대로다(`eclipse-temurin:17-jre-alpine`, `/app/app.jar`, 8080). 이미지 안에서 빌드하지 않으므로 jar 를 먼저 만든다.

```bash
./gradlew build
docker build -t hongikon-be:$(git rev-parse --short HEAD) .
```

`.dockerignore` 는 실행 jar 하나만 빌드 컨텍스트에 넣는다(`.env`·키 파일이 이미지에 섞이지 않게).

## 3. 이미지 업로드 — `.github/workflows/publish-image.yml`

| 항목 | 내용 |
|---|---|
| 트리거 | `dev`·`main` 에 대한 `push` 만(`pull_request` 에서는 돌지 않음) |
| 권한 | `contents: read`, `packages: write` — 로그인은 워크플로의 `GITHUB_TOKEN`(별도 Secrets 없음) |
| 빌드 | `./gradlew build -x test` 로 jar 만 만든 뒤 `docker build`. 테스트는 같은 push 의 `ci.yml` 이 돌린다. 빌드 캐시는 쓰지 않는다. |
| 안전 확인 | jar 에 `application-local.properties` 가 들어 있으면 올리지 않고 실패한다 |
| 동시 실행 | 같은 브랜치에 새 push 가 오면 이전 실행은 취소 |
| 이미지 위치 | `ghcr.io/<owner>/hongikon-be` (`<owner>` = 저장소 소유자, 소문자) |

태그 규칙:

| 태그 | 붙는 때 | 용도 |
|---|---|---|
| `sha-xxxxxxx` | 항상(커밋 해시 앞 7자리) | 배포·롤백 기준. 바뀌지 않는다 |
| `dev` / `main` | push 된 브랜치 이름 | 그 브랜치의 최신 이미지(움직이는 태그) |
| `latest` | `main` 에서만 | `main` 과 같다 |

> `ci.yml` 과 따로 돌기 때문에 테스트가 실패한 커밋도 이미지는 올라갈 수 있다. 배포할 때는 그 커밋의 `CI / build` 가 통과했는지 보고 `sha-` 태그로 고른다.
> 처음 올라간 패키지는 GitHub 의 Packages 설정에서 공개 여부를 확인한다(서버에서 받으려면 비공개일 때 `read:packages` 권한 토큰으로 `docker login ghcr.io` 가 필요).

**아직 자동 배포는 연결하지 않았다.** 서버에서 쓸 때는 직접 받아 배포 스크립트에 넘긴다.

```bash
IMAGE_NAME=ghcr.io/<owner>/hongikon-be ENV_FILE=/path/to/.env ./scripts/deploy.sh sha-xxxxxxx   # 로컬에 없으면 pull 한다
```

## 4. 배포 스크립트 초안 — `scripts/deploy.sh`

자동 실행에 연결하지 않은 초안이다. 서버에서 이미지를 만든 뒤 직접 실행한다.

```bash
ENV_FILE=/path/to/.env ./scripts/deploy.sh <image-tag>
```

1. 지금 컨테이너를 멈추고 `<이름>-prev` 로 바꿔 둔다(지우지 않음)
2. `<IMAGE_NAME>:<image-tag>` 로 새 컨테이너 기동(`--env-file`, `--restart unless-stopped`)
3. `HEALTH_URL`(기본 `http://127.0.0.1:8080/reports`)이 200 을 줄 때까지 대기
4. 성공 → `-prev` 삭제 / 실패·시간 초과·컨테이너 종료 → 새 컨테이너 로그 출력 후 삭제, `-prev` 를 원래 이름으로 다시 기동

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `ENV_FILE` | (필수) | 컨테이너에 넘길 env 파일 경로 |
| `IMAGE_NAME` | `hongikon-be` | 이미지 이름 |
| `CONTAINER_NAME` | `hongikon-be` | 컨테이너 이름 |
| `PUBLISH` | `127.0.0.1:8080:8080` | `docker run -p` 값 |
| `HEALTH_URL` | `http://127.0.0.1:8080/reports` | 헬스체크 주소 |
| `HEALTH_TIMEOUT` / `HEALTH_INTERVAL` | `180` / `5` | 최대 대기·간격(초) |
| `EXTRA_RUN_ARGS` | 없음 | `docker run` 추가 인자 |

> 처음 쓰기 전에 서버의 현재 `docker run` 옵션(컨테이너 이름, 포트, 네트워크 등)과 위 기본값이 맞는지 확인하고, 다르면 환경변수로 맞춘다.
> DB 스키마 변경(`db/*.sql`)은 스크립트가 하지 않는다 — 지금처럼 `docs/deploy-runbook-*.md` 순서대로 먼저 적용한다(`ddl-auto=validate`).

서버 주소·계정·키 경로·버킷 이름 같은 값은 이 저장소(문서·스크립트·워크플로)에 적지 않는다. 필요하면 서버의 env 파일이나 GitHub Secrets 이름으로만 참조한다.

## 5. 다음 단계

1. ~~**이미지 레지스트리 업로드**~~ — 완료(3절, ghcr.io).
2. **SSM 기반 배포** — SSH 키 없이 `aws ssm send-command` 로 (AWS 인증은 액세스 키 대신 GitHub OIDC + IAM 역할) 서버에서 `docker pull` + `scripts/deploy.sh <sha>` 를 실행한다. 대상 인스턴스 ID 등은 Secrets/변수로만 둔다. 처음엔 `workflow_dispatch`(수동 승인)로 시작한다.
3. **DB 마이그레이션 도구 검토** — 지금은 `db/*.sql` 을 손으로 적용한다. Flyway(또는 Liquibase)로 옮기면 배포 순서 실수(스키마 누락으로 기동 실패)를 줄일 수 있다. 기존 운영 DB 는 baseline 부터 잡아야 한다.
