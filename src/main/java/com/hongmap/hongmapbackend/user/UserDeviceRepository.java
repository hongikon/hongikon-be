package com.hongmap.hongmapbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPushToken(String pushToken);

    List<UserDevice> findByUserIdAndActiveTrue(Long userId);

    void deleteByUserId(Long userId);

    /**
     * 새 소식 하나의 푸시 대상 기기(활성, 지정 토큰 종류). 아래 중 하나라도 해당하는 유저의 기기:
     * <ul>
     *   <li>학과 게시판 글(departmentId 있음) → 그 학과를 구독한 유저(user_departments)</li>
     *   <li>대학공지(departmentId 없음) → 그 category를 끄지 않은 유저(notification_categories에 enabled = false 행이 없음).
     *       한 번도 저장하지 않은 카테고리는 켜짐으로 본다 — NotificationCategoryService.getUserCategories()가 화면에 보여주는 값과 같은 기준.</li>
     *   <li>위와 무관하게 제목에 구독 키워드가 들어간 유저(keyword_subscriptions, 대소문자 무시)</li>
     * </ul>
     * 세 기준을 한 쿼리의 OR로 묶어 기기 행이 한 번씩만 나온다 — 여러 기준에 걸린 유저도 같은 기기로 중복 발송되지 않는다.
     */
    @Query("""
            SELECT d FROM UserDevice d
            WHERE d.active = true
              AND d.tokenType = :tokenType
              AND (
                   (:departmentId IS NOT NULL AND d.user.id IN (
                        SELECT ud.user.id FROM UserDepartment ud WHERE ud.department.id = :departmentId))
                OR (:departmentId IS NULL AND d.user.id NOT IN (
                        SELECT nc.user.id FROM NotificationCategory nc
                        WHERE nc.category = :category AND nc.enabled = false))
                OR d.user.id IN (
                        SELECT ks.user.id FROM KeywordSubscription ks
                        WHERE LOCATE(LOWER(ks.keyword), LOWER(:title)) > 0)
              )
            """)
    List<UserDevice> findPushTargets(
            @Param("tokenType") TokenType tokenType,
            @Param("departmentId") Long departmentId,
            @Param("category") String category,
            @Param("title") String title
    );

    /** Expo가 DeviceNotRegistered로 알려준 토큰(앱 삭제 등)의 기기를 비활성화한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserDevice d SET d.active = false, d.updatedAt = CURRENT_TIMESTAMP WHERE d.pushToken IN :pushTokens AND d.active = true")
    int deactivateByPushTokens(@Param("pushTokens") Collection<String> pushTokens);
}
