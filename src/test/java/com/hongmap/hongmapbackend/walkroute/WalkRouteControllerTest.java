package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 위치 출발(GET /map/routes/approach) 파라미터. 스프링 전체를 띄우지 않고 컨트롤러만 본다. */
class WalkRouteControllerTest {

    private static final byte[] BODY = "{\"nodes\":[],\"starts\":[]}".getBytes();
    private WalkRouteService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WalkRouteService.class);
        when(service.approachJson(anyLong(), any(), anyBoolean())).thenReturn(BODY);
        mockMvc = MockMvcBuilders.standaloneSetup(new WalkRouteController(service)).build();
    }

    @Test
    void approach_avoidStairs_를_생략하면_false() throws Exception {
        mockMvc.perform(get("/map/routes/approach").param("to", "7"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BODY));
        verify(service).approachJson(7L, null, false);
    }

    @Test
    void approach_avoidStairs_true_를_넘긴다() throws Exception {
        mockMvc.perform(get("/map/routes/approach").param("to", "7").param("toFloor", "3").param("avoidStairs", "true"))
                .andExpect(status().isOk());
        verify(service).approachJson(7L, 3.0, true);
    }
}
