package com.hongmap.hongmapbackend.user;

/**
 * 회원 상태. SUSPENDED(관리자 정지)는 로그인·조회는 그대로 되지만 제보·신고·문의·닉네임 변경 같은 쓰기가 막힌다
 * (SuspendedUserInterceptor). 정지·해제는 관리자 API(/admin/users/{id}/suspend, /unsuspend)로 한다.
 */
public enum UserStatus {
    ACTIVE, SUSPENDED
}
