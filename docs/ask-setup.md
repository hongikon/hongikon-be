# 물어보기 설정 — 키 넣기와 맥미니 임베딩 서버 (백엔드 담당자용)

물어보기를 켜려면 키 두 종류와 (RAG 를 쓸 때) 맥미니 임베딩 서버가 필요하다.
키 실제 값은 저장소·이슈·채팅·스크린샷에 남기지 않는다. 설정 항목 전체 표는 [`ask-architecture.md`](ask-architecture.md) 7절.

| 무엇 | 어디에 | 언제 필요 |
|---|---|---|
| `ANTHROPIC_API_KEY` | EC2 `.env` (로컬은 셸 환경변수) | 물어보기를 켤 때 항상(비면 503) |
| `ASK_DEV_KEY_SHA256` | EC2 `.env` (로컬은 실행 인자) | `/dev/ask` 를 쓸 때(비면 404) |
| `ASK_RAG_ENABLED` · `ASK_EMBED_URL` · `ASK_EMBED_KEY` | EC2 `.env` + 맥미니 | RAG 를 켤 때만(기본 끔) |

## 1. Anthropic API 키 받기

1. Anthropic Console(console.anthropic.com) → **API Keys** → **Create Key**. 이름은 `hongikon-be-prod` 처럼 쓰는 곳이 보이게 짓는다.
2. 키는 만들 때 한 번만 보인다. 바로 2절 `.env` 에 넣고, 메모장·채팅에 남기지 않는다.
3. Console 에서 월 사용 한도를 정해 둔다. 질문당 비용은 Haiku 약 $0.004, Opus 약 $0.19([`ask-reasoning-eval.md`](ask-reasoning-eval.md)).
4. 키가 새면 Console 에서 그 키를 **삭제**하고 새로 만든 뒤 2절을 다시 한다.

## 2. 운영 서버(EC2)에 넣기

서버는 키를 `application.properties` 에 적지 않고 환경변수로 읽는다. 운영 컨테이너는 `/home/ubuntu/hongikon-be/.env` 를 `--env-file` 로 읽는다.

```sh
ssh <EC2>
cd /home/ubuntu/hongikon-be
cp .env .env.bak.$(date +%Y%m%d)        # 되돌릴 수 있게
nano .env                                # 아래 줄 추가(이미 있으면 값만 바꾼다)
chmod 600 .env
```

```
ANTHROPIC_API_KEY=<1절에서 받은 키>
ASK_DEV_KEY_SHA256=<아래로 만든 64자 hex>
```

- `ASK_DEV_KEY_SHA256` 은 `/dev/ask` 암호키 **원문이 아니라** 그 SHA-256 값이다. FE `src/screens/DevPathGate.tsx` 의 `KEY_SHA256` 과 같은 값이어야 한다.
  만들기: `printf %s '<암호키 원문>' | shasum -a 256` → 앞의 64자.

**컨테이너를 다시 만들어야 반영된다.** `docker restart` 로는 `.env` 를 다시 읽지 않는다. 지금 떠 있는 이미지 태그 그대로:

```sh
ENV_FILE=/home/ubuntu/hongikon-be/.env IMAGE_NAME=<이미지> ./scripts/deploy.sh <현재 태그>
```

자세한 절차는 [`deploy-runbook-2026-10.md`](deploy-runbook-2026-10.md)·[`ci-cd.md`](ci-cd.md).

**확인:**

```sh
docker exec <컨테이너> printenv ANTHROPIC_API_KEY | cut -c1-7   # 'sk-ant-' 만 보이면 들어간 것(전체를 출력하지 않는다)
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<API 주소>/ask/route \
  -H 'Content-Type: application/json' -H 'X-Ask-Dev-Key: <암호키 원문>' -d '{"question":"휴학 신청 언제야?"}'
# 200 정상 · 404 ASK_DEV_KEY_SHA256 없음/틀림 · 503 ANTHROPIC_API_KEY 없음(RAG 를 켰다면 임베딩 서버 실패도)
```

키가 든 명령은 셸 기록에 남는다. 명령 앞에 공백을 하나 넣거나(`HISTCONTROL=ignorespace` 일 때) 확인 뒤 `history -d` 로 지운다.

## 3. 로컬(개발자 맥)

```sh
export ANTHROPIC_API_KEY=<개발용 키>      # 이 셸에만
./gradlew bootRun --args='--spring.profiles.active=local --ask.dev-key-sha256=<64자 hex>'
```

- `application-local.properties`(git 제외)는 이 환경변수를 읽기만 한다. 키를 그 파일에 직접 적지 않는다.
- jar 로 띄울 때는 `--spring.config.additional-location=file:<경로>/application-local.properties` 를 함께 준다(이 파일은 jar 에 안 들어간다). `JWT_SECRET` 도 환경변수로 필요하다.

## 4. 맥미니 임베딩 서버(RAG 를 켤 때)

RAG 는 질문을 맥미니의 bge-m3 모델로 벡터로 바꿔 안내를 찾는다. 맥미니가 꺼지거나 잠들면 물어보기가 503 이 된다(앱은 실패를 알린다).
설계는 [`ask-embedding-plan.md`](ask-embedding-plan.md), 서버 파일 설명은 `tools/embed-server/README.md`.

### 4-1. 처음 한 번 (맥미니 터미널)

