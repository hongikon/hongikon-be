package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.PlaceListResponse;
import com.hongmap.hongmapbackend.building.dto.PlaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;

    @Transactional(readOnly = true)
    public PlaceListResponse getFiltered(Long buildingId, String category) {
        var places = placeRepository.findFiltered(buildingId, category).stream()
                .map(PlaceResponse::of)
                .toList();
        return new PlaceListResponse(places);
    }

    @Transactional(readOnly = true)
    public PlaceResponse getById(Long id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 시설입니다."));
        return PlaceResponse.of(place);
    }
}
