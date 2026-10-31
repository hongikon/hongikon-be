"""물어보기 임베딩 서버(맥미니). 문장을 bge-m3 벡터(1024차원, 길이 1로 맞춤)로 바꿔 준다. docs/ask-embedding-plan.md.

- POST /embed  {"texts": ["…", …]}  → {"model": "BAAI/bge-m3", "vectors": [[…], …]}   (한 번에 최대 32문장, 문장당 최대 2000자)
- GET  /health → {"ok": true, "model": …}
- 키: 요청 머리 X-Embed-Key 가 환경변수 EMBED_SERVER_KEY 와 같아야 한다(비어 있으면 서버가 뜨지 않는다). 질문 글은 기록하지 않는다.
- 주소: EMBED_SERVER_HOST(기본 127.0.0.1)·EMBED_SERVER_PORT(기본 8090). 운영 서버와는 사설 터널(Tailscale 등) 주소에만 연다 — 공인 포트로 열지 않는다.
실행: python3 -m venv venv && ./venv/bin/pip install -r requirements.txt && EMBED_SERVER_KEY=… ./venv/bin/python server.py
"""
import hmac, json, os, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL_ID = os.environ.get('EMBED_MODEL', 'BAAI/bge-m3')
KEY = os.environ.get('EMBED_SERVER_KEY', '')
HOST = os.environ.get('EMBED_SERVER_HOST', '127.0.0.1')
PORT = int(os.environ.get('EMBED_SERVER_PORT', '8090'))
MAX_TEXTS = 32
MAX_CHARS = 2000
MAX_BODY = 200_000

if len(KEY) < 16:
    sys.exit('EMBED_SERVER_KEY(16자 이상)가 없어 서버를 띄우지 않는다.')

from sentence_transformers import SentenceTransformer  # noqa: E402  키 확인 뒤에 무거운 모델을 읽는다

# 질문은 짧아 CPU 로도 수십 ms 다. GPU(MPS)는 앱 빌드·에뮬레이터와 메모리를 다퉈 쓰지 않는다(10-28 실험에서 메모리 부족).
_model = SentenceTransformer(MODEL_ID, device=os.environ.get('EMBED_DEVICE', 'cpu'))
_model.max_seq_length = 512
_lock = threading.Lock()


def embed(texts):
    with _lock:
        return _model.encode(texts, batch_size=8, normalize_embeddings=True, show_progress_bar=False).tolist()


class Handler(BaseHTTPRequestHandler):
    server_version = 'hongikon-embed'

    def log_message(self, fmt, *args):  # 요청 줄만 남긴다(본문·질문 글은 남기지 않는다)
        sys.stderr.write('%s %s\n' % (self.command, self.path))

    def _send(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self):
        return hmac.compare_digest(self.headers.get('X-Embed-Key', ''), KEY)

    def do_GET(self):
        if self.path != '/health':
            return self._send(404, {'error': 'not found'})
        if not self._authorized():
            return self._send(403, {'error': 'forbidden'})
        self._send(200, {'ok': True, 'model': MODEL_ID})

    def do_POST(self):
        if self.path != '/embed':
            return self._send(404, {'error': 'not found'})
        if not self._authorized():
            return self._send(403, {'error': 'forbidden'})
        length = int(self.headers.get('Content-Length') or 0)
        if length <= 0 or length > MAX_BODY:
            return self._send(413, {'error': 'body size'})
        try:
            texts = json.loads(self.rfile.read(length)).get('texts')
        except (ValueError, AttributeError):
            return self._send(400, {'error': 'json'})
        if not isinstance(texts, list) or not texts or len(texts) > MAX_TEXTS or not all(isinstance(t, str) for t in texts):
            return self._send(400, {'error': 'texts'})
        self._send(200, {'model': MODEL_ID, 'vectors': embed([t[:MAX_CHARS] for t in texts])})


class Server(ThreadingHTTPServer):
    # 기본 대기열(5)이면 동시에 6개 넘게 연결이 몰릴 때 나머지를 거부했다(10-31: 동시 20개 중 14개 거부 → 물어보기 503).
    request_queue_size = 128
    daemon_threads = True


if __name__ == '__main__':
    embed(['준비'])  # 첫 요청이 느리지 않게 미리 한 번 돌린다
    print(f'embed server {MODEL_ID} on {HOST}:{PORT}', flush=True)
    Server((HOST, PORT), Handler).serve_forever()
