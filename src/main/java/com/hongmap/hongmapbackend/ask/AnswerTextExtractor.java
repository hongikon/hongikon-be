package com.hongmap.hongmapbackend.ask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 조각조각 들어오는 구조화 출력 JSON({"answer":"…","found":…,"sourceIds":[…]})에서 answer 문자열만 풀어 낸다.
 * 답 글자를 다 받기 전에 앱으로 흘려보내려고 쓴다. answer 가 스키마 첫 칸이라 맨 앞에 온다.
 * 이스케이프(\n, \", \\uXXXX …)는 다 들어온 뒤에 풀고, 닫는 따옴표를 만나면 더 내보내지 않는다. 한 차례(응답 하나)에 하나씩 쓴다.
 */
final class AnswerTextExtractor {

    private static final Pattern ANSWER_KEY = Pattern.compile("\"answer\"\\s*:\\s*\"");

    private final StringBuilder buffer = new StringBuilder();
    private int position = -1;
    private boolean closed;

    /** 새 조각을 받아, 이번에 새로 풀린 답 글자를 돌려준다(없으면 빈 문자열). */
    String feed(String chunk) {
        buffer.append(chunk);
        if (closed) {
            return "";
        }
        if (position < 0) {
            Matcher matcher = ANSWER_KEY.matcher(buffer);
            if (!matcher.find()) {
                return "";
            }
            position = matcher.end();
        }
        StringBuilder out = new StringBuilder();
        while (position < buffer.length()) {
            char c = buffer.charAt(position);
            if (c == '"') {
                closed = true;
                break;
            }
            if (c != '\\') {
                out.append(c);
                position++;
                continue;
            }
            if (position + 1 >= buffer.length()) {
                break;
            }
            char escape = buffer.charAt(position + 1);
            if (escape == 'u') {
                if (position + 6 > buffer.length()) {
                    break;
                }
                out.append((char) Integer.parseInt(buffer.substring(position + 2, position + 6), 16));
                position += 6;
                continue;
            }
            out.append(switch (escape) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case 'f' -> '\f';
                default -> escape;
            });
            position += 2;
        }
        return out.toString();
    }
}
