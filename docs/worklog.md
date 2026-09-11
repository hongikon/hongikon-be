# 홍익온 백엔드 작업 일지 (worklog)

> 이 문서는 백엔드(`hongikon-be`) 작업 진행 상황을 날짜순으로 기록합니다.
> 상세한 논의/트래커는 Notion("홍익대 캠퍼스 앱 — 백엔드 프로젝트 지침서")도 함께 참고하세요.

---

## 2026-07-22

- 프로젝트 최초 세팅 (커밋 `0238bac`) — Spring Boot 프로젝트 스캐폴딩

## 2026-08-05

- `news.cs.json`이 8/5 14:04 스냅샷에서 멈춰있는 문제 발견 — 자동 갱신 로직(DB + `@Scheduled`) 필요성 확인
- 구독 키워드 알림/푸시는 서버 사이드 로직 필수 (정적 JSON import 구조로는 불가능) 확인
- 로그인 유저 데이터 영속화 필요성 확인 (`AsyncStorage`는 기기 단위라 계정 개념 없음)
- Kakao 로그인 방식 논의: JSON body 대신 **딥링크 + 1회용 코드 교환** 방식 채택 (WebView 환경에서 JSON 응답을 앱이 못 읽는 문제 회피)

## 2026-08-06

- 백엔드 레포 세팅 + 로컬 MySQL 연결 (`hongmap` DB, `hongmap_app` 계정)
- `schema.sql` 적용 → 테이블 16개 생성
- `User`, `UserDevice` JPA 엔티티 + Repository 작성, Kakao OAuth2 로그인 구현 (커밋 `1e3cb4a`)
- **Kakao OAuth2 + JWT 로그인 전체 파이프라인 1차 검증 완료** (Postman) — 로그인 → 유저 DB 저장 → 딥링크 → 1회용 코드 교환 → accessToken/refreshToken 발급
- 크롤러 아키텍처 이슈 발견: 프론트 크롤러가 DB를 거치지 않고 `news.cs.json` 정적 파일로 직행 → 백엔드 크롤러와 역할 재조정 필요
- 커밋 `97b152e`: `JwtProperties` 바인딩 오류 수정, Kakao scope 조정, `OAuth2SuccessHandler` 로깅 추가 (worklog 누락분 보강)

## 2026-08-10

- `hongikon-be` Public 전환 전 보안 점검 수행
  - 커밋 히스토리 전체에서 비밀값 패턴 검색 → 문제없음 (환경변수 참조만 존재)
  - `.env`/`application-local.properties` 커밋 이력 확인 → 없음
- GitHub 조직 `hongikon`으로 레포 이전 완료 (백엔드/프론트 둘 다)
- 커밋 `7a4713f`: `Building`/`Place`/`Report`/`ReportFlag`/`NotificationCategory`/`KeywordSubscription` 엔티티 스켈레톤 추가 (이 시점엔 엔티티만 존재, Repository/Service/Controller 없음) — worklog 누락분 보강
- 커밋 `fb80c58`: 실시간 제보(Report) 기능 Repository/Service/Controller **최초** 구현 + `SecurityConfig`에 `GET /reports` permitAll 추가 — worklog 누락분 보강
  - ⚠️ 정정: 기존에 09-08 섹션에 "Report 도메인 구현 작업 착수"라고 적혀 있던 것은 부정확함. 최초 구현은 이미 08-10에 있었고, 09-08 이후 작업은 확정 스펙(카테고리·상태 enum화, building/floor 필수화 등) 반영을 위한 **개편**이었음 (자세한 내용은 09-08 섹션 참고)
- README/조직 이전 관련 커밋 5건 (`5eacc46`, `2b145de`, `7c7c507`, `369ab93`, `2cf7901`, `4181e36`) — 위 "GitHub 조직 이전" 작업의 일환으로 README 신규 작성 및 도메인 구조/완료 항목 반영 (worklog 누락분 보강)

## 2026-08-11

- **Postman으로 로그인 → JWT 발급 → 인증 API 호출 전체 파이프라인 재검증 완료**
  - `accessToken`(30분) / `refreshToken`(14일) 발급 확인
  - `POST /partners`(인증 필요 엔드포인트) 실제 호출 성공 확인
- `hongikon/.github` 레포 + `profile/README.md` 설치 완료 (조직 페이지 소개글 표시)
- Partners 도메인: `category`/`affiliation` 값 String으로 우선 구현, 확정 요청 트래커에 등록 (커밋 `00b4877`)
- 커밋 `e1ae505`: News 도메인 구현(엔티티~Controller) + `SecurityConfig`에 `GET /news` permitAll 추가 — worklog 누락분 보강
- 커밋 `47c9a8e`: `RouteNode`/`RouteEdge` 엔티티 및 Repository 추가 — worklog 누락분 보강. 08-13 "라우팅 엔진 구현"의 선행 작업으로, 엔티티는 이날 만들어졌고 그래프 로더/Dijkstra 로직은 08-13에 구현됨

