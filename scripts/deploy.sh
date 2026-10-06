#!/usr/bin/env bash
# 백엔드 컨테이너 교체 배포 (초안).
# 아직 CI 나 다른 자동화에 연결하지 않았다 — 서버에서 사람이 직접 실행한다. 사용법은 docs/ci-cd.md.
#
#   ENV_FILE=/path/to/.env ./scripts/deploy.sh <image-tag>
#
# 1) 지금 컨테이너를 멈추고 <이름>-prev 로 이름을 바꿔 둔다(지우지 않음 → 롤백 때 그대로 다시 띄운다)
# 2) <IMAGE_NAME>:<image-tag> 로 새 컨테이너를 띄운다
# 3) HEALTH_URL 이 200 을 줄 때까지 기다린다
# 4) 성공하면 -prev 컨테이너를 지우고, 실패하면 새 컨테이너를 지우고 -prev 를 원래 이름으로 되돌려 다시 띄운다
#
# 서버 주소·비밀값은 이 파일에 넣지 않는다. 모두 환경변수로 받는다.
#   ENV_FILE         (필수) 컨테이너에 넘길 env 파일 경로(docker run --env-file)
#   IMAGE_NAME       이미지 이름 (기본 hongikon-be)
#   CONTAINER_NAME   컨테이너 이름 (기본 hongikon-be)
#   PUBLISH          docker run -p 값 (기본 127.0.0.1:8080:8080 — 앞단 nginx 만 접근)
#   HEALTH_URL       헬스체크 주소 (기본 http://127.0.0.1:8080/reports)
#   HEALTH_TIMEOUT   헬스체크 최대 대기 초 (기본 180)
#   HEALTH_INTERVAL  헬스체크 간격 초 (기본 5)
#   EXTRA_RUN_ARGS   docker run 에 더 붙일 인자 (선택, 공백으로 나뉨)
set -euo pipefail

TAG="${1:-}"
if [[ -z "$TAG" ]]; then
  echo "사용법: ENV_FILE=/path/to/.env $0 <image-tag>" >&2
  exit 2
fi

: "${ENV_FILE:?ENV_FILE 환경변수가 필요합니다}"
IMAGE_NAME="${IMAGE_NAME:-hongikon-be}"
CONTAINER_NAME="${CONTAINER_NAME:-hongikon-be}"
PUBLISH="${PUBLISH:-127.0.0.1:8080:8080}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/reports}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"
EXTRA_RUN_ARGS="${EXTRA_RUN_ARGS:-}"

IMAGE="${IMAGE_NAME}:${TAG}"
PREV_NAME="${CONTAINER_NAME}-prev"

log() { echo "[deploy $(date -u +%H:%M:%S)] $*"; }

container_exists() { docker container inspect "$1" >/dev/null 2>&1; }

if [[ ! -r "$ENV_FILE" ]]; then
  echo "ENV_FILE 을 읽을 수 없습니다: $ENV_FILE" >&2
  exit 2
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  log "로컬에 $IMAGE 가 없어 pull 을 시도합니다"
  docker pull "$IMAGE"
fi

# 지난 배포가 중간에 끊겨 남은 -prev 가 있으면 지금 컨테이너가 없을 때만 되살릴 수 있게 둔다.
if container_exists "$PREV_NAME"; then
  if container_exists "$CONTAINER_NAME"; then
    log "이전 배포에서 남은 $PREV_NAME 을 지웁니다"
    docker rm -f "$PREV_NAME" >/dev/null
  else
    echo "$CONTAINER_NAME 없이 $PREV_NAME 만 남아 있습니다. 상태를 직접 확인한 뒤 다시 실행하세요." >&2
    exit 1
  fi
fi

HAD_PREV=false
PREV_IMAGE=""
if container_exists "$CONTAINER_NAME"; then
  PREV_IMAGE="$(docker container inspect -f '{{.Config.Image}}' "$CONTAINER_NAME")"
  log "기존 컨테이너 중지: $CONTAINER_NAME ($PREV_IMAGE)"
  docker stop "$CONTAINER_NAME" >/dev/null
  docker rename "$CONTAINER_NAME" "$PREV_NAME"
  HAD_PREV=true
else
  log "기존 컨테이너가 없습니다 — 새로 띄웁니다"
fi

rollback() {
  log "롤백: 새 컨테이너 로그(마지막 50줄)"
  docker logs --tail 50 "$CONTAINER_NAME" 2>&1 || true
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  if [[ "$HAD_PREV" == true ]]; then
    docker rename "$PREV_NAME" "$CONTAINER_NAME"
    docker start "$CONTAINER_NAME" >/dev/null
    log "이전 컨테이너($PREV_IMAGE)를 다시 띄웠습니다"
  else
    log "되돌릴 이전 컨테이너가 없습니다"
  fi
  exit 1
}

log "새 컨테이너 기동: $IMAGE"
# shellcheck disable=SC2086 # EXTRA_RUN_ARGS 는 일부러 단어 단위로 나눈다
if ! docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --env-file "$ENV_FILE" \
  -p "$PUBLISH" \
  $EXTRA_RUN_ARGS \
  "$IMAGE" >/dev/null; then
  rollback
fi

log "헬스체크: $HEALTH_URL (최대 ${HEALTH_TIMEOUT}s)"
deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
while true; do
  if [[ "$(docker container inspect -f '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null)" != "true" ]]; then
    log "새 컨테이너가 멈췄습니다"
    rollback
  fi
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || true)"
  if [[ "$code" == "200" ]]; then
    break
  fi
  if (( $(date +%s) >= deadline )); then
    log "헬스체크 시간 초과 (마지막 응답 코드: ${code:-없음})"
    rollback
  fi
  sleep "$HEALTH_INTERVAL"
done

log "헬스체크 통과 (200)"
if [[ "$HAD_PREV" == true ]]; then
  docker rm "$PREV_NAME" >/dev/null
  log "이전 컨테이너를 지웠습니다. 이전 이미지는 남아 있습니다: $PREV_IMAGE"
fi
log "배포 완료: $IMAGE"
