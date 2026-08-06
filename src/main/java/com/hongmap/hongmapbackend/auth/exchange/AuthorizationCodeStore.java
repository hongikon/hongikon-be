package com.hongmap.hongmapbackend.auth.exchange;

import java.util.Optional;

public interface AuthorizationCodeStore {

    String issue(Long userId);

    Optional<Long> consume(String code);
}
