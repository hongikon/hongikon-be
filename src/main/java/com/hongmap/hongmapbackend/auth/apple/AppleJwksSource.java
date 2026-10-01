package com.hongmap.hongmapbackend.auth.apple;

import java.util.List;

/**
 * Apple 공개키 목록(JWKS)을 가져오는 곳. 운영은 {@link HttpAppleJwksSource}, 테스트는 로컬에서 만든 키를 돌려주는 스텁을 쓴다.
 */
public interface AppleJwksSource {

    /** 현재 Apple이 서명에 쓰는 RSA 공개키들. 네트워크 오류 등은 예외로 던진다. */
    List<AppleJwk> fetchKeys();
}
