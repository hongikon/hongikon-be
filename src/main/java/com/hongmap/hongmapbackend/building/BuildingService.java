package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.BuildingListResponse;
import com.hongmap.hongmapbackend.building.dto.BuildingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;

    @Transactional(readOnly = true)
    public BuildingListResponse getAll() {
        var buildings = buildingRepository.findAll().stream()
                .map(BuildingResponse::of)
                .toList();
        return new BuildingListResponse(buildings);
    }

    @Transactional(readOnly = true)
    public BuildingResponse getById(Long id) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 건물입니다."));
        return BuildingResponse.of(building);
    }
}
