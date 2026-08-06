package com.hongmap.hongmapbackend.auth.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAuthorizationCodeStore implements AuthorizationCodeStore {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public InMemoryAuthorizationCodeStore(@Value("${app.auth.exchange-code-ttl-seconds:60}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String issue(Long userId) {
        codes.entrySet().removeIf(entry -> entry.getValue().isExpired());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        codes.put(code, new CodeEntry(userId, Instant.now().plusSeconds(ttlSeconds)));
        return code;
    }

    @Override
    public Optional<Long> consume(String code) {
        CodeEntry entry = codes.remove(code);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private record CodeEntry(Long userId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
