package com.hongmap.hongmapbackend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger UI 태그를 Controller 경계가 아니라 기능/화면 단위로 재편성하기 위한 태그 이름 상수 모음.
// 각 Controller 메서드에서 @Tag(name = SwaggerConfig.TAG_...)로 참조해 그룹을 지정한다.
@Configuration
public class SwaggerConfig {

    public static final String TAG_MAP_NAVIGATION = "🗺️ 지도/내비게이션";
    public static final String TAG_NEWS_NOTIFICATION = "📰 공지사항/알림";
    public static final String TAG_AUTH_MYPAGE = "👤 인증/내정보";
    public static final String TAG_PARTNER_ETC = "🏪 제휴업체/기타";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("홍익온 API 문서")
                        .description("홍익대학교 캠퍼스 지도 서비스 '홍익온'의 백엔드 API 명세")
                        .version("v1"))
                .tags(List.of(
                        new Tag().name(TAG_MAP_NAVIGATION).description("건물, 시설, 경로 탐색, 실시간 제보 관련 API"),
                        new Tag().name(TAG_NEWS_NOTIFICATION).description("학교 소식, 북마크, 키워드 구독, 알림 카테고리 관련 API"),
                        new Tag().name(TAG_AUTH_MYPAGE).description("로그인/인증, 기기 등록, 내 학과 정보 등 내 정보 관리 관련 API"),
                        new Tag().name(TAG_PARTNER_ETC).description("제휴업체 정보, 전체 학과 목록 등 기타 API")
                ));
    }
}
