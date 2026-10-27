"""물어보기 RAG 색인 만들기(맥미니). 백엔드 ask/sources.json(안내 원문) → 문단 → bge-m3 벡터.

출력(src/main/resources/ask/): rag-chunks.json(문단: 안내 id·글), rag-vectors.bin(float16, 문단 수 × 1024, 리틀엔디언).
안내 원문(sources.json)을 다시 만들면(FE export-catalog) 이 스크립트도 다시 돌린다. 서버는 둘의 version 이 같을 때만 색인을 쓴다.
문단 자르기: 줄 단위로 모아 600자 안팎, 안내 첫 줄(제목·요약)을 문단 앞에 붙인다(10-28 실험과 같은 규칙).
실행: ./venv/bin/python build_index.py   (CPU 로 30분 안팎. EMBED_DEVICE=mps 면 빠르지만 다른 빌드와 메모리를 다툰다)
"""
import json, os, struct, sys, time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASK = os.path.join(ROOT, 'src', 'main', 'resources', 'ask')
MODEL_ID = os.environ.get('EMBED_MODEL', 'BAAI/bge-m3')
CHUNK_CHARS = 600
HEAD_CHARS = 120


def chunk(sources):
    out = []
    for node_id, text in sources.items():
        lines = [line for line in text.split('\n') if line.strip()]
        head = lines[0][:HEAD_CHARS] if lines else ''
        buf = ''
        for line in lines:
            if buf and len(buf) + len(line) > CHUNK_CHARS:
                out.append({'id': node_id, 'text': head + '\n' + buf.rstrip()})
                buf = ''
            buf += line + '\n'
        if buf:
            out.append({'id': node_id, 'text': head + '\n' + buf.rstrip()})
    return out


def main():
    src = json.load(open(os.path.join(ASK, 'sources.json'), encoding='utf-8'))
    chunks = chunk(src['sources'])
    print(f'{len(chunks)} 문단 (sources version {src["version"]})', flush=True)
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(MODEL_ID, device=os.environ.get('EMBED_DEVICE', 'cpu'))
    model.max_seq_length = 512
    started = time.time()
    vectors = model.encode([c['text'] for c in chunks], batch_size=8, normalize_embeddings=True, show_progress_bar=True)
    dim = vectors.shape[1]
    with open(os.path.join(ASK, 'rag-vectors.bin'), 'wb') as f:
        f.write(vectors.astype('<f2').tobytes())
    json.dump({'version': src['version'], 'model': MODEL_ID, 'dim': dim, 'chunks': chunks},
              open(os.path.join(ASK, 'rag-chunks.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    print(f'완료 {time.time() - started:.0f}s, {len(chunks)} × {dim}', flush=True)


if __name__ == '__main__':
    sys.exit(main())
