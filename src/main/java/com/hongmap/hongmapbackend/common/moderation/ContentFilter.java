package com.hongmap.hongmapbackend.common.moderation;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자 글(지금은 제보 댓글) 올리기 전 서버 필터 — App Store 1.2 "불쾌한 사용자 생성 콘텐츠를 거르는 방법".
 * 댓글은 사후 검토(바로 공개)라 올리는 순간 여기서 한 번 거른다. 제보는 사전 검토(PENDING → 관리자 승인)라 쓰지 않는다.
 *
 * <ul>
 *   <li>링크: http(s)://, www., 흔한 도메인(xxx.com·.co.kr·.me …), "닷컴"·"(.)"·"dot com" 같은 돌려 쓰기.</li>
 *   <li>연락처: 한국 휴대폰·지역번호·050 안심번호(구분자·공백·+82 포함), 이메일, 오픈채팅·카톡 아이디 문구.</li>
 *   <li>욕설·혐오·성적 표현: {@code moderation/banned-words.txt} 목록. 정규화(소문자, 문장부호·숫자·제로폭 문자 제거,
 *       같은 글자 반복 줄이기, 한 글자씩 띄운 낱말 붙이기) 뒤 부분 문자열로 찾는다 — "시 발", "시1발", "ㅅㅂ" 를 잡는다.
 *       {@code moderation/allowed-words.txt} 의 낱말(시발점·등신대·수박씨 …)은 먼저 가려서 오탐을 막는다.</li>
 * </ul>
 * 외부 API 없이 결정적으로, 200자 댓글 기준 마이크로초 단위. 내용은 로그에 남기지 않는다(개인정보) — 호출부가 사유만 센다.
 * 목록은 운영 중 리소스 파일만 고쳐 늘릴 수 있다(한 줄에 하나, # 뒤는 주석, 정규화는 코드가 똑같이 한다).
 */
@Component
public class ContentFilter {

    static final String BANNED_RESOURCE = "moderation/banned-words.txt";
    static final String ALLOWED_RESOURCE = "moderation/allowed-words.txt";

    // ---------- 링크 ----------
    private static final String TLD = "(?:co\\.kr|or\\.kr|ac\\.kr|go\\.kr|ne\\.kr|com|net|org|kr|io|me|ly|co|xyz|info|biz|site"
            + "|shop|link|app|gg|to|tv|us|so|page|kakao|ai|cc|im|ws|be)";
    private static final Pattern LINK = Pattern.compile(
            "https?:/*|hxxps?:|www\\."
                    + "|[a-z0-9-]\\.(?:[a-z0-9-]+\\.)*" + TLD + "(?![a-z0-9])"
                    // 한글 뒤 점은 문장 끝일 수 있어("좋아요. Me!") 흔한 도메인만 본다(네이버.com)
                    + "|[가-힣]\\.(?:co\\.kr|com|net|org|kr)(?![a-z0-9])"
                    // naver닷com · naverdotcom · naver점kr
                    + "|[a-z0-9](?:dot|닷|점)(?:com|net|org|kr|컴|넷)(?![a-z])"
                    + "|닷컴|닷넷|닷케이알|닷오알지|dotcom|dotnet");
    /** "(.)" "[dot]" "{닷}" → "." */
    private static final Pattern BRACKETED_DOT = Pattern.compile("[(\\[{<](?:\\.|dot|닷|점)[)\\]}>]");

    // ---------- 연락처 ----------
    /** 숫자 덩어리: 숫자 사이에 공백·하이픈·점·괄호 등 0~3개를 허용한다(010 1234 5678, (02)123-4567). */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\+?\\d(?:[\\s\\-.·_/()~]{0,3}\\d)+");
    private static final Pattern GROUP_SPLIT = Pattern.compile("[^0-9]+");
    /** 국내 번호(앞 0 포함): 휴대폰 01X, 지역번호, 인터넷전화 070, 안심번호 050X. +82 는 앞 0 없이 와도 잡는다. */
    private static final Pattern PHONE = Pattern.compile(
            "(?:820?|0)(?:1[016789]|2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|70|50[2-8])\\d{7,8}");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}");
    /** 공백을 지운 글에서 찾는다. */
    private static final List<String> CONTACT_PHRASES = List.of(
            "오픈채팅", "오픈톡", "오픈카톡", "오카방", "open.kakao", "openkakao", "오픈채팅방",
            "카톡아이디", "카톡id", "카톡아디", "카톡친추", "카카오톡아이디", "카카오톡id", "카카오아이디", "카카오id",
            "kakaoid", "kakaotalkid", "katalkid", "텔레그램아이디", "텔레그램id", "텔레아이디", "텔레id", "라인아이디", "라인id",
            "인스타아이디", "인스타id", "디엠주세요", "dm주세요", "갠톡주세요", "갠톡해", "연락처남겨", "번호남겨");

    // ---------- 욕설 ----------
    private static final Pattern NOT_LETTER = Pattern.compile("[^가-힣ㄱ-ㅎㅏ-ㅣa-z]");
    /** 한글 사이에 끼운 영문 한두 글자("시x발")는 지운다. */
    private static final Pattern LATIN_BETWEEN_HANGUL = Pattern.compile("(?<=[가-힣])[a-z]{1,2}(?=[가-힣])");
    private static final Pattern REPEAT = Pattern.compile("(.)\\1+");
    /** 보이지 않는 문자: 제로폭, 소프트 하이픈, 한글 채움 문자(빈칸처럼 쓰임), BOM. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u034F\\u115F\\u1160\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u3164\\uFEFF\\uFFA0]");
    private static final char MASK = '_';

    private final List<String> banned;
    private final List<String> allowed;

    public ContentFilter() {
        this(readWords(BANNED_RESOURCE), readWords(ALLOWED_RESOURCE));
        if (banned.isEmpty()) {
            // 목록이 빠진 채로 배포되면 욕설 필터가 조용히 꺼진다 — 기동을 멈춘다.
            throw new IllegalStateException(BANNED_RESOURCE + " 이 없거나 비었다");
        }
    }

    ContentFilter(Collection<String> bannedWords, Collection<String> allowedWords) {
        this.banned = normalizeWords(bannedWords);
        this.allowed = normalizeWords(allowedWords);
    }

    /** 걸리면 첫 사유(연락처 → 링크 → 욕설 순), 아니면 empty. null·빈 글은 empty. */
    public Optional<ContentViolation> check(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String base = base(text);
        String compact = base.replaceAll("\\s+", "");
        if (hasContact(base, compact)) {
            return Optional.of(ContentViolation.CONTACT);
        }
        if (LINK.matcher(BRACKETED_DOT.matcher(compact).replaceAll(".")).find()) {
            return Optional.of(ContentViolation.LINK);
        }
        if (hasBannedWord(base)) {
            return Optional.of(ContentViolation.PROFANITY);
        }
        return Optional.empty();
    }

    // ---------- 단계별 ----------

    /** NFC, 전각 영숫자 → 반각, 소문자, 보이지 않는 문자 제거. 공백은 남긴다. */
    static String base(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(nfc.length());
        for (int i = 0; i < nfc.length(); i++) {
            char c = nfc.charAt(i);
            if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            } else if (c == '　') {
                c = ' ';
            }
            sb.append(c);
        }
        return INVISIBLE.matcher(sb.toString().toLowerCase()).replaceAll("");
    }

    private static boolean hasContact(String base, String compact) {
        if (EMAIL.matcher(compact).find()) {
            return true;
        }
        for (String phrase : CONTACT_PHRASES) {
            if (compact.contains(phrase)) {
                return true;
            }
        }
        String digits = base.replace("공일공", "010").replace("영일영", "010");
        Matcher run = DIGIT_RUN.matcher(digits);
        while (run.find()) {
            if (isPhone(run.group())) {
                return true;
            }
        }
        return false;
    }

    /** 숫자 묶음 i..j 를 이어 붙여 전화번호 꼴인지 본다 — 앞에 다른 숫자("3층 010…")가 붙어도 잡는다. */
    private static boolean isPhone(String run) {
        String[] groups = GROUP_SPLIT.split(run.replace("+", ""));
        for (int i = 0; i < groups.length; i++) {
            StringBuilder joined = new StringBuilder();
            for (int j = i; j < groups.length; j++) {
                joined.append(groups[j]);
                if (joined.length() > 13) {
                    break;
                }
                if (PHONE.matcher(joined).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBannedWord(String base) {
        StringBuilder text = new StringBuilder(profanityText(base));
        for (String ok : allowed) {
            int at = text.indexOf(ok);
            while (at >= 0) {
                for (int k = at; k < at + ok.length(); k++) {
                    text.setCharAt(k, MASK);
                }
                at = text.indexOf(ok, at + ok.length());
            }
        }
        String masked = text.toString();
        for (String word : banned) {
            if (masked.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 낱말마다 글자만 남기고(숫자·문장부호 제거, 한글 사이 영문 제거, 'ㅡ' 제거, 반복 줄이기) 공백으로 잇는다.
     * 한 글자 낱말이 이어지면("시 발", "ㅅ ㅂ", "씨 1 발") 붙인다. 두 글자 이상 낱말끼리는 붙이지 않는다 —
     * "다시 발급"·"3시 발표" 를 "시발" 로 잘못 읽지 않기 위해서다.
     */
    static String profanityText(String base) {
        List<String> tokens = new ArrayList<>();
        for (String raw : base.split("\\s+")) {
            String t = normalizeToken(raw);
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        StringBuilder out = new StringBuilder();
        String prev = null;
        for (String t : tokens) {
            boolean glue = prev != null && prev.length() == 1 && t.length() == 1;
            if (prev != null && !glue) {
                out.append(' ');
            }
            out.append(t);
            prev = t;
        }
        // 붙인 뒤 다시 반복 줄이기("시 시 발" → "시발")
        return REPEAT.matcher(out).replaceAll("$1");
    }

    private static String normalizeToken(String raw) {
        String t = NOT_LETTER.matcher(raw).replaceAll("");
        t = LATIN_BETWEEN_HANGUL.matcher(t).replaceAll("");
        t = t.replace("ㅡ", "");
        return REPEAT.matcher(t).replaceAll("$1");
    }

    private static List<String> normalizeWords(Collection<String> words) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : words) {
            String n = normalizeToken(base(w).replaceAll("\\s+", ""));
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }

    static List<String> readWords(String resource) {
        ClassPathResource file = new ClassPathResource(resource);
        if (!file.exists()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int hash = line.indexOf('#');
                String word = (hash >= 0 ? line.substring(0, hash) : line).strip();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " 을 읽지 못했다", e);
        }
        return words;
    }

    int bannedCount() {
        return banned.size();
    }
}