## 2026-08-13

- **RouteNode/RouteEdge 라우팅 엔진 구현 완료** (커밋 `96c1aea`)
  - 인메모리 그래프 로더(`RouteGraphLoader`) + Dijkstra 알고리즘
  - `POST /routes/search` 게스트 허용(permitAll)
  - Postman 더미 데이터(node id 1~4, edge id 1)로 실동작 검증 — 200 OK, 거리/경로 정상 반환

## 2026-08-14

> ⚠️ 아래 커밋 `0f2b43c`의 실제 커밋 일자는 08-15 (커밋 로그 기준). 작업 기록이 하루 전 날짜로 남아있던 것을 보강.

- 라우팅 엔진 추가 고도화 (커밋 `0f2b43c`)
  - `RouteNode`에 `point_no` 컬럼 추가 — 유저는 건물코드+층만 입력, 백엔드가 대표 접속점 자동 선택
  - `RouteEdgeType` enum 도입 (CORRIDOR/ELEVATOR/STAIRS/RAMP), `useElevator` 옵션 추가
  - 거리→시간 변환(`estimatedTimeSeconds`), 경로 간략화(`simplifiedSteps`) 응답 추가
  - Swagger UI(springdoc) 설치 — `/swagger-ui.html`
  - `POST /auth/test-token` 추가 (⚠️ 배포 전 제거 필요, TODO 주석 남김)

## 2026-08-20

- 커밋 `23c0349`: `./gradlew` 실행 권한 복원 (로컬 Swagger 확인 중 permission denied 발견 후 수정, 석훈님 작업) — worklog 누락분 보강

## 2026-08-24

- 커밋 `cc0f0b3`: 뉴스 크롤러 Java 포팅 (Jsoup 기반) — worklog 누락분 보강. 08-05/08-06에서 논의된 "프론트 크롤러가 DB를 거치지 않는" 아키텍처 이슈에 대한 실제 백엔드 구현
- API 인터페이스 변경 (석훈님 요청 반영, 커밋 `f7d599e`): 건물 식별을 numbering(id) → 고유 name(문자열)으로 변경
  - `RouteSearchRequest`가 `startBuildingName/startFloor`, `endBuildingName/endFloor` 형태로 변경
- 커밋 `1bd8a23`: Swagger UI 한글화 및 기능 단위 그룹 재편성 — worklog 누락분 보강
- 슬러그(code) 기반으로 추가 리팩토링 (석훈님 아이디어 반영)
  - `buildings`/`route_nodes`/`places`에 `code`(VARCHAR, UNIQUE) 컬럼 추가
  - 형식: `hongik_{건물}`, `hongik_{건물}_floor_{층}`, `hongik_{건물}_{시설}`
  - `RouteSearchRequest`가 `startBuildingCode`/`endBuildingCode` 2개 필드로 축소
  - 실기동 검증 완료 (커밋 `eba85f3`) — 200/404/400 케이스 모두 확인
- 커밋 `11d7ffc`: Swagger UI에 JWT Bearer 인증 스킴 등록 — worklog 누락분 보강

## 2026-08-26 — 캠퍼스 실데이터 시딩

- PM(관진님) 정리 엑셀(`홍익대학교_건물_정보_amenity_locations_완성.xlsx`) 기반으로 `buildings` 27건 + `places`(편의시설) 72건 실데이터 DB 시딩
- `Place.category` 신규 6종 추가: `CAFE`, `STUDY_ROOM`, `READING_ROOM`, `LOUNGE`, `NAP_ROOM`, `CERTIFICATE_KIOSK` (엑셀의 `SMOKING_BOOTH`는 `SMOKING_AREA`로 통일)
- 층 정보 없는 25건은 `floor=1` 기본값 + `extra_info`에 플래그 처리
- `building_code` 없는 4개 건물은 `hongik_gym`/`hongik_field`/`hongik_dorm2`/`hongik_dorm3`로 슬러그 임의 생성
- `BuildingResponse` DTO에 `code` 필드 누락 발견 → 추가 (커밋 `6377f99`)
- Swagger 실기동 검증 완료 — `buildings` 29건(실데이터 27+테스트더미 2), `places` 72건 정상 확인
- ⚠️ 남은 이슈: `floors` 마스터 데이터 미확보, `Building`의 여러 필드 미입력, 라우팅 노드/엣지 실데이터는 픽토그램 기반 별도 작업 필요
- ⚠️ 정정: 실데이터 시딩 SQL을 추가한 커밋(`b442f8c`, "캠퍼스 실데이터 시딩 SQL 추가")의 실제 커밋 일자는 09-03. 데이터 정리·검증 작업 자체는 이날(08-26) 진행됐고 커밋만 며칠 뒤로 늦어짐 — worklog 날짜 부정확분 보강

