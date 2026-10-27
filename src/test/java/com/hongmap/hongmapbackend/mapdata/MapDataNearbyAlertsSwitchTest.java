package com.hongmap.hongmapbackend.mapdata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 근처 제휴 알림 긴급 스위치(hongikon.nearby-alerts.enabled=true)가 GET /map/data 최상위 nearbyAlertsEnabled 로 나간다. 꺼짐(기본)은 MapDataIntegrationTest. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "hongikon.nearby-alerts.enabled=true")
class MapDataNearbyAlertsSwitchTest {

    @Autowired MockMvc mockMvc;

    @Test
    void 스위치를_켜면_nearbyAlertsEnabled가_true() throws Exception {
        mockMvc.perform(get("/map/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearbyAlertsEnabled").value(true));
    }
}
