package com.hongmap.hongmapbackend.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppNoticeRepository extends JpaRepository<AppNotice, Long> {

    /** 앱 목록 — 게시 시각이 지난 공지 최근 50개, 최근 게시 순. */
    List<AppNotice> findTop50ByPublishAtLessThanEqualOrderByPublishAtDescIdDesc(LocalDateTime now);

    /** 관리자 목록 — 예약(게시 전) 포함 전부, 게시 시각 늦은 순. */
    List<AppNotice> findAllByOrderByPublishAtDescIdDesc();
}