## 2026-09-03 — 로그인 작업 착수 + 노션 정리

- 노션 API 명세서 DB에 `도메인` 속성(이모지 포함) 추가, 43개 항목 전체 분류, 도메인별 탭(뷰) 10개 생성
- `/buildings`, `/places` API 명세서 최신화 (누락된 `code` 필드, 신규 category 6종 반영)
- 백엔드 Auth 구조 조사: OAuth2 로그인 골든패스는 완성도 높음. `/auth/reissue`, `/auth/logout`, 회원탈퇴 미구현 확인
- 프론트 Auth/routing 구조 조사: 프론트가 라우팅을 백엔드 API가 아니라 자체 Dijkstra+Yen's 알고리즘으로 독립 실행 중인 것 확인 (이원화 이슈)

## 2026-09-04 — 로그인 도메인 완성 + 지도 데이터 이슈 발견

### 로그인 완성
- 리다이렉트 스킴 `hongdaero://` → `hongikon://` 수정
- 프론트 `.env` 파일 부재 발견 → `EXPO_PUBLIC_API_BASE_URL` 생성
- `redirect-uri`를 고정 `localhost`에서 `{baseUrl}/login/oauth2/code/{registrationId}` 동적 템플릿으로 변경
- **`POST /auth/token/exchange` 실기동 검증 성공** (Swagger)
- ngrok으로 실기기 테스트 세팅 (`https://comma-king-pulverize.ngrok-free.dev`)
- 카카오 콘솔에 ngrok https 주소 등록 (KOE006 에러 해결)
- ⚠️ 버그 수정: `server.forward-headers-strategy=framework` 추가 (커밋 `bd1245d`) — ngrok 경유 시 https가 http로 잘못 조립되던 문제 해결
- `/auth/reissue`, `/auth/logout`, `DELETE /auth/me` 구현 완료 (커밋 `7688504`) — `refresh_tokens` 테이블 신설(SHA-256 해시), 로테이션/무효화/하드삭제 전부 Swagger로 검증
- ⚠️ 실기기 테스트 중 SDK 버전 불일치 발견 (프로젝트 SDK 56, 폰 Expo Go SDK 57) — 실기기 테스트 자체가 막힘

### 지도 데이터 이슈
- 프론트 `MapScreen.tsx`가 건물/편의시설/제휴업체를 백엔드 API가 아니라 로컬 상수(`src/constants/*.ts`)로만 그리는 것 확인 (의도된 오프라인 설계)
- 프론트-백엔드 데이터 대조: 건물 27개 완전 일치, 편의시설 4건 차이(같은 케이스, 처리방식만 다름) — 이원화 위험 낮음 확인
- 전환 방식으로 하이브리드(API + 로컬 캐싱) 추천, 프론트 쪽 별도 개발 필요 (규모 있는 작업으로 보류)
- 네이버 지도 API 키 `.env` 누락 발견 → 해결
- ⚠️ 네이버 지도 API secret 재발급 여부 미확인 (예전에 번들 노출 이력 있음)

## 2026-09-06

- 커밋 `5eb7276`: `GET /status` 공개 헬스체크 엔드포인트 추가 (`StatusController`, 석훈님 작업, Claude Code 세션 진행) — 백엔드 빌드 버전/시각을 노출해 프론트 "앱 상태" 화면에서 접속 중인 백엔드가 기대 빌드와 일치하는지 확인 가능. worklog 누락분 보강. 09-08 섹션의 "프론트/백엔드 레포 동기화"에서 로컬에 반영된 커밋이 바로 이것

## 2026-09-08 — 로그인 실기기 최종 검증 + 배포 준비 착수

- 프론트/백엔드 레포 동기화 (`git stash` → `pull` → `stash pop`으로 충돌 없이 병합)
  - 프론트: SDK 57 업그레이드, `/temp/status` 헬스체크 화면 등 4개 커밋 반영
  - 백엔드: `StatusController`(헬스체크 API) 1개 커밋 반영 (커밋 `5eb7276`, 실제 커밋일은 09-06 — 위 09-06 섹션 참고)
