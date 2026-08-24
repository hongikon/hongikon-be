package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * News.department_id / News.building_id를 채우기 위한 키워드 매칭.
 * 정교한 NLP가 아니라 단순 매칭이라 오탐/누락 가능성은 있다 — 지금은 "고려"한 수준의 1차 구현.
 */
@Component
@RequiredArgsConstructor
public class NewsLocationMatcher {

    private final DepartmentRepository departmentRepository;
    private final BuildingRepository buildingRepository;

    /**
     * board.sourceId는 프론트 TREE_DATA의 학과 id와 같게 맞춰져 있어(config.mjs 코멘트 참고)
     * department.name과 직접 매칭된다. 대학공지처럼 학과가 아닌 게시판(예: "학사", "장학")은
     * departments 테이블에 없는 라벨이라 매칭되지 않는다 — 정상이며 department는 null로 남는다.
     */
    public Department matchDepartment(BoardConfig board) {
        return departmentRepository.findByName(board.sourceId()).orElse(null);
    }

    /**
     * 제목+본문에 건물명이 그대로 등장하면 그 건물로 매칭한다.
     * 여러 건물명이 부분적으로 겹치면(예: "미술관"이 "제2미술관"에도 걸림) 이름이 더 긴(더 구체적인) 쪽을 우선한다.
     */
    public Building matchBuilding(String title, String content) {
        String haystack = (title == null ? "" : title) + " " + (content == null ? "" : content);

        return buildingRepository.findAll().stream()
                .filter(building -> haystack.contains(building.getName()))
                .max(Comparator.comparingInt(building -> building.getName().length()))
                .orElse(null);
    }
}
