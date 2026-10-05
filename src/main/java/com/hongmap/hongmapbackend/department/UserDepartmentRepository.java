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

    /** 중복 행이 있어도 오류 없이 가장 먼저 만든 것 하나. */
    Optional<UserDepartment> findFirstByUser_IdAndDepartment_IdOrderByIdAsc(Long userId, Long departmentId);

    /** (user, department) 조합 전부 삭제. 지운 행 수. */
    @Modifying
    @Query("DELETE FROM UserDepartment ud WHERE ud.user.id = :userId AND ud.department.id = :departmentId")
    int deleteAllByUserIdAndDepartmentId(@Param("userId") Long userId, @Param("departmentId") Long departmentId);

    boolean existsByUser_IdAndDepartment_Id(Long userId, Long departmentId);

    @Modifying
    @Query("UPDATE UserDepartment ud SET ud.isPrimary = false WHERE ud.user.id = :userId")
    void clearPrimaryForUser(@Param("userId") Long userId);

    void deleteByUser_Id(Long userId);
}
