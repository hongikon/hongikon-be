package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.user.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WithdrawRetentionRepository extends JpaRepository<WithdrawRetention, Long> {

    /** 만료 여부와 관계없이 — (social_type, social_id_hash) 유니크라 탈퇴 때 있으면 그 행을 다시 쓴다. */
    Optional<WithdrawRetention> findBySocialTypeAndSocialIdHash(SocialType socialType, String socialIdHash);

    /** 관리자 회원 카드: 이 회원과 연결된(재가입) 아직 보관 중인 기록. */
    Optional<WithdrawRetention> findFirstByRejoinedUserIdAndRetainUntilAfter(Long rejoinedUserId, LocalDateTime now);

    /** 관리자 회원 목록용 일괄 조회. */
    List<WithdrawRetention> findByRejoinedUserIdInAndRetainUntilAfter(Collection<Long> rejoinedUserIds, LocalDateTime now);

    /** 만료 정리 — 한 번에 최대 200건. */
    List<WithdrawRetention> findTop200ByRetainUntilLessThanEqualOrderByIdAsc(LocalDateTime now);
}