- 배포 전 보안 점검 (Claude Code 세션): `SecurityConfig`에서 `POST /auth/test-token`이 `permitAll`로 열려 있어 배포 전 제거 대상으로 지적됨 → 완전 삭제 대신 로컬 전용으로 격리 (커밋 `5e6ae31`)
  - `AuthController`에 있던 `issueTestToken()` 메서드와 관련 상수/의존성을 새 `AuthTestController`로 분리
  - `AuthTestController`에 `@Profile("local")` 부여 — local 프로필이 아니면 빈 자체가 등록되지 않아 `/auth/test-token` 엔드포인트가 아예 존재하지 않게 됨
  - `SecurityConfig`가 `Environment`를 주입받아 `local` 프로필일 때만 `/auth/test-token`을 permitAll 목록에 추가하도록 변경 — 컨트롤러 가드와 SecurityConfig 가드가 일관되게 동작
- 제보(Report) 기능 정체 확정: "신고"가 아니라 로그인 유저가 지도의 특정 지점(건물+층)에 시간 한정 이벤트 정보를 올리는 크라우드소싱 기능
  - 카테고리 5종 확정: `EVENT`/`PERFORMANCE`/`FOOD_TRUCK`/`BOOTH`/`ETC`
  - status 5종: `PENDING`/`ACTIVE`/`REJECTED`/`HIDDEN`/`DELETED` (등록 즉시 공개 안 됨, 운영진 사전 검토)
  - 게스트 조회 허용, `endsAt` 상한 설정값화(`report.endsAt.maxDays`), 신고 3회 누적 시 자동 `HIDDEN` 전환(`report.flag.threshold`) 확정
  - ⚠️ 정정: 이 작업은 "신규 착수"가 아니라 08-10에 이미 있던 최초 구현(커밋 `fb80c58`)에 대한 **확정 스펙 반영 개편**임 — Report 도메인 자체는 08-10부터 존재했음
  - Claude Code 세션에서 개편 작업 진행 (아래 전부 아직 미커밋 상태):
    - `ReportCategory`, `ReportStatus` enum 신설 — `Report` 엔티티의 category/status 필드를 String에서 `@Enumerated(EnumType.STRING)` 기반 enum으로 전환
    - `building_id`/`floor` 컬럼을 nullable에서 `NOT NULL`로 전환 — "건물+층이 정보의 핵심"이라는 확정 스펙에 맞춰 기존의 "건물 밖 제보(좌표만 존재)" 케이스 제거
    - `customCategoryLabel` 컬럼 추가 — category가 `ETC`일 때만 자유 텍스트 세분화 허용, `ReportService`에서 `ETC`가 아닌데 값이 채워지면 400 에러 처리
    - `ReportService`에 하드코딩돼 있던 신고 임계치(3건)와 endsAt 상한(12시간) 상수를 제거하고 `@Value`로 주입받는 `report.flag.threshold`(기본 3), `report.endsAt.maxDays`(기본 7)로 설정값화 (`application.properties`에 추가)
    - `ddl-auto=validate` 환경이라 엔티티 변경만으로는 반영되지 않아, 실제 DB에 수동 적용할 `db/alter_reports_table.sql` 마이그레이션 스크립트 신규 작성 (building_id/floor NOT NULL, custom_category_label 컬럼 추가, building_id FK 제약 추가)
- EC2 배포 아키텍처 결정: EC2(Ubuntu 22.04, t3.micro) + Nginx(80/443 SSL) + Docker로 Spring Boot 실행 + RDS(MySQL) 분리 구조. 별도 세션에서 실제 세팅 진행 중
- **🎉 로그인 실기기 최종 검증 성공** — SDK 57 업그레이드 확인 → ngrok 재연결 → Expo 계정 로그인(CLI+앱 둘 다 필요했음) → 카카오 로그인 → 딥링크 복귀 → 로그인 상태 전환까지 실기기(iOS)에서 전 구간 확인. **Auth 도메인 완전히 종료.**

---

## 다음 할 일 (요약)

- [ ] 제보 기능 구현 완료 및 검증
- [ ] 라우팅 노드/엣지 실데이터 입력 (픽토그램 기반)
- [ ] 크롤러 FK 매칭 버그 수정, 중복 게시글 처리
- [ ] 알림 발송 로직 (Expo Push) 구현
- [ ] 제휴업체 `benefit` 컬럼 추가
- [ ] 지도 데이터 하이브리드 전환 (프론트 별도 작업)
- [x] `POST /auth/test-token` 배포 전 제거 → 완전 삭제 대신 `@Profile("local")`로 격리 완료 (커밋 `5e6ae31`, 09-08)
- [ ] EC2 배포 (진행 중, 별도 세션)
