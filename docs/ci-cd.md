# CI / CD (2026-10-06, CD 추가 2026-10-08)

자동으로 도는 것은 **CI(빌드·테스트)**, **이미지 업로드(ghcr.io)**, **운영 배포(CD, 승인 필요)** 세 가지다. 배포 흐름은 5절.

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

운영 배포는 5절의 `deploy.yml` 이 한다. 서버에서 손으로 할 때는 직접 받아 배포 스크립트에 넘긴다.

```bash
IMAGE_NAME=ghcr.io/<owner>/hongikon-be ENV_FILE=/path/to/.env ./scripts/deploy.sh sha-xxxxxxx   # 로컬에 없으면 pull 한다
```

## 4. 배포 스크립트 — `scripts/deploy.sh`

5절의 CD 가 서버에서 실행한다(서버의 `~/deploy.sh` 복사본이 아니라 배포 대상 커밋의 이 파일을 보낸다). 손으로 직접 실행할 수도 있다.

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

## 5. 운영 배포(CD) — `.github/workflows/deploy.yml`

SSH 키·AWS 액세스 키 없이 **GitHub OIDC → IAM 역할 → SSM Run Command** 로 서버에서 배포한다.
서버·AWS 값은 저장소에 적지 않고 GitHub Environment **`production`** 의 변수로만 읽는다.

| 변수(Environment `production` → Variables) | 용도 |
|---|---|
| `AWS_REGION` | 리전 |
| `AWS_ROLE_ARN` | OIDC 로 assume 할 역할 |
| `EC2_INSTANCE_ID` | SSM 명령을 보낼 서버 |

> 저장소가 Public 이라 Actions 로그도 누구나 볼 수 있다. 워크플로가 역할 ARN·인스턴스 ID 를 `::add-mask::` 로 가리고 계정 ID 도 가리지만, 서버 출력(stdout/stderr)은 그대로 로그에 남으니 서버 쪽 스크립트에 비밀값을 찍는 명령을 넣지 않는다.

### 5.1 흐름

```
main push → CI / Publish image(sha-xxxxxxx 업로드)
          └ Publish image 성공 → Deploy(workflow_run)
               guard   : 대상 커밋 확인, db/ 변경 있으면 멈춤
               deploy  : [승인 대기] → OIDC → SSM → 서버에서 pull·태그·deploy.sh·확인
```

| 트리거 | 대상 | dry_run | sql_applied |
|---|---|---|---|
| `workflow_run` — `Publish image` 가 **main** push 에서 성공 | `workflow_run.head_sha`, 태그 `sha-<앞 7자리>` | `false`(실제 배포) | `false` |
| `workflow_dispatch`(Actions → Deploy → Run workflow) | 입력 `image_tag`(`sha-1234567`) 의 커밋 | 입력(기본 `true`) | 입력(기본 `false`) |

- **guard**(권한 `contents: read`, Environment 없음): 대상 커밋이 `origin/main` 에 포함돼 있는지 보고(이미지는 dev·main 둘 다에서 만들어지지만 운영 배포는 main 커밋만 허용 — dev 에만 있는 `sha-` 태그는 거부), 첫 번째 부모와 비교해 `db/` 아래 바뀐 파일을 찾는다. 바뀐 게 있는데 `sql_applied` 가 `true` 가 아니면 파일 목록을 찍고 실패한다.
- **deploy**(권한 `id-token: write`, `contents: read`, `packages: read`, Environment `production`): 동시에 하나만 돈다(`concurrency: deploy-production`, 진행 중인 배포는 취소하지 않음 — 대기 중인 실행이 이미 있으면 GitHub 이 더 오래된 대기 실행을 취소하고 최신 것만 남긴다).
  서버에서 할 일(실제 배포일 때):
  1. job 의 `GITHUB_TOKEN` 으로 `docker login ghcr.io --password-stdin` → `docker pull ghcr.io/hongikon/hongikon-be:<태그>` → 바로 `docker logout`(스크립트가 어떻게 끝나든 `trap` 으로 한 번 더 logout)
  2. 지금 돌고 있는 `hongikon-be` 컨테이너의 이미지를 `hongikon-be:before-<태그>` 로 태그(롤백용)
  3. 대상 커밋의 `scripts/deploy.sh` 를 base64 로 보내 임시 파일로 풀어 실행: `ENV_FILE=/home/ubuntu/hongikon-be/.env IMAGE_NAME=ghcr.io/hongikon/hongikon-be deploy.sh <태그>`
  4. 확인: `GET /reports` 200, 비로그인 `GET /admin/users` 401 이 아니면 실패