```sh
cd ~/Documents/project/hongikon/hongikon-be/tools/embed-server
python3 -m venv venv && ./venv/bin/pip install -r requirements.txt     # 모델 약 2.3GB 는 첫 실행 때 받는다
openssl rand -hex 24                                                    # 임베딩 서버 키(48자)
# hongikon-be/.env (git 제외 파일)에 한 줄 추가:  ASK_EMBED_KEY=<48자>
./run.sh                                                                # 손으로 띄워 보기(127.0.0.1:8090). Ctrl+C 로 끈다
```

### 4-2. 켜질 때 자동 실행(launchd)

macOS 는 자동 실행 프로그램이 `~/Documents` 를 못 읽게 막는다(10-28 확인). 그래서 서버 파일과 키를 `~/Library/Application Support/hongikon-embed/` 에 따로 두고 거기서 띄운다.

```sh
cd ~/Documents/project/hongikon/hongikon-be/tools/embed-server
D="$HOME/Library/Application Support/hongikon-embed"; mkdir -p "$D"
cp server.py requirements.txt "$D/"
(umask 077; sed -n 's/^ASK_EMBED_KEY=//p' ../../.env > "$D/key")       # 키 파일(본인만 읽기)
printf '#!/bin/zsh\ncd "$(dirname "$0")"\nexport EMBED_SERVER_KEY="$(cat key)"\nexport EMBED_SERVER_HOST=127.0.0.1\nexec ./venv/bin/python -I server.py\n' > "$D/run.sh"
chmod 700 "$D/run.sh"
(cd "$D" && python3 -m venv venv && ./venv/bin/pip install -r requirements.txt)
sed "s#__DIR__#$D#g; s#__HOME__#$HOME#g" launchd.plist.template > ~/Library/LaunchAgents/com.hongikon.embed-server.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.hongikon.embed-server.plist

# 확인
curl -s -H "X-Embed-Key: $(cat "$D/key")" localhost:8090/health        # {"ok": true, "model": "BAAI/bge-m3"}
tail -f ~/Library/Logs/hongikon-embed-server.log
```

- **잠자기 끄기:** 시스템 설정 → 에너지 → '디스플레이가 꺼져 있을 때 자동으로 잠자기 방지' 켜기. 정전 뒤 자동으로 켜기도 켠다.
- 다시 띄우기: `launchctl kickstart -k gui/$(id -u)/com.hongikon.embed-server` (server.py 를 고쳤으면 `$D` 로 다시 복사한 뒤)
- 끄기: `launchctl bootout gui/$(id -u)/com.hongikon.embed-server`
- 키를 바꾸면 `.env`·`$D/key`·EC2 `.env` 세 곳을 같은 값으로 바꾸고 둘 다 다시 띄운다.

### 4-3. 운영 서버(EC2)와 잇기 — 사설 터널

**공인 포트(공유기 포트포워딩)는 열지 않는다.** EC2 와 맥미니를 사설 터널(Tailscale 등)로 잇는다.

1. 맥미니와 EC2 에 Tailscale 을 설치하고 같은 계정(tailnet)에 로그인한다(운영자 계정 작업).
2. 맥미니 터널 IP 확인: `tailscale ip -4` (예: 100.x.y.z).
3. 맥미니: `$D/run.sh` 의 `EMBED_SERVER_HOST=127.0.0.1` 을 그 터널 IP 로 바꾸고 다시 띄운다(kickstart). 이제 터널 안에서만 열린다.
4. EC2 `.env` 에 추가하고 컨테이너를 다시 만든다(2절):

```
ASK_RAG_ENABLED=true
ASK_EMBED_URL=http://<맥미니 터널 IP>:8090
ASK_EMBED_KEY=<4-1 의 48자, 맥미니와 같은 값>
```

5. 확인(EC2 에서): `curl -s -H "X-Embed-Key: <키>" http://<맥미니 터널 IP>:8090/health` → `{"ok": true …}` 면 연결된 것(403 = 키 다름, 응답 없음 = 터널·주소).
   그다음 2절의 `/ask/route` 호출이 200 인지 본다. 503 이면 컨테이너 안에서 터널 주소에 닿는지 확인한다.

### 4-4. 안내 원문이 바뀌었을 때

FE `export-catalog.mjs` 로 `ask/sources.json` 이 바뀌거나 FE `data/rag-*.json`(학과 홈페이지)이 바뀌면 색인을 다시 만들고 백엔드를 다시 배포한다. 서버는 색인 version 이 카탈로그와 다르면 RAG 를 끈다.

```sh
cd hongikon-be/tools/embed-server
./venv/bin/python -I build_index.py    # CPU 로 15~30분 → src/main/resources/ask/rag-chunks.json · rag-vectors.bin
```

## 5. 운영에 켜기 전 확인

- [ ] 개인정보처리방침 반영([`privacy-ask-conversation-draft.md`](privacy-ask-conversation-draft.md)). RAG 를 켜면 질문 글이 운영 서버에서 맥미니로 간다는 처리 위치도 넣는다.
- [ ] Anthropic Console 월 사용 한도 설정
- [ ] (RAG) 맥미니 자동 실행·잠자기 끄기·터널 연결 확인, 맥미니를 껐을 때 앱이 실패 안내를 보이는지 확인
- [ ] 물어보기는 웹 `/dev/ask` 에서만 쓴다. 앱 화면에는 넣지 않는다.
