# 홍대로 (Hongdaero) - Backend

홍익대학교 캠퍼스 앱 "홍대로" 졸업 프로젝트의 백엔드 레포지토리입니다.

## 프로젝트 소개

홍익대학교 학생들을 위한 캠퍼스 통합 앱으로, 아래 두 가지 핵심 기능을 제공합니다.

- **지도/내비게이션**: 건물·시설 마커 표시, 실내 경로 안내, 자체 그래프 기반 라우팅 엔진(Dijkstra/A*)
- **공지사항**: 학과별 구독, 키워드 알림, 크롤링 기반 데이터 수집

지도 플랫폼은 **Naver Maps**를 사용하며, 프론트엔드(React Native + Expo)는 WebView + Naver 웹 JS API 방식으로 연동되어 있습니다.

## 팀 구성

| 이름 | 역할 | GitHub |
|---|---|---|
| 주세원 | 백엔드 리드 | [sewonzoo](https://github.com/sewonzoo) |
| 최석훈 | 프론트엔드 (React Native + Expo) | [Sskskxi](https://github.com/Sskskxi) |
| 이관진 | PM | [Jinius36](https://github.com/Jinius36) |

## 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 4.1.0 (Spring Framework 7, Jakarta EE 11, Jackson 3)
- **Build**: Gradle (Groovy)
- **DB**: MySQL
- **ORM**: JPA / Hibernate
- **Auth**: Spring Security, OAuth2 Client (Kakao), JWT (jjwt 0.12.6)
- **기타**: Lombok

> Spring Boot 3.4.1은 EOL로 Initializr에서 선택 불가하여 4.1.0으로 진행합니다. 김영한 강의(3.x 기준) 코드는 최신 문법으로 변환이 필요할 수 있습니다.

## 로컬 실행 방법

### 1. 사전 준비
- Java 17
- MySQL 설치 및 실행
- `hongmap` DB, `hongmap_app` 계정 생성

### 2. 환경 설정 분리

로컬 개발 환경변수는 `application-local.properties`에 작성하며, `.gitignore`에 포함되어 커밋되지 않습니다.

```powershell
# application-local.properties 예시 (직접 생성 필요)
spring.datasource.url=jdbc:mysql://localhost:3306/hongmap
spring.datasource.username=hongmap_app
spring.datasource.password=your_password

# Kakao OAuth
spring.security.oauth2.client.registration.kakao.client-id=your_client_id
spring.security.oauth2.client.registration.kakao.redirect-uri=your_redirect_uri
```

### 3. 실행

```powershell
./gradlew bootRun
```

## 카카오 OAuth 설정 주의사항

- 카카오 개발자 콘솔에서 **REST API 키** 발급 및 **Redirect URI** 등록 필요
- 동의항목은 `profile_nickname`만 활성화
- `account_email` scope 요청 시 **KOE205 에러** 발생하므로 요청 항목에서 제외할 것

## 인증 플로우

Kakao OAuth2 → Spring Security → JWT(access/refresh) 발급 → 1회용 코드 교환 → 딥링크(`hongdaero://`)로 앱 복귀하는 구조로 구현되어 있습니다.

- 지도·공지 열람은 비로그인 가능 (게스트 접근 허용)
- 구독·북마크·알림 기능은 로그인 필요

## 문서

- **API 명세서**: Notion 문서 (팀 내 공유)
- **ERD**: [ERDCloud](https://erdcloud.com/d/QrgQKkDtpdhXn9mdH)
- **메뉴트리(FigJam)**: 팀 내 공유

## 알려진 이슈 / 진행 중인 논의

- 크롤러 아키텍처 방향 미확정: 프론트 레포의 Node 기반 크롤러(정적 파일 저장) vs 백엔드 DB 저장 + API 서빙 방식 간 협의 필요
- `Building.category`와 `Place.category` 컬럼 네이밍 충돌 확인 필요

## Spring Boot 4.1.0 마이그레이션 관련 메모

- `JwtProperties` Bean 충돌 시 `@Component` 제거 + `@ConfigurationPropertiesScan` 추가로 해결
- 김영한 강의(3.x) 코드는 최신 문법으로 변환 필요한 부분이 있음
