package com.hongmap.hongmapbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u.role FROM User u WHERE u.id = :id")
    Optional<UserRole> findRoleById(@Param("id") Long id);

    @Query("SELECT u.status FROM User u WHERE u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") Long id);

    /** 관리자 회원 조회: 닉네임 일부로 찾기(최신 가입순 50명). */
    List<User> findTop50ByNicknameContainingOrderByIdDesc(String nickname);

    List<User> findTop200ByStatusOrderBySuspendedAtDesc(UserStatus status);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsByAppNicknameIgnoreCaseAndIdNot(String appNickname, Long id);
}
