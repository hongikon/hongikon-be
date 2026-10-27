# 물어보기 임베딩 서버(맥미니)

물어보기 RAG 검색용. 문장을 bge-m3 벡터로 바꿔 준다. 질문 글은 기록하지 않는다. 설계·실험 결과는 `docs/ask-embedding-plan.md`.

## 처음 한 번

```sh
cd hongikon-be/tools/embed-server
python3 -m venv venv && ./venv/bin/pip install -r requirements.txt   # 모델(약 2.3GB)은 첫 실행 때 받는다
# hongikon-be/.env 에 ASK_EMBED_KEY=<무작위 48자>(openssl rand -hex 24) 와 ASK_RAG_ENABLED=true
```

## 실행

- 손으로: `./run.sh` (127.0.0.1:8090)
- 맥미니가 켜질 때 자동으로(launchd). 이 등록은 운영자가 직접 한다:

```sh
cd hongikon-be/tools/embed-server
sed "s#__DIR__#$(pwd)#g; s#__HOME__#$HOME#g" launchd.plist.template > ~/Library/LaunchAgents/com.hongikon.embed-server.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.hongikon.embed-server.plist
# 확인
curl -s -H "X-Embed-Key: $(sed -n 's/^ASK_EMBED_KEY=//p' ../../.env)" localhost:8090/health
# 끄기
launchctl bootout gui/$(id -u)/com.hongikon.embed-server
```

- 잠자기를 끄지 않으면 맥미니가 잠들 때 물어보기가 503 이 된다: 시스템 설정 → 에너지 → '디스플레이가 꺼져 있을 때 자동으로 잠자기 방지' 켜기.

## 원문 색인 다시 만들기

FE `node scripts/ask/export-catalog.mjs` 로 `ask/sources.json` 이 바뀌면 반드시 다시 만든다. 서버는 색인 version 이 카탈로그와 다르면 RAG 를 끈다.

```sh
./venv/bin/python -I build_index.py   # CPU 로 15~30분 → src/main/resources/ask/rag-chunks.json · rag-vectors.bin
```

## 운영 서버와 잇기

공인 포트를 열지 않는다. EC2 와 맥미니를 사설 터널(Tailscale 등)로 잇고, 서버를 그 터널 주소에만 연다.
- 맥미니: `EMBED_SERVER_HOST=<터널 IP>`
- EC2 `.env`: `ASK_EMBED_URL=http://<맥미니 터널 IP>:8090`, `ASK_EMBED_KEY`, `ASK_RAG_ENABLED=true`
