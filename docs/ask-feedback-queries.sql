-- ============================================
-- 챗봇 질문·피드백 로그(ask_feedback_logs) 조회 예시 — 관리자 API 대신 RDS 에서 직접 돌린다.
--
-- 앱은 질문 하나에 행을 최대 두 개 남긴다:
--   1) 답변 직후: resolved = NULL
--   2) "궁금한 점이 해결됐나요?" 응답 시: resolved = 1 / 0
-- 두 행은 연결돼 있지 않다. 질문 수는 resolved IS NULL 행으로, 해결 비율은 resolved IS NOT NULL 행으로 센다.
-- 기간 조건은 created_at 인덱스를 탄다(아래는 최근 30일). created_at 은 UTC 로 저장된다(서버 JVM 기본 시간대가 UTC) — 그래서 NOW() 대신 UTC_TIMESTAMP() 와 비교한다.
-- ============================================

-- 1. 못 찾은 질문(outcome = NONE) 빈도 순 상위 20개
--    대소문자·앞뒤 공백 차이는 같은 질문으로 본다(질문은 저장 때 이미 앞뒤 공백을 뺐다).
SELECT LOWER(question)   AS question,
       COUNT(*)          AS asked,
       MAX(created_at)   AS last_asked_at
FROM ask_feedback_logs
WHERE outcome = 'NONE'
  AND resolved IS NULL
  AND created_at >= UTC_TIMESTAMP() - INTERVAL 30 DAY
GROUP BY LOWER(question)
ORDER BY asked DESC, last_asked_at DESC
LIMIT 20;

-- 2. 안내 흐름(flowId / nodeId)별 "해결 안 됨" 비율
--    응답이 적은 노드는 비율이 흔들리므로 응답 수(answered)를 같이 보고, 필요하면 HAVING answered >= 5 등으로 거른다.
SELECT flow_id,
       node_id,
       COUNT(*)                                         AS answered,
       SUM(resolved = 0)                                AS unresolved,
       ROUND(SUM(resolved = 0) / COUNT(*) * 100, 1)     AS unresolved_pct
FROM ask_feedback_logs
WHERE outcome = 'FLOW'
  AND resolved IS NOT NULL
  AND created_at >= UTC_TIMESTAMP() - INTERVAL 30 DAY
GROUP BY flow_id, node_id
ORDER BY unresolved_pct DESC, answered DESC;

-- 3. (참고) 답변 종류별 질문 수와 해결 비율
SELECT outcome,
       SUM(resolved IS NULL)                                               AS questions,
       SUM(resolved IS NOT NULL)                                           AS answered,
       ROUND(SUM(resolved = 0) / NULLIF(SUM(resolved IS NOT NULL), 0) * 100, 1) AS unresolved_pct
FROM ask_feedback_logs
WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 30 DAY
GROUP BY outcome;
