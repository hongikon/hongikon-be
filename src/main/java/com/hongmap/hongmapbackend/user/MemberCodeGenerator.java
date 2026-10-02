package com.hongmap.hongmapbackend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 새 회원 번호를 만든다. 숫자 6자리는 SecureRandom 으로 뽑아 순서·가입자 수를 추측할 수 없게 하고,
 * 이미 쓰인 번호면 다시 뽑는다(최대 {@link #MAX_ATTEMPTS}번).
 *
 * <p>중복 확인은 JPA 가 아니라 JdbcTemplate 으로 한다 — 엔티티 저장 콜백(@PrePersist) 안에서 불리므로
 * 영속성 컨텍스트를 건드리지(flush 시키지) 않아야 한다. 같은 트랜잭션의 커넥션을 그대로 쓴다.
 * 확인과 INSERT 사이에 다른 가입이 같은 번호를 가져가는 경합은 유니크 인덱스가 막는다(그 가입 한 건만 실패, 재로그인하면 새 번호).
 */
@Component
public class MemberCodeGenerator {

    static final int MAX_ATTEMPTS = 10;

    private final String prefix;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public MemberCodeGenerator(@Value("${app.member-code.school-prefix:HIU}") String prefix, JdbcTemplate jdbcTemplate) {
        this.prefix = MemberCodes.requireValidPrefix(prefix == null ? null : prefix.trim());
        this.jdbcTemplate = jdbcTemplate;
    }

    public String prefix() {
        return prefix;
    }

    /** 중복 확인 없이 형식만 맞춘 번호 하나. */
    String randomCode() {
        int number = MemberCodes.MIN_NUMBER + random.nextInt(MemberCodes.MAX_NUMBER - MemberCodes.MIN_NUMBER + 1);
        return MemberCodes.format(prefix, number);
    }

    /** 아직 아무도 쓰지 않은 번호. {@link #MAX_ATTEMPTS}번 모두 겹치면 IllegalStateException(번호 공간이 거의 찼다는 뜻). */
    public String nextAvailable() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("회원 번호를 " + MAX_ATTEMPTS + "번 뽑았지만 모두 사용 중입니다. 접두사·자릿수를 늘려야 합니다.");
    }

    boolean exists(String memberCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE member_code = ?", Integer.class, memberCode);
        return count != null && count > 0;
    }
}
