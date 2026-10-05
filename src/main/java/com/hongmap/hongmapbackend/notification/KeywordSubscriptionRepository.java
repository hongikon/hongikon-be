package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, Long> {

    List<KeywordSubscription> findByUser_Id(Long userId);

    boolean existsByUser_IdAndKeyword(Long userId, String keyword);

    /** 대소문자 무시 중복 확인 — 운영 콜레이션(ci)과 같은 기준을 H2 에서도 쓰려고 명시한다. */
    boolean existsByUser_IdAndKeywordIgnoreCase(Long userId, String keyword);

    long countByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);
}
