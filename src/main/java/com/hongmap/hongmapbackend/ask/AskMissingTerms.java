package com.hongmap.hongmapbackend.ask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 질문 낱말 중 고른 안내 원문에 없는 것('오승환', '컴퓨터공학과' …). 고르기가 비슷한 학교 전체 안내(교내장학금)를 골라
 * 학과 공지에만 있는 내용을 놓치던 것을 막는다(10-25 '컴퓨터공학과 동문 및 오승환 장학금' 제보).
 * 서버가 이 낱말로 공지·안내를 미리 찾아 답 쓰기에 후보로 넘기고, 기록(steps)에도 남겨 운영진이 어떤 낱말을 놓쳤는지 본다.
 */
final class AskMissingTerms {

    /** 한 질문에서 뽑는 낱말 수. */
    static final int MAX_TERMS = 3;

    /** 낱말 끝 조사·어미. 긴 것부터 지운다. */
    private static final List<String> ENDINGS = List.of(
            "이에요", "인가요", "하나요", "되나요", "할래요", "에서는", "으로는",
            "하면", "되면", "됐어", "했어", "냈어", "해도", "돼도",
            "에서", "으로", "이랑", "하고", "까지", "부터", "에게", "한테", "이야", "인데", "는데", "예요", "해요", "돼요", "이요", "돼",
            "은", "는", "이", "가", "을", "를", "에", "와", "과", "도", "만", "의", "로", "야", "요");

    /** 어느 안내에도 그대로 나오지 않는 물음 말투·흔한 말 — 놓친 낱말로 보지 않는다. */
    private static final Set<String> STOPWORDS = Set.of(
            "어떻게", "어떻게해", "언제", "언제야", "언제까지", "언제부터", "얼마", "얼마야", "얼마나", "몇개", "몇번", "몇학점",
            "해요", "해", "했어", "했는데", "할래", "하는", "하는법", "할수", "되요", "됩니까", "인가", "건가", "거야", "건데", "같은데",
            "어디", "어디서", "어디야", "무엇", "뭐야", "뭔가", "뭐", "뭐가", "뭐에", "어떤", "무슨", "몇",
            "알려줘", "알려", "알려주세", "궁금", "궁금해", "궁금합니다", "있어", "있나", "있나요", "있는", "없어", "되나", "돼", "되는",
            "해야", "해야돼", "하려면", "하면", "싶어", "싶은데", "싶어요", "대해", "대해서", "관련", "관해", "정보", "혹시", "지금",
            "이번", "오늘", "내일", "그거", "이거", "저거", "그럼", "근데", "그리고", "및", "또", "좀", "제가", "저는", "나", "내가",
            "우리", "학교", "홍익대", "홍익대학교", "홍대", "홍익", "학생", "물어봐", "물어볼", "말해줘", "설명", "설명해줘");

    private AskMissingTerms() {
    }

    /** 질문 낱말 중 원문(공백 무시, 대소문자 무시)에 없는 것, 앞에서부터 최대 {@link #MAX_TERMS}개. */
    static List<String> find(String question, String sources) {
        List<String> terms = new ArrayList<>();
        if (question == null || question.isBlank()) {
            return terms;
        }
        String haystack = sources == null ? "" : sources.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String raw : question.split("[^\\p{L}\\p{N}]+")) {
            String word = stem(raw.toLowerCase(Locale.ROOT));
            if (word.length() < 2 || STOPWORDS.contains(word) || STOPWORDS.contains(raw) || word.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if (!haystack.contains(word) && !terms.contains(word)) {
                terms.add(word);
                if (terms.size() >= MAX_TERMS) {
                    break;
                }
            }
        }
        return terms;
    }

    /** 질문의 내용 낱말(조사·물음 말투를 뺀 것), 앞에서부터 최대 limit 개. 학과 게시판 공지를 찾을 때 쓴다. */
    static List<String> words(String question, int limit) {
        List<String> words = new ArrayList<>();
        if (question == null) {
            return words;
        }
        for (String raw : question.split("[^\\p{L}\\p{N}]+")) {
            String word = stem(raw.toLowerCase(Locale.ROOT));
            if (word.length() >= 2 && !STOPWORDS.contains(word) && !STOPWORDS.contains(raw) && !words.contains(word)) {
                words.add(word);
                if (words.size() >= limit) {
                    break;
                }
            }
        }
        return words;
    }

    /** 끝 조사·어미를 한 번 지운다(지우고 두 글자 이상 남을 때만). */
    static String stem(String word) {
        for (String ending : ENDINGS) {
            if (word.endsWith(ending) && word.length() - ending.length() >= 2) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }
}
