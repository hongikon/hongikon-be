package com.hongmap.hongmapbackend.ask;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 다른 대학 정보 섞임 출구 검사(docs/ask-scope-policy.md 3장). 모델 판단과 무관하게 서버 코드로 막는다.
 *
 * 답에 다른 대학 이름이 나오면 그 이름이 이번 답의 근거로 읽은 홍익대 원문(ground)에도 있어야 통과한다 —
 * 행복기숙사 참여 대학, 동아리 교류전처럼 우리 원문에 적힌 언급만 살리고, 모델이 덧붙이거나 지어낸 다른 대학 정보는 막는다.
 * 실시간 전송은 {@link Gate} 로 대학 이름 길이만큼 글자를 붙잡아 두고 보내, 이름 일부가 먼저 새어 나가지 않게 한다.
 */
public final class OtherSchoolGuard {

    /**
     * 다른 대학 이름 줄기. '연세대'는 연세대·연세대학교를 모두 잡는다.
     * '연대'·'이대'·'고대' 같은 줄임말은 일반 낱말(연대보증·이대로·고대 유적)과 겹쳐 넣지 않는다 — 입구 판정과 답 쓰기 지시가 막는다.
     * 뒤에 붙으면 대학이 아닌 말(서울대입구역, 세종대왕 등)은 제외 패턴으로 거른다.
     */
    static final List<String> NAMES = List.of(
            "서울대", "연세대", "고려대", "서강대", "성균관대", "한양대", "중앙대", "경희대", "한국외대", "한국외국어대",
            "이화여대", "이화여자대", "숙명여대", "숙명여자대", "성신여대", "성신여자대", "동덕여대", "덕성여대", "서울여대", "서울여자대",
            "건국대", "동국대", "국민대", "숭실대", "세종대", "광운대", "명지대", "상명대", "가톨릭대", "서울시립대", "시립대",
            "한성대", "삼육대", "서경대", "단국대", "인하대", "아주대", "가천대", "경기대", "한국항공대", "서울과기대", "서울과학기술대",
            "서울교대", "한국체대", "추계예술대", "배화여대", "감리교신학대", "서울여자간호대", "디지털서울문화예술대",
            "한국예술종합학교", "한예종", "카이스트", "포스텍", "포항공대", "부산대", "경북대", "전남대", "충남대", "전북대", "부경대",
            "yonsei", "korea university", "seoul national university", "sogang", "ewha", "kaist", "postech", "sungkyunkwan",
            "hanyang", "kyung hee", "chung-ang");

    /**
     * 대학 이름 뒤에 오면 대학이 아닌 말(서울대입구역·서울대공원·세종대왕·고려대장경·경기대회).
     * '서강대로'(도로)·'서강대교'(다리)는 넣지 않는다 — '연세대로 가는 길' 같은 섞임이 빠져나가므로. 우리 원문에 있으면 근거 검사로 통과한다.
     */
    private static final Pattern NOT_A_SCHOOL = Pattern.compile("^(입구|공원|왕|장경|회)");

    private static final Pattern NAME_PATTERN = Pattern.compile(
            String.join("|", NAMES.stream().map(Pattern::quote).toList()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** 이름 일부가 새어 나가지 않게 붙잡아 둘 글자 수(가장 긴 이름 + 뒤 제외 낱말). */
    static final int HOLD = NAMES.stream().mapToInt(String::length).max().orElse(0) + 3;

    private OtherSchoolGuard() {
    }

    /** 근거 원문에 없는 다른 대학 이름(처음 나온 것). 없으면 empty. */
    public static Optional<String> ungrounded(String text, String ground) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        String groundLower = ground == null ? "" : ground.toLowerCase(Locale.ROOT);
        Matcher matcher = NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String after = text.substring(matcher.end(), Math.min(text.length(), matcher.end() + 3));
            if (NOT_A_SCHOOL.matcher(after).find()) {
                continue;
            }
            if (!groundLower.contains(matcher.group().toLowerCase(Locale.ROOT))) {
                return Optional.of(matcher.group());
            }
        }
        return Optional.empty();
    }

    /**
     * 실시간 전송 길목. {@link #feed} 는 지금 내보내도 되는 글자를 돌려준다. 근거 없는 대학 이름이 보이면 그때부터 아무것도 내보내지 않는다.
     * 다 받은 뒤 {@link #finish} 로 붙잡아 둔 나머지를 내보낸다(막혔으면 빈 글자).
     */
    public static final class Gate {
        private final String ground;
        private final StringBuilder seen = new StringBuilder();
        private int sent;
        private String blockedName;

        public Gate(String ground) {
            this.ground = ground;
        }

        public String feed(String text) {
            if (blockedName != null) {
                return "";
            }
            seen.append(text);
            Optional<String> name = ungrounded(seen.toString(), ground);
            if (name.isPresent()) {
                blockedName = name.get();
                return "";
            }
            int safe = Math.max(sent, seen.length() - HOLD);
            String out = seen.substring(sent, safe);
            sent = safe;
            return out;
        }

        public String finish() {
            if (blockedName != null) {
                return "";
            }
            String out = seen.substring(sent);
            sent = seen.length();
            return out;
        }

        /** 막은 대학 이름. 안 막았으면 null. */
        public String blockedName() {
            return blockedName;
        }
    }
}
