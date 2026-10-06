package com.hongmap.hongmapbackend.user;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 앱 닉네임 규칙. 앞뒤 공백을 지운 뒤 2~12자, 한글(완성형)·영문·숫자·밑줄만,
 * 운영진·학교를 사칭할 수 있는 단어는 대소문자 무시하고 포함만 돼도 거절한다.
 * 프론트(src/utils/nickname.ts)도 같은 규칙을 쓰므로 바꾸면 함께 바꾼다.
 */
public final class AppNicknamePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 12;

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z0-9_]+$");

    static final List<String> RESERVED_WORDS = List.of(
            "운영", "운영진", "관리자", "admin", "홍익온", "hongikon", "공식", "학교", "학생회");

    private AppNicknamePolicy() {
    }

    /** null·공백만 있으면 null(= 지우기), 아니면 앞뒤 공백을 지운 값. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 규칙에 어긋나면 사용자에게 보여 줄 문구를 돌려준다. normalize 를 거친 값을 넣는다. */
    public static Optional<String> violation(String nickname) {
        int length = nickname.codePointCount(0, nickname.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return Optional.of("닉네임은 " + MIN_LENGTH + "~" + MAX_LENGTH + "자로 입력해 주세요.");
        }
        if (!ALLOWED.matcher(nickname).matches()) {
            return Optional.of("닉네임에는 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.");
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        String compact = lower.replace("_", "");
        for (String word : RESERVED_WORDS) {
            if (lower.contains(word) || compact.contains(word)) {
                return Optional.of("'" + word + "'처럼 운영진이나 학교로 오해할 수 있는 단어는 쓸 수 없어요.");
            }
        }
        return Optional.empty();
    }
}
