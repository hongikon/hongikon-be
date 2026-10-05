package com.hongmap.hongmapbackend.user;

/**
 * 관리자가 회원을 이용 정지·해제했을 때(AdminUserService) 발행. 커밋 뒤 AccountStatusPushDispatcher 가 비동기로 받아
 * 그 회원에게 알림을 보낸다(이용 제한 사유 고지·이의 제기 안내 — 공정위 불공정약관 심사 지침 2019).
 *
 * @param suspended true 면 정지(사유 변경 포함), false 면 해제
 * @param reason    정지 사유(해제면 null)
 */
public record UserSuspensionChangedEvent(Long userId, boolean suspended, String reason) {
}
