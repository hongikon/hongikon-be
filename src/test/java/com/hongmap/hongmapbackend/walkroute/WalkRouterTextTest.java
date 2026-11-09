package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 길 안내 글(앱 routing.test.ts 에 있던 것을 옮김). */
class WalkRouterTextTest {

    @Test
    void 조사_메모를_길_안내_말로() {
        assertThat(WalkRouter.readableMemo("HI_T_3F_ENTER 앞 교차점")).isEqualTo("T동 3층 출입구 앞 교차점");
        assertThat(WalkRouter.readableMemo("HI_C_3_ELEVATOR\n3F~8F\n2대\n주말 사용 불가")).isEqualTo("C동 엘리베이터 · 3F~8F · 주말 사용 불가");
        assertThat(WalkRouter.readableMemo("상수역 -> 제4공학관 T동")).isNull();
        assertThat(WalkRouter.readableMemo("HI_R_2F_1_ENTER로 가는 계단 및 경사로")).isEqualTo("R동 2층 출입구로 가는 계단 및 경사로");
        assertThat(WalkRouter.readableMemo("주말 폐문")).isEqualTo("주말 폐문");
    }

    @Test
    void 이름표와_층_표기() {
        assertThat(WalkRouter.readableCode("HI_Z3_B1_ENTER")).isEqualTo("Z3동 지하 1층 출입구");
        assertThat(WalkRouter.readableCode("HI_R_1.5F_ENTER")).isEqualTo("R동 1.5층 출입구");
        assertThat(WalkRouter.floorText(-2)).isEqualTo("지하 2층");
        assertThat(WalkRouter.floorText(0)).isEqualTo("L층");
        assertThat(WalkRouter.floorText(1.5)).isEqualTo("1.5층");
        assertThat(WalkRouter.shortBuildingName("제4공학관 T동")).isEqualTo("T동");
        assertThat(WalkRouter.shortBuildingName("학생회관")).isEqualTo("학생회관");
    }
}
