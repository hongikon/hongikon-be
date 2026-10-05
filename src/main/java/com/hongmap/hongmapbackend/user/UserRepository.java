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

    @Query("SELECT u.suspendedReason FROM User u WHERE u.id = :id")
    Optional<String> findSuspendedReasonById(@Param("id") Long id);

    /**
     * 관리자 회원 조회: 앱 닉네임(appNickname — 앱 사용자에게 보이는 이름) 일부로 찾기(최신 가입순 50명).
     * 로그인(카카오/Apple) 닉네임으로는 찾지 않는다 — 운영진이 실명으로 회원을 찾아볼 수 없게(개인정보 보호법 제3조 최소 처리).
     */
    List<User> findTop50ByAppNicknameContainingOrderByIdDesc(String appNickname);

    List<User> findTop200ByStatusOrderBySuspendedAtDesc(UserStatus status);

    /** 관리자 회원 조회: 회원 번호(K7Q2M9XA4D)로 찾기. 번호는 대문자로 저장한다. */
    Optional<User> findByMemberCode(String memberCode);

    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsBySocialTypeAndSocialId(SocialType socialType, String socialId);

    boolean existsByAppNicknameIgnoreCaseAndIdNot(String appNickname, Long id);

    /** 같은 공식 이름을 가진 다른 계정이 있는지(공식 이름은 계정마다 하나). */
    boolean existsByOfficialNameAndIdNot(String officialName, Long id);
}
