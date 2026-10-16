-- 물어보기 서버 답 기록(ask_conversation_logs) 조회 예시 — 개선할 곳 찾기·회귀 평가셋 만들기.
-- 질문·답은 가려져 있다([학번] 등). 원문 복원은 하지 않는다.

-- 1) 최근 7일 결과 분포와 '해결됨' 비율
SELECT outcome, COUNT(*) AS n,
       SUM(resolved = 1) AS resolved_yes, SUM(resolved = 0) AS resolved_no,
       ROUND(AVG(latency_ms)) AS avg_ms
FROM ask_conversation_logs
WHERE created_at >= NOW() - INTERVAL 7 DAY
GROUP BY outcome ORDER BY n DESC;

-- 2) 고칠 후보: 확인 안 됨·답 없음·실패·'해결 안 됨' (최근 것부터)
SELECT id, created_at, outcome, resolved, question, node_ids, LEFT(answer, 200) AS answer
FROM ask_conversation_logs
WHERE outcome IN ('NOT_FOUND', 'NO_ANSWER', 'FAILED') OR resolved = 0
ORDER BY created_at DESC LIMIT 100;

-- 3) 자주 묻는데 못 찾는 질문(같은 질문 묶기)
SELECT question, COUNT(*) AS n
FROM ask_conversation_logs
WHERE outcome IN ('NOT_FOUND', 'NO_ANSWER')
GROUP BY question ORDER BY n DESC LIMIT 50;

-- 4) 한 대화 다시 보기(이어 묻기가 잘 풀렸는지)
SELECT turn, question, outcome, LEFT(answer, 300) AS answer, steps
FROM ask_conversation_logs
WHERE conversation_id = '<대화 id>'
ORDER BY turn;

-- 5) 비용(토큰) — 하루별
SELECT DATE(created_at) AS day, COUNT(*) AS n,
       SUM(input_tokens) AS input_tokens, SUM(cache_read_tokens) AS cache_read, SUM(output_tokens) AS output_tokens
FROM ask_conversation_logs
GROUP BY DATE(created_at) ORDER BY day DESC LIMIT 30;

-- 6) 회귀 평가셋 뽑기: '해결됨' 답(정답 후보)과 '해결 안 됨' 질문(실패 후보)
SELECT question, node_ids, resolved FROM ask_conversation_logs WHERE resolved IS NOT NULL ORDER BY created_at DESC;
