package com.hongmap.hongmapbackend.user;

/**
 * 관리자 여부. 관리자 지정은 앱/API가 아니라 DB에서 직접 한다:
 * {@code UPDATE users SET role = 'ADMIN' WHERE id = ?;}
 */
public enum UserRole {
    USER, ADMIN
}
