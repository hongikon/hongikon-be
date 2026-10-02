-- ============================================
-- 기존 소식 카테고리 재분류 (2026-10-01, NewsCategoryClassifier 변경에 맞춤)
--   1) 장학 게시판(source_id='장학') 글, 제목에 장학|등록금|학자금, 본문에 '장학' → 장학
--   2) 그 외는 제목 키워드 규칙을 같은 순서로 다시 적용. 취업 키워드에서 '공고'를 뺐다.
-- 여러 번 실행해도 결과가 같다. Workbench 라면 먼저 SET SQL_SAFE_UPDATES = 0;
-- 실행 전 확인: SELECT category, COUNT(*) FROM news GROUP BY category;
-- ============================================

UPDATE news SET category = CASE
    WHEN source_id = '장학'
      OR title REGEXP '장학|등록금|학자금'
      OR content LIKE '%장학%'                                                  THEN '장학'
    WHEN title REGEXP '취업|채용|인턴|기업|박람회|연구원|모집'                       THEN '취업'
    WHEN title REGEXP '수강|성적|졸업|학점|교과|전공|시험|수업|계절학기|등록|휴학|복학'  THEN '수강'
    WHEN title REGEXP '축제|행사|전시|공연|대회|특강|세미나|워크숍|해커톤|공모전'        THEN '행사'
    WHEN title REGEXP '시설|공사|정전|단수|점검|보수|주차장|엘리베이터|소방|안전진단'     THEN '시설'
    WHEN title REGEXP '상담|심리|건강|보건'                                        THEN '상담'
    ELSE '공지'
END;

-- 실행 후 확인: SELECT category, COUNT(*) FROM news GROUP BY category;
--             SELECT id, title, category FROM news WHERE title LIKE '%학업지원금%';
