package com.hongmap.hongmapbackend.route.routing;

import com.hongmap.hongmapbackend.route.dto.RouteSimplifiedStepResponse;
import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// 경로 상에서 연속으로 이어지는 같은 edge_type 구간을 하나의 스텝으로 합쳐 스텝 수를 줄인다.
// nodeIds[i] -> nodeIds[i+1] 구간이 edges[i]에 대응한다고 가정한다.
@Component
public class RoutePathSimplifier {

    public List<RouteSimplifiedStepResponse> simplify(List<Long> nodeIds, List<RouteEdgeView> edges) {
        List<RouteSimplifiedStepResponse> steps = new ArrayList<>();
        if (edges.isEmpty()) {
            return steps;
        }

        String currentType = edges.get(0).edgeType();
        BigDecimal currentDistance = edges.get(0).distanceM();
        int currentStepCount = 1;
        Long segmentFromNodeId = nodeIds.get(0);
        Long segmentToNodeId = nodeIds.get(1);

        for (int i = 1; i < edges.size(); i++) {
            RouteEdgeView edge = edges.get(i);
            if (edge.edgeType().equals(currentType)) {
                currentDistance = currentDistance.add(edge.distanceM());
                currentStepCount++;
                segmentToNodeId = nodeIds.get(i + 1);
                continue;
            }

            steps.add(new RouteSimplifiedStepResponse(
                    currentType, currentDistance, currentStepCount, segmentFromNodeId, segmentToNodeId));

            currentType = edge.edgeType();
            currentDistance = edge.distanceM();
            currentStepCount = 1;
            segmentFromNodeId = nodeIds.get(i);
            segmentToNodeId = nodeIds.get(i + 1);
        }

        steps.add(new RouteSimplifiedStepResponse(
                currentType, currentDistance, currentStepCount, segmentFromNodeId, segmentToNodeId));

        return steps;
    }
}
