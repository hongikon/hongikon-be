package com.hongmap.hongmapbackend.common.moderation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 댓글 필터 — 실제 리소스 목록(banned-words.txt·allowed-words.txt)으로 검사한다. */
class ContentFilterTest {

    private final ContentFilter filter = new ContentFilter();

    // ---------- 링크 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "여기 보세요 https://example.com/a",
            "http://bit.ly/abc 들어가 봐요",
            "www.naver.com 에 있어요",
            "WWW.NAVER.COM",
            "hongik.ac.kr 공지 확인",
            "naver.com",
            "naver . com 검색",
            "n a v e r . c o m",
            "bit.ly/3xYz",
            "abc.co.kr 참고",
            "promo.io 가입",
            "my.me 로 와",
            "naver닷컴",
            "naver 닷 com",
            "naver dot com",
            "naver(.)com",
            "naver[dot]com",
            "네이버.com",
            "ｎａｖｅｒ．ｃｏｍ",
            "goo​.gl​.com",
    })
    void 링크는_막는다(String text) {
        assertThat(filter.check(text)).contains(ContentViolation.LINK);
    }

    // ---------- 연락처 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678 로 연락 주세요",
            "01012345678",
            "010 1234 5678",
            "010.1234.5678",
            "010 - 1234 - 5678",
            "+82 10-1234-5678",
            "+821012345678",
            "02-123-4567 가게 번호",
            "(02)1234-5678",
            "031-123-4567",
            "070-1234-5678",
            "0505-123-4567",
            "3층 010 1234 5678",
            "공일공-1234-5678",
            "０１０－１２３４－５６７８",
            "오픈채팅 들어와요",
            "오픈 채팅방 링크 드릴게요",
            "open.kakao.com/o/abc",
            "카톡 아이디 abc123",
            "카톡ID: hong",
            "카카오톡 id 알려줘요",
            "메일 hong@gmail.com 으로",
    })
    void 연락처와_오픈채팅은_막는다(String text) {
        assertThat(filter.check(text)).contains(ContentViolation.CONTACT);
    }

    // ---------- 욕설 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "시발",
            "아 씨발 줄 너무 길다",
            "시 발",
            "씨 1 발",
            "시1발",
            "시..발",
            "씨~~~발",
            "씨빨",
            "ㅅㅂ",
            "ㅅ ㅂ 진짜",
            "ㅆㅂㅆㅂ",
            "시x발",
            "시​발",
            "시ㅤ발", // 한글 채움 문자(U+3164)
            "SIBAL",
            "tlqkf",
            "병신같네",
            "병1신",
            "ㅄ",
            "개새끼",
            "개 새 끼",
            "좆같다",
            "존나 짜증",
            "지랄하네",
            "닥쳐",
            "미친년",
            "fuck you",
            "f.u.c.k",
            "한남충",
            "김치녀",
            "짱깨",
            "섹스",
    })
    void 욕설_혐오_성적_표현은_막는다(String text) {
        assertThat(filter.check(text)).contains(ContentViolation.PROFANITY);
    }

    // ---------- 정상 글 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "지금 줄 짧아요",
            "붕어빵 3개에 2,000원이에요!",
            "3시 발표 끝나고 갈게요",
            "학생증 다시 발급 받으러 왔어요",
            "다시발급 받았어요",
            "여기가 시발점이에요",
            "수박씨발라 먹는 중",
            "솜씨발휘 대박",
            "등신대 전시 중이에요",
            "위기가 닥쳐오고 있다",
            "불이 꺼져 있어요",
            "새끼 고양이 있어요",
            "미친 듯이 맛있어요",
            "보지 마세요 스포 있음",
            "자지 말고 공부하자",
            "10개년 계획",
            "하루 세끼 다 먹음",
            "무지개같은 하늘",
            "2026.10.04 14:00 시작",
            "10/4 3시 T동 302호",
            "가격 15,000원, 학번 B812345",
            "좋아요. Me too!",
            "끝났어요. 다음에 봐요",
            "1.5배 길어요",
            "오픈 시간은 10시예요",
            "카톡으로 공지 왔어요",
            "ㅋㅋㅋㅋ 대박",
            "아저씨 발 조심하세요",
    })
    void 정상_글은_통과한다(String text) {
        assertThat(filter.check(text)).isEmpty();
    }

    @Test
    void 빈_글과_null_은_통과() {
        assertThat(filter.check(null)).isEmpty();
        assertThat(filter.check("   ")).isEmpty();
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_리소스에서_읽고_주석과_빈_줄은_건너뛴다() {
        List<String> words = ContentFilter.readWords(ContentFilter.BANNED_RESOURCE);
        assertThat(words).contains("시발", "fuck").noneMatch(w -> w.startsWith("#") || w.isBlank());
        assertThat(filter.bannedCount()).isGreaterThan(50);
    }

    @Test
    void 허용_목록이_금칙어를_가린다() {
        ContentFilter custom = new ContentFilter(List.of("바보"), List.of("바보상자"));
        assertThat(custom.check("바보")).contains(ContentViolation.PROFANITY);
        assertThat(custom.check("바 보")).contains(ContentViolation.PROFANITY);
        assertThat(custom.check("바보상자 보는 중")).isEmpty();
        // 허용 낱말 밖에 금칙어가 또 있으면 잡는다
        assertThat(custom.check("바보상자 보는 바보")).contains(ContentViolation.PROFANITY);
    }

    @Test
    void 목록_파일이_없으면_빈_목록() {
        assertThat(ContentFilter.readWords("moderation/없는파일.txt")).isEmpty();
        assertThat(new ContentFilter(List.of(), List.of()).check("x")).isEmpty();
    }
}
