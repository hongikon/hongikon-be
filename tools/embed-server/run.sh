#!/bin/zsh
# 맥미니 자동 실행(launchd)용. 키는 hongikon-be/.env 의 ASK_EMBED_KEY 를 읽는다(plist 에 키를 적지 않는다).
cd "$(dirname "$0")"
export EMBED_SERVER_KEY="$(sed -n 's/^ASK_EMBED_KEY=//p' ../../.env)"
exec ./venv/bin/python -I server.py
