package com.hongmap.hongmapbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsBySocialTypeAndSocialId(SocialType socialType, String socialId);
}
