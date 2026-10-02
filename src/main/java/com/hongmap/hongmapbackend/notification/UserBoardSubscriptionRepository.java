package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBoardSubscriptionRepository extends JpaRepository<UserBoardSubscription, Long> {

    List<UserBoardSubscription> findByUser_IdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<UserBoardSubscription> findByUser_IdAndSourceId(Long userId, String sourceId);

    long countByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM UserBoardSubscription s WHERE s.user.id = :userId AND s.sourceId = :sourceId")
    int deleteByUserIdAndSourceId(@Param("userId") Long userId, @Param("sourceId") String sourceId);

    void deleteByUser_Id(Long userId);
}
