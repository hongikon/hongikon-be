package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationCategoryRepository
        extends JpaRepository<NotificationCategory, NotificationCategory.Pk> {

    List<NotificationCategory> findByUser_Id(Long userId);

    Optional<NotificationCategory> findByUser_IdAndCategory(Long userId, String category);
}
