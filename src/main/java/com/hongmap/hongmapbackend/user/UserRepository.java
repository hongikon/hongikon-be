package com.hongmap.hongmapbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u.role FROM User u WHERE u.id = :id")
    Optional<UserRole> findRoleById(@Param("id") Long id);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsByAppNicknameIgnoreCaseAndIdNot(String appNickname, Long id);
}