- 러너는 SSM 명령이 끝날 때까지 10초마다 상태를 보고, 서버 stdout/stderr 를 로그에 펼친다. `Success` 가 아니거나 30분이 넘으면(명령 취소) job 이 실패한다.
- `AWS-RunShellScript` 는 `sh` 로 돌기 때문에 서버로 보내는 본문은 base64 로 감싸 `bash` 로 실행한다.

### 5.2 승인 버튼

Environment `production` 에 **Required reviewers** 를 걸어 두면 deploy job 이 승인 대기로 멈춘다.
Actions → 해당 **Deploy** 실행 화면 위쪽의 노란 상자 **Review deployments** → `production` 체크 → **Approve and deploy**.
(설정: Settings → Environments → `production` → Deployment protection rules. 거기서 Deployment branches 를 `main` 으로 제한해 두면 다른 브랜치에서 수동 실행해도 배포 job 이 돌지 않는다.)

### 5.3 db/ 가 바뀐 커밋은 멈춘다

`ddl-auto=validate` 라 스키마가 앞서 있지 않으면 새 컨테이너가 뜨지 않는다. 그리고 SQL 적용은 되돌리기 어렵기 때문에 사람이 확인하고 한다.

1. guard 로그의 파일 목록과 `docs/deploy-runbook-*.md` 를 본다.
2. **RDS 스냅샷**을 먼저 만든다.
3. SQL 을 운영 DB 에 적용한다.
4. Actions → Deploy → Run workflow — `image_tag` = 막힌 실행 요약에 나온 태그, `dry_run` = **해제**, `sql_applied` = **체크**.

> guard 는 대상 커밋과 **첫 번째 부모 사이만** 본다. PR 머지 커밋이면 그 PR 전체가 잡히지만, 앞선 main 커밋의 배포가 실패·건너뛰어진 채로 다음 커밋을 배포하면 앞 커밋의 `db/` 변경은 잡히지 않는다. 그럴 땐 마지막으로 배포된 태그부터 `git diff --name-only <이전 sha> <새 sha> -- db/` 로 직접 확인한다.

### 5.4 dry_run — 인증·SSM 경로 시험

`workflow_dispatch` 의 기본값이 `dry_run=true` 다. 서버에서 `whoami`, `docker ps`, SSM 에이전트 상태, `GET /reports` 상태 코드만 찍고 **로그인·pull·교체는 하지 않는다**(토큰도 보내지 않는다).
처음 설정했을 때, 역할·인스턴스를 바꿨을 때 먼저 돌린다. dry run 도 guard 를 거치므로 `db/` 가 바뀐 커밋의 태그면 `sql_applied` 를 체크해야 지나간다(dry run 은 아무것도 바꾸지 않으므로 체크해도 된다).

### 5.5 롤백

