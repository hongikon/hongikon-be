package com.hongmap.hongmapbackend.ask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문에서 학생 상황 칸(규정 그래프 condition 칸, {@link AskRuleGraph#CONDITION_KEYS})을 읽는다.
 * 조건이 붙은 선(장학생·분할납부·4학년 …)은 학생이 그 상황을 말했을 때만 답 쓰기에 넘긴다 — 상황을 모르는데 넘기면
 * 모델이 '장학생이면 달라요'처럼 해당 없는 규정으로 답을 채우고 원래 안내의 방법·결과를 빠뜨렸다(10-31 그래프 켬/끔 평가,
 * docs/ask-rule-graph-plan.md 9절 '상황 정리 단계'의 1차). 질문 글은 저장하지 않는다.
 */
final class AskSituation {

    private static final Pattern GRADE = Pattern.compile("([1-5])\\s*학년");
    private static final Pattern FRESHMAN = Pattern.compile("신입생|새내기");
    private static final Pattern SEMESTERS = Pattern.compile("([1-9])\\s*학기\\s*(를|까지)?\\s*(이수|다니|마치|마쳤|다녔|들었)");
    private static final Map<String, Pattern> FLAGS = Map.of(
            "장학생", Pattern.compile("장학"),
            "분할납부", Pattern.compile("분할"),
            "졸업유예", Pattern.compile("졸업\\s*유예|학위\\s*취득\\s*유예|유예생"),
            // 군복무: 지금 복무 중·전역한 학생. '입대해'(앞으로 갈 학생)는 넣지 않는다 — 전역 뒤 복학 규정이 입대 전 질문에 붙었다(11-10 R15).
            "휴학중", Pattern.compile("휴학\\s*중|휴학하고\\s*있|휴학생|휴학한\\s*상태|휴학\\s*상태"),
            "군복무", Pattern.compile("군대|군\\s*복무|전역|제대|복무\\s*중"));

    /**
     * 질문이 시점을 말하는지(11-10 평가 — Haiku 손해 오류의 가장 큰 원인이 날짜 단정이었다: 시점을 안 물었는데 '신청 기간이 지났다').
     * 말하지 않았으면 답 쓰기에 오늘 날짜를 주지 않는다 — 규칙으로 답한다.
     */
    private static final Pattern TIME = Pattern.compile(
            "지금|오늘|내일|모레|이번|올해|현재|언제|며칠|몇\\s*일|몇\\s*시|마감|열려|열었|열어|닫|요즘|다음\\s*주|이따|아직|벌써|늦었|지났|당장");

    private AskSituation() {
    }

    static boolean mentionsTime(String question) {
        return question != null && TIME.matcher(question).find();
    }

    /** 질문에서 읽은 상황 칸. 학년·이수학기는 숫자, 나머지는 true. 없으면 빈 Map. */
    static Map<String, Object> from(String question) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (question == null || question.isBlank()) {
            return out;
        }
        Matcher grade = GRADE.matcher(question);
        if (grade.find()) {
            out.put("학년", Integer.parseInt(grade.group(1)));
        } else if (FRESHMAN.matcher(question).find()) {
            out.put("학년", 1);
        }
        Matcher semesters = SEMESTERS.matcher(question);
        if (semesters.find()) {
            out.put("이수학기", Integer.parseInt(semesters.group(1)));
        }
        FLAGS.forEach((key, pattern) -> {
            if (pattern.matcher(question).find()) {
                out.put(key, true);
            }
        });
        return out;
    }

    /** 선의 조건 칸이 모두 상황에 있고 값이 맞으면 true. 조건이 없으면 true. */
    static boolean satisfies(Map<String, Object> condition, Map<String, Object> situation) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            Object have = situation.get(entry.getKey());
            Object want = entry.getValue();
            if (have == null) {
                return false;
            }
            if (want instanceof Number number && !(have instanceof Number got && got.intValue() == number.intValue())) {
                return false;
            }
            if (Boolean.FALSE.equals(want)) {
                return false;
            }
        }
        return true;
    }
}
