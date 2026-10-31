package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 학생이 방법·절차를 물었는지. '신청 방법' 섹션(답 규칙 13)은 방법을 물을 때만 만든다(10-31 운영진 요청 —
 * '홍아람'처럼 이름만 물었는데 지원 절차까지 길게 나왔다). 지시만으로는 모델이 자주 어겨서 서버가 판단해 넘기고,
 * 방법을 묻지 않았는데 섹션이 오면 뺀다.
 */
final class AskQuestionIntent {

    static final String APPLY_SECTION_TITLE = "신청 방법";

    private static final Pattern ASKS_HOW = Pattern.compile(
            "어떻게|어케|방법|절차|순서|하려면|되려면|하면\\s*돼|하면\\s*되|어디서|어디에서|어디로|어디에\\s*(내|제출|신청)|"
                    + "서류|준비물|제출|자격|조건|누가\\s*할\\s*수|신청하고\\s*싶|지원하고\\s*싶|가입하고\\s*싶|"
                    + "신청할래|지원할래|가입할래|신청하는\\s*법|지원하는\\s*법|가입하는\\s*법|how",
            Pattern.CASE_INSENSITIVE);

    private AskQuestionIntent() {
    }

    static boolean asksHow(String question) {
        return question != null && ASKS_HOW.matcher(question).find();
    }

    /** 답 쓰기 메시지에 넣는 한 줄. */
    static String contextLine(String question) {
        return asksHow(question)
                ? "[질문 종류] 방법·절차를 묻는 질문 — 답 규칙 13대로 '신청 방법' 섹션을 쓴다.\n\n"
                : "[질문 종류] 방법·절차를 묻지 않은 질문 — '신청 방법' 섹션을 만들지 않는다(답 규칙 13).\n\n";
    }

    /**
     * 방법을 묻지 않았는데 온 '신청 방법' 섹션을 뺀다. 방법을 물었는데 제목이 빈 순서 목록이 오면 '신청 방법'으로 채운다
     * (10-31 로컬 확인: 모델이 제목 없이 누가·언제·어디서·어떻게만 보내는 일이 있었다).
     */
    static List<AskRouteResponse.Section> filter(String question, List<AskRouteResponse.Section> sections) {
        if (sections == null) {
            return null;
        }
        if (asksHow(question)) {
            return sections.stream()
                    .map(section -> (section.title() == null || section.title().isBlank()) && section.ordered()
                            ? new AskRouteResponse.Section(APPLY_SECTION_TITLE, section.items(), true, section.table())
                            : section)
                    .toList();
        }
        return sections.stream().filter(section -> !APPLY_SECTION_TITLE.equals(section.title())).toList();
    }
}