- **자동**: `deploy.sh` 가 새 컨테이너 기동 실패·종료·헬스체크(`/reports` 200) 시간 초과면 새 컨테이너를 지우고 직전 컨테이너(`hongikon-be-prev`)를 원래 이름으로 다시 띄운다. 이때 job 은 실패로 끝난다.
- **배포 후 확인 실패는 자동 롤백하지 않는다**: `/reports` 200·`/admin/users` 401 확인에서 실패하면 새 컨테이너는 그대로 떠 있고 job 만 실패한다 — 아래처럼 `hongikon-be:before-<태그>` 로 수동 복구한다.
- **수동**: deploy.sh 는 통과했는데 배포 후 확인(`/admin/users` 401 등)에서 실패했거나, 배포 뒤에 문제를 발견했을 때.
  - 이전 `sha-` 태그를 알면: Run workflow 로 그 태그를 `dry_run` 해제해 다시 배포한다(가장 간단).
  - 서버에서 바로: 배포 직전 이미지가 `hongikon-be:before-<배포한 태그>` 로 남아 있다.
    ```bash
    ENV_FILE=/home/ubuntu/hongikon-be/.env IMAGE_NAME=hongikon-be ./scripts/deploy.sh before-<배포한 태그>
    ```
- DB 는 롤백하지 않는다. 새 스키마가 옛 코드와 안 맞으면 5.3 에서 만든 스냅샷을 기준으로 판단한다.
- `before-*` 태그는 배포마다 쌓이므로 가끔 `docker image prune` / `docker rmi` 로 정리한다.

### 5.6 처음 설정할 때

- **workflow_run 은 기본 브랜치(main)에 `deploy.yml` 이 있어야 동작한다.** dev 에만 있으면 자동 배포가 일어나지 않는다. `workflow_dispatch` 의 Run workflow 버튼도 기본 브랜치에 파일이 있어야 보인다.
- IAM 역할 신뢰 정책: `token.actions.githubusercontent.com`, `aud` = `sts.amazonaws.com`, `sub` = `repo:hongikon/hongikon-be:environment:production` 로 좁힌다.
- 역할 권한: 그 인스턴스에 대한 `ssm:SendCommand`(문서 `AWS-RunShellScript`), `ssm:GetCommandInvocation`, `ssm:CancelCommand` 정도만.
- ghcr 패키지 설정(Package settings → Manage Actions access)에 이 저장소가 읽기 권한으로 들어 있어야 job 의 `GITHUB_TOKEN` 으로 pull 된다.
- 서버의 지금 컨테이너 이름이 `hongikon-be`, 포트 `127.0.0.1:8080:8080` 인지(4절 기본값) 확인한다.

### 5.7 토큰이 서버·AWS 쪽에 남는 문제

실제 배포 때 `GITHUB_TOKEN` 은 SSM 명령 본문 안에(base64 로 감싸져) 들어간다. 로그에는 찍지 않지만 다음 곳에는 남는다.

- SSM Run Command 기록(콘솔 Run Command 기록, `ssm:ListCommands`) — 명령 파라미터가 그대로 보인다.
- 서버의 SSM 에이전트 작업 디렉터리(`/var/lib/amazon/ssm/<인스턴스>/document/orchestration/<명령 ID>/`, root 만 읽기).

이 토큰은 `packages: read`·`contents: read` 만 있고 **job 이 끝나면 만료된다**(최대 24시간). 그래도 줄이려면:
SSM 기록을 볼 수 있는 IAM 권한을 좁히고, 패키지를 Public 으로 바꿔 로그인 자체를 없애거나, 토큰 대신 SSM Parameter Store SecureString 을 서버가 직접 읽게 바꾼다.

## 6. 다음 단계

1. ~~**이미지 레지스트리 업로드**~~ — 완료(3절, ghcr.io).
2. ~~**SSM 기반 배포**~~ — 완료(5절, OIDC + SSM, Environment 승인).
3. **DB 마이그레이션 도구 검토** — 지금은 `db/*.sql` 을 손으로 적용한다. Flyway(또는 Liquibase)로 옮기면 배포 순서 실수(스키마 누락으로 기동 실패)를 줄일 수 있다. 기존 운영 DB 는 baseline 부터 잡아야 한다.
