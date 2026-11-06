"""물어보기 RAG 색인 만들기(맥미니). 백엔드 ask/sources.json(안내 원문) → 문단 → bge-m3 벡터.

출력(src/main/resources/ask/): rag-chunks.json(문단: 안내 id·글, 홈페이지 쪽 정보), rag-vectors.bin(float16, 문단 수 × 1024, 리틀엔디언).
안내 원문 말고도 FE scripts/ask/data/rag-*.json(학과 홈페이지 등 원문 그대로 넣는 자료 — depthome.py)을 'page:<key>' 문단으로 함께 넣는다.
안내 원문(sources.json)을 다시 만들면(FE export-catalog) 이 스크립트도 다시 돌린다. 서버는 둘의 version 이 같을 때만 색인을 쓴다.
문단 자르기: 줄 단위로 모아 600자 안팎, 안내 첫 줄(제목·요약)을 문단 앞에 붙인다(10-28 실험과 같은 규칙).
실행: ./venv/bin/python -I build_index.py
  기본은 이어 만들기: 지금 색인(rag-chunks.json·rag-vectors.bin)에서 모델이 같고 글이 똑같은 문단은 벡터를 그대로 쓰고,
  새로 생기거나 바뀐 문단만 계산한다(팁 몇 개 추가면 1분 안쪽). 문단 자르기·모델을 바꿨거나 처음부터 다시 만들려면 RAG_FULL=1
  (CPU 로 30분 안팎. EMBED_DEVICE=mps 면 빠르지만 다른 빌드와 메모리를 다툰다). RAG_OUT_DIR 로 다른 곳에 쓸 수 있다(시험용), RAG_PREV_DIR 로 벡터를 가져올 색인을 따로 정할 수 있다.
"""
import json, os, sys, time

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASK = os.path.join(ROOT, 'src', 'main', 'resources', 'ask')
# 원문 그대로 넣는 자료(FE 레포). 다른 곳에 있으면 RAG_PAGES_DIR 로 바꾼다.
PAGES_DIR = os.environ.get('RAG_PAGES_DIR', os.path.join(ROOT, '..', 'hongikon-fe', 'scripts', 'ask', 'data'))
PAGE_TEXT_MAX = 6000
MODEL_ID = os.environ.get('EMBED_MODEL', 'BAAI/bge-m3')
OUT_DIR = os.environ.get('RAG_OUT_DIR', ASK)
# 이어 만들 때 벡터를 가져올 지금 색인(기본: 쓰는 곳과 같은 곳).
PREV_DIR = os.environ.get('RAG_PREV_DIR', OUT_DIR)
FULL = os.environ.get('RAG_FULL') == '1'
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


def pages():
    """rag-*.json 의 쪽들 → ({'page:key': 원문}, {'page:key': {title, dept, url, text}})."""
    texts, meta = {}, {}
    for name in sorted(os.listdir(PAGES_DIR)) if os.path.isdir(PAGES_DIR) else []:
        if not (name.startswith('rag-') and name.endswith('.json')):
            continue
        data = json.load(open(os.path.join(PAGES_DIR, name), encoding='utf-8'))
        # rag-depthome.json 은 {"pages": [...]}, rag-tips.json 은 [...] 그대로 — 둘 다 받는다(11-04).
        for p in data['pages'] if isinstance(data, dict) else data:
            pid = 'page:' + p['key']
            label = f"{p['dept']} · {p['title']}" if p['key'].startswith('tip-') else f"{p['dept']} 홈페이지 · {p['title']}"
            texts[pid] = label + '\n' + p['text']
            meta[pid] = {'title': label, 'dept': p['dept'], 'url': p['url'], 'text': p['text'][:PAGE_TEXT_MAX]}
    return texts, meta


def previous():
    """지금 색인의 {문단 글: 벡터}. 없거나 모델이 다르면 빈 dict(처음부터 계산)."""
    chunks_path, vectors_path = os.path.join(PREV_DIR, 'rag-chunks.json'), os.path.join(PREV_DIR, 'rag-vectors.bin')
    if FULL or not (os.path.exists(chunks_path) and os.path.exists(vectors_path)):
        return {}
    old = json.load(open(chunks_path, encoding='utf-8'))
    if old.get('model') != MODEL_ID:
        return {}
    vectors = np.fromfile(vectors_path, dtype='<f2').reshape(len(old['chunks']), old['dim'])
    return {c['text']: vectors[i] for i, c in enumerate(old['chunks'])}


def main():
    src = json.load(open(os.path.join(ASK, 'sources.json'), encoding='utf-8'))
    page_texts, page_meta = pages()
    chunks = chunk(src['sources']) + chunk(page_texts)
    print(f'홈페이지 등 {len(page_meta)}쪽', flush=True)
    print(f'{len(chunks)} 문단 (sources version {src["version"]})', flush=True)
    started = time.time()
    known = previous()
    todo = sorted({c['text'] for c in chunks if c['text'] not in known})
    print(f'다시 쓰는 벡터 {len(chunks) - sum(1 for c in chunks if c["text"] not in known)} · 새로 계산 {len(todo)} 문단'
          + (' (RAG_FULL=1)' if FULL else ''), flush=True)
    if todo:
        from sentence_transformers import SentenceTransformer
        model = SentenceTransformer(MODEL_ID, device=os.environ.get('EMBED_DEVICE', 'cpu'))
        model.max_seq_length = 512
        fresh = model.encode(todo, batch_size=8, normalize_embeddings=True, show_progress_bar=True)
        known.update({text: fresh[i].astype('<f2') for i, text in enumerate(todo)})
    vectors = np.stack([known[c['text']] for c in chunks]).astype('<f2')
    dim = vectors.shape[1]
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, 'rag-vectors.bin'), 'wb') as f:
        f.write(vectors.tobytes())
    json.dump({'version': src['version'], 'model': MODEL_ID, 'dim': dim, 'chunks': chunks, 'pages': page_meta},
              open(os.path.join(OUT_DIR, 'rag-chunks.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    print(f'완료 {time.time() - started:.0f}s, {len(chunks)} × {dim}', flush=True)


if __name__ == '__main__':
    sys.exit(main())
