package com.hongmap.hongmapbackend.route.routing;

import com.hongmap.hongmapbackend.route.entity.RouteEdgeType;
import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 경로의 총 거리(distanceM)를 도보 소요시간(초)으로 환산한다.
// 엘리베이터 구간은 순수 이동시간 외에 대기시간이 추가로 발생하므로 통과 횟수만큼 가중치를 더한다.
@Component
public class RouteTimeEstimator {

    private static final BigDecimal WALK_SPEED_M_PER_S = BigDecimal.valueOf(1.2);
    private static final long ELEVATOR_WAIT_SECONDS = 30;

    public long estimateSeconds(BigDecimal totalDistanceM, List<RouteEdgeView> edges) {
        BigDecimal walkSeconds = totalDistanceM.divide(WALK_SPEED_M_PER_S, 0, RoundingMode.CEILING);
        long elevatorCount = edges.stream()
                .filter(edge -> RouteEdgeType.ELEVATOR.name().equals(edge.edgeType()))
                .count();

        return walkSeconds.longValue() + elevatorCount * ELEVATOR_WAIT_SECONDS;
    }
}
