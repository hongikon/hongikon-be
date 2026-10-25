
  - (저녁) 최신 공지 기준으로 기존 안내 바로잡기 + 공지 본문 찾기:
    - 공지 찾기(`AskLiveData.searchNotices`, 도구 search_notices)가 제목뿐 아니라 본문도 본다(제목 3점·본문 1점). Q&A 공지 본문에만 있는 사실을 찾게.
    - 놓친 낱말 어미 정리(`AskMissingTerms`: '결석하면'→'결석', '공결돼'→'공결').
    - ask/catalog.json·sources.json 다시 생성(FE fixes.py 반영, 765노드).
    - 백엔드 테스트 662 통과.
    > **미결**: '정정기간에 결석하면 공결돼?'는 후보에 새 안내(faq-registration)가 있어도 모델이 출석인정 안내로만 답한다(공지 2076은 정정기간 새 신청 결석은 일괄 공결).
