package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * department_id/building_id가 비어 있는 채로 저장된 기존 News를 NewsLocationMatcher로
 * 재매칭하는 1회성 백필. 크롤링 시점(NewsCrawlStorageService.save)과 같은 매칭 로직
 * (matchDepartment/matchBuilding)을 그대로 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsLocationBackfillService {

    private final NewsRepository newsRepository;
    private final NewsLocationMatcher locationMatcher;

    /** 이미 채워진 필드는 건드리지 않는다. 매칭 실패한 필드는 그대로 NULL로 남는다. */
    @Transactional
    public int backfill() {
        List<News> targets = newsRepository.findAllMissingLocation();
        int updatedCount = 0;

        for (News news : targets) {
            Department department = news.getDepartment() == null
                    ? locationMatcher.matchDepartmentBySourceUrl(news.getSourceUrl())
                    : null;
            Building building = news.getBuilding() == null
                    ? locationMatcher.matchBuilding(news.getTitle(), news.getContent())
                    : null;

            if (news.fillMissingLocation(department, building)) {
                updatedCount++;
            }
        }

        log.info("뉴스 위치정보 백필 완료: 대상 {}건 중 {}건 갱신", targets.size(), updatedCount);
        return updatedCount;
    }
}
