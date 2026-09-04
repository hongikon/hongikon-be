package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, Long> {

    List<KeywordSubscription> findByUser_Id(Long userId);

    boolean existsByUser_IdAndKeyword(Long userId, String keyword);

    void deleteByUser_Id(Long userId);
}
