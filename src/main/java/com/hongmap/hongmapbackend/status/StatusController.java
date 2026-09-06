package com.hongmap.hongmapbackend.status;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.status.dto.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 프론트(앱 상태 화면)가 EAS 빌드에 박힌 "필요 백엔드 버전"과 실제로 붙은
 * 백엔드 버전이 일치하는지 보여주기 위해 쓴다. BuildProperties는
 * build.gradle의 springBoot.buildInfo()가 생성하는 build-info.properties가
 * 클래스패스에 있어야 채워진다(로컬 gradlew bootRun도 포함).
 */
@RestController
@RequiredArgsConstructor
public class StatusController {

    private final Optional<BuildProperties> buildProperties;

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "백엔드 버전 조회", description = "현재 떠 있는 백엔드의 버전과 빌드 시각을 조회합니다. 인증 불필요.")
    @GetMapping("/status")
    public StatusResponse getStatus() {
        return buildProperties
                .map(props -> new StatusResponse(props.getVersion(), props.getTime().toString()))
                .orElseGet(() -> new StatusResponse("unknown", "unknown"));
    }
}
