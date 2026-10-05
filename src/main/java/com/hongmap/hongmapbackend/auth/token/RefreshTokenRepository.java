package com.hongmap.hongmapbackend.auth.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 현재 해시로 세션 row 를 찾으며 잠근다(SELECT ... FOR UPDATE). 같은 토큰으로 재발급이 동시에 두 번 오면
     * 두 번째 요청은 첫 번째가 커밋할 때까지 기다렸다가, 이미 바뀐 해시를 못 찾고 previous 쪽 조회로 넘어간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.previousTokenHash = :hash")
    List<RefreshToken> findByPreviousTokenHashForUpdate(@Param("hash") String tokenHash);

    /** 최근에 쓴 세션이 앞. updated_at 이 같으면(MySQL DATETIME 은 초 단위) 나중에 만든 row 가 앞. */
    List<RefreshToken> findByUser_IdOrderByUpdatedAtDescIdDesc(Long userId);

    long countByUser_Id(Long userId);

    /** 탈퇴: 이 유저의 모든 세션 삭제. */
    void deleteByUser_Id(Long userId);

    /** 만료된 세션 정리(정기 작업). 지운 행 수를 돌려준다. */
    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
