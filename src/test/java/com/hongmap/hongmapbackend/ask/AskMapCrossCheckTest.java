package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** 지도 버튼 교차 확인(11-12 — '열람실 어디'의 버튼이 G동 행정·지원 부서를 열었다). */
class AskMapCrossCheckTest {

    private static final Map<String, String> KINDS = Map.of(
            "hi-g-2f-office-02", "학생처", "hi-g-5f-reading-01", "열람실", "hi-k-6f-reading-room", "열람실");
    private static final Function<String, Optional<String>> KIND_OF = id -> Optional.ofNullable(KINDS.get(id));

    private static AskLiveData.Reference facility(String id) {
        return new AskLiveData.Reference("facility:" + id, "설명", null, null);
    }

    @Test
    void kindQuestionWithoutPickedPlaceDropsGuideOfficeLocation() {
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("열람실 어디 있어"), null, "hi-g-2f-office-02", KIND_OF);
        assertThat(pick.place()).isNull();
        assertThat(pick.facilityId()).isNull();
    }

    @Test
    void kindQuestionDropsPickedFacilityOfAnotherKind() {
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("열람실 어디"),
                facility("hi-g-2f-office-02"), "hi-g-2f-office-02", KIND_OF);
        assertThat(pick.place()).isNull();
        assertThat(pick.facilityId()).isNull();
    }

    @Test
    void kindQuestionKeepsPickedFacilityOfAskedKind() {
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("K동 열람실"),
                facility("hi-k-6f-reading-room"), "hi-k-6f-reading-room", KIND_OF);
        assertThat(pick.facilityId()).isEqualTo("hi-k-6f-reading-room");
        assertThat(pick.place()).isNotNull();
    }

    @Test
    void studyQuestionAcceptsReadingRoom() {
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("공부할 곳"),
                facility("hi-g-5f-reading-01"), "hi-g-5f-reading-01", KIND_OF);
        assertThat(pick.facilityId()).isEqualTo("hi-g-5f-reading-01");
    }

    @Test
    void buildingTargetIsKept() {
        AskLiveData.Reference building = new AskLiveData.Reference("building:G", "학생회관 G동", null, null);
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("열람실 어디"), building, null, KIND_OF);
        assertThat(pick.place()).isEqualTo(building);
    }

    @Test
    void nonKindQuestionKeepsOfficeLocation() {
        AskRouteService.MapPick pick = AskRouteService.crossCheck(AskFacilityKinds.kinds("학생지원팀 어디야"), null, "hi-g-2f-office-02", KIND_OF);
        assertThat(pick.facilityId()).isEqualTo("hi-g-2f-office-02");
    }
}
