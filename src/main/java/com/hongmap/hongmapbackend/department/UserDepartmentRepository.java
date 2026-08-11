package com.hongmap.hongmapbackend.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserDepartmentRepository extends JpaRepository<UserDepartment, Long> {

    List<UserDepartment> findByUser_Id(Long userId);

    Optional<UserDepartment> findByUser_IdAndDepartment_Id(Long userId, Long departmentId);

    boolean existsByUser_IdAndDepartment_Id(Long userId, Long departmentId);

    @Modifying
    @Query("UPDATE UserDepartment ud SET ud.isPrimary = false WHERE ud.user.id = :userId")
    void clearPrimaryForUser(@Param("userId") Long userId);
}
