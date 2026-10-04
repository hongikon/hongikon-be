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
  - Claude Code 세션에서 개편 작업 착수 (스펙 확정과 설계 논의는 이날 진행, 실제 코드 변경·커밋은 09-11로 넘어감 — 커밋 `af35645`, 아래 09-11 섹션 참고)
- EC2 배포 아키텍처 결정: EC2(Ubuntu 22.04, t3.micro) + Nginx(80/443 SSL) + Docker로 Spring Boot 실행 + RDS(MySQL) 분리 구조. 별도 세션에서 실제 세팅 진행 중
- **🎉 로그인 실기기 최종 검증 성공** — SDK 57 업그레이드 확인 → ngrok 재연결 → Expo 계정 로그인(CLI+앱 둘 다 필요했음) → 카카오 로그인 → 딥링크 복귀 → 로그인 상태 전환까지 실기기(iOS)에서 전 구간 확인. **Auth 도메인 완전히 종료.**

## 2026-09-11 — 제보(Report) 기능 확정 스펙 반영 커밋 완료

- 커밋 `af35645`: 09-08에 논의된 확정 스펙을 실제 코드에 반영. `Report`/`ReportController`/`ReportRepository`/`ReportService`/DTO 3종/`application.properties`/`db/alter_reports_table.sql` 변경 + 본 `docs/worklog.md` 최초 작성
  - `ReportCategory`, `ReportStatus` enum 신설 — `Report` 엔티티의 category/status 필드를 String에서 `@Enumerated(EnumType.STRING)` 기반 enum으로 전환. `status` 기본값이 `"ACTIVE"`에서 `ReportStatus.PENDING`으로 변경 (등록 즉시 공개 안 됨, 운영진 승인 대기)
  - `building_id`/`floor` 컬럼을 nullable에서 `NOT NULL`로 전환 — "건물+층이 정보의 핵심"이라는 확정 스펙에 맞춰 기존의 "건물 밖 제보(좌표만 존재)" 케이스 제거. `customCategoryLabel` 컬럼 신설 — category가 `ETC`일 때만 자유 텍스트 세분화 허용, `ReportService`에서 `ETC`가 아닌데 값이 채워지면 400 에러 처리
  - `ReportController`는 클래스 주석만 최신 기능 정의("로그인 유저가 건물+층에 시간 한정 이벤트 정보를 올리는 기능", "GET /reports는 게스트 허용·그 외는 로그인 필수")로 갱신 — 엔드포인트 시그니처 자체는 변경 없음
  - `ReportRepository.findLiveReports()`가 하드코딩된 `status = 'ACTIVE'` 조건 대신 `status` 파라미터를 받도록 변경, `updateStatus()`도 String → `ReportStatus` 파라미터로 전환
  - `ReportService`
    - 하드코딩 상수(`REPORT_HIDE_THRESHOLD=3`, `REPORT_MAX_DURATION=12시간`, `CATEGORIES` 리스트)를 제거하고 `@Value`로 주입받는 `report.flag.threshold`(기본 3), `report.endsAt.maxDays`(기본 7)로 설정값화 (`application.properties`에 추가) — endsAt 검증 로직도 "startsAt 기준 +12시간 이내"에서 "현재 시각 기준 +N일 이내"로 변경
    - `create()`에서 카테고리 문자열을 `ReportCategory.valueOf()`로 파싱(실패 시 400 처리)하고, `category != ETC`인데 `customCategoryLabel`이 채워지면 400 처리하는 검증 추가
    - `create()`에서 `buildingId`가 없어도 되던 분기를 제거 — 항상 `buildingRepository.findById()`로 조회하고 없으면 400 (building 필수화에 대응)
  - `ReportCreateRequest`: `buildingId`/`floor`에 `@NotNull` 추가, `customCategoryLabel`(`@Size(max=50)`) 필드 신설
  - `ReportResponse`/`ReportSummaryResponse`: `customCategoryLabel` 필드 추가, `category`/`status` 직렬화를 enum `.name()` 기반으로 변경
  - `db/alter_reports_table.sql` 신규 작성 — `ddl-auto=validate` 환경이라 엔티티 변경만으로는 실제 DB에 반영되지 않아, 배포 전 수동 실행 필요 (building_id/floor NOT NULL 전환, custom_category_label 컬럼 추가, status 기본값 PENDING, building_id FK 제약 추가)
  - 이 커밋에서 `docs/worklog.md`(본 문서) 최초 작성
  - ⚠️ 아직 미완료: Swagger/Postman을 통한 실기동 검증(등록 시 PENDING 상태 확인, 카테고리·customCategoryLabel·endsAt 상한 초과 400 케이스, 신고 누적 시 HIDDEN 전환 등)은 이 커밋 시점 기준 미실시

---

## 다음 할 일 (요약)

- [x] 제보 기능 확정 스펙 구현 완료 (커밋 `af35645`, 09-11)
- [ ] 제보 기능 실기동 검증 (Swagger/Postman) — `db/alter_reports_table.sql` DB 반영 포함
- [ ] 라우팅 노드/엣지 실데이터 입력 (픽토그램 기반)
- [ ] 크롤러 FK 매칭 버그 수정, 중복 게시글 처리
- [ ] 알림 발송 로직 (Expo Push) 구현
- [ ] 제휴업체 `benefit` 컬럼 추가
- [ ] 지도 데이터 하이브리드 전환 (프론트 별도 작업)
- [x] `POST /auth/test-token` 배포 전 제거 → 완전 삭제 대신 `@Profile("local")`로 격리 완료 (커밋 `5e6ae31`, 09-08)
- [ ] EC2 배포 (진행 중, 별도 세션)

## 2026-09-17 — EC2 배포 작업 재개 (VPC 생성 착수)

- 배포 세션 재개 (09-08 이후 별도 세션에서 대기 중이던 EC2 배포 작업)
- 09-08에 확정된 인프라 설계를 실제 AWS 콘솔 작업으로 옮기기 시작
  - Custom VPC(10.0.0.0/16) + Public 서브넷 1개 + Private 서브넷 2개(서로 다른 AZ, RDS 서브넷 그룹 요건) 구성 예정
  - hongikon-ec2-sg(22/80/443) / hongikon-rds-sg(3306, EC2 SG만 허용) 보안그룹 분리 예정
  - NAT Gateway 미사용 확정 (비용 절감)
  - 도메인은 Route 53에서 구매 예정 (Certbot SSL 발급에 필요, EC2/RDS 세팅 완료 후 진행)
- AWS 계정이 2025.7.15 이후 생성되어 크레딧 기반 Free Plan(최대 $200, 6개월) 적용 대상 확인, 현재 잔여 크레딧 약 70% — 졸프 예산으로 배포 비용 지원 가능해 소진 리스크 낮음
- 이 시점까지 AWS 콘솔에서 실제로 생성한 리소스는 없음 (설계만 확정, VPC 생성부터 시작 예정)

## 2026-09-17 (계속) — VPC/EC2/RDS 인프라 구축 완료 + 배포 전 SQL 순서 확정

### 인프라 구축 완료 (별도 세션)
- VPC(`hongikon-vpc`, 10.0.0.0/16) + Public 서브넷 2개(2a/2b) + Private 서브넷 2개(2a/2b) 생성 완료
- 보안그룹 2개 생성: `hongikon-ec2-sg`(22 내 IP, 80/443 전체), `hongikon-rds-sg`(3306, EC2 SG만 허용)
- EC2 인스턴스 생성 완료: `hongikon-be-server`(Ubuntu 24.04 LTS로 변경 — 22.04는 Quick Start 목록에서 순정 AMI를 찾지 못해 24.04로 대체 결정), t3.micro, Public Subnet
- Elastic IP 할당 및 연결 완료 (54.180.195.51로 고정)
- SSH 접속 확인, 2GB 스왑 설정 완료(`/swapfile`, `/etc/fstab` 등록), Docker 설치 완료
- RDS(MySQL 8.4, db.t4g.micro) 생성 완료: `hongikon-db`, 마스터 계정 `hongmap_app`, 초기 DB명 `hongmap`, Private Subnet Group, 퍼블릭 액세스 비활성화
- EC2 → RDS 연결 테스트 성공 (mysql-client로 접속, `SHOW DATABASES`에 `hongmap` 확인)

### 배포 전 SQL 적용 순서 확정 (레포 실제 파일 확인 완료)
- RDS는 현재 빈 스키마 상태 — `ddl-auto=validate`라 테이블이 먼저 있어야 앱이 부팅됨. 아래 순서로 RDS에 적용 필요:
  1. `hongikon-fe` 레포의 `docs/schema.sql` (기본 16개 테이블 생성, 아직 RDS엔 미적용)
  2. `hongikon-be`의 `db/seed_buildings_places.sql` (건물 27건 + 편의시설 72건)
  3. `hongikon-be`의 `db/alter_reports_table.sql` (제보 기능 확정 스펙 반영)
- `db/seed_departments.sql`은 GitHub에 미푸시 상태(로컬 untracked) 확인됨 — 학과 구독 기능은 `Department`/`UserDepartment` 테이블 자체가 schema.sql에 포함되어 있어 배포에는 지장 없음. 데이터가 비어있어도 `GET /departments`가 빈 배열을 반환할 뿐 에러는 아님 → 학과 구독 기능을 실제로 완성하는 시점에 이 파일 커밋+push+RDS 적용을 별도로 진행하기로 결정

### 확인된 사항
- `application.properties`가 이미 `${DB_URL:...}`, `${KAKAO_CLIENT_ID:...}`, `${JWT_SECRET:...}` 등 전부 환경변수 기반으로 구성되어 있음 확인 — 별도 `application-prod.properties` 작성 불필요, Docker 컨테이너 실행 시 환경변수만 주입하면 됨
- SecurityConfig에 CORS 설정이 없음을 확인 — 현재 프론트가 백엔드 API를 직접 호출하지 않는 구조(로컬 상수 기반 지도)라 당장은 문제없으나, 추후 하이브리드 전환 시 재점검 필요

### 다음 단계
- git clone (EC2) → schema.sql/seed SQL 적용 → Dockerfile 작성 → 이미지 빌드/컨테이너 실행 → Nginx → 도메인/SSL 순서로 진행 예정

## 2026-09-17 (계속) — Docker 배포 + Nginx 리버스 프록시 완료, 외부 접속 확인

### RDS 스키마 최종 정리 (엔티티 대조 검증 완료)
- buildings/places/route_nodes에 누락된 슬러그 컬럼(code, point_no) 보정 SQL 적용
- refresh_tokens/reports/report_flags 3개 테이블 CREATE 스크립트가 레포에 없었던 것 확인
  → 로컬 DB의 실제 테이블 구조(SHOW CREATE TABLE)를 엔티티와 전수 대조 검증 후
    db/create_refresh_tokens_table.sql, db/create_reports_table.sql,
    db/create_report_flags_table.sql로 신규 작성, 커밋
  → refresh_tokens.token_hash가 CHAR(64)로 잘못되어 있던 것 VARCHAR(64)로 수정
    (엔티티에 columnDefinition 미지정 시 Hibernate가 VARCHAR 기대)
- alter_reports_table.sql은 신규 DB에는 불필요, 구버전 마이그레이션 전용임을
  파일 상단 주석으로 명시
- RDS에 최종 19개 테이블 전부 생성 완료, buildings 27건/places 72건 데이터 확인

### Docker 배포
- EC2에 Java 17(openjdk-17-jdk) 설치
- `./gradlew bootJar`로 EC2에서 직접 JAR 빌드 (멀티스테이지 Docker 빌드 대신 —
  t3.micro 메모리 제약 고려한 선택)
- Dockerfile 신규 작성 (eclipse-temurin:17-jre-alpine 베이스, JAR만 복사하는 단일 스테이지)
- .env 파일로 운영 환경변수 관리 (DB_URL/DB_USERNAME/DB_PASSWORD, KAKAO_CLIENT_ID/SECRET,
  JWT_SECRET 신규 생성 — 로컬과 별도 키 사용, SPRING_PROFILES_ACTIVE=prod), .gitignore에 추가
- `docker run`으로 컨테이너 실행 (8080 포트, --restart unless-stopped)
- 부팅 성공 확인 (Started HongmapBackendApplication, ddl-auto=validate 통과)
- EC2 내부에서 `GET /status` 정상 응답 확인

### Nginx 리버스 프록시
- `apt install nginx`로 설치, /etc/nginx/sites-available/default를 80 → localhost:8080
  프록시 설정으로 교체
- **외부 브라우저에서 http://54.180.195.51/status 정상 접속 확인 — 배포 성공**

### 다음 단계
- 도메인 구매(Route 53) + Certbot SSL 적용
- 카카오 개발자 콘솔에 배포용(IP 또는 도메인) Redirect URI 추가 등록
- 프론트 EXPO_PUBLIC_API_BASE_URL을 배포 주소로 전환

## 2026-09-17 (계속) — Swagger 외부 접속 확인, 카카오 IP 기준 Redirect URI 등록

- http://54.180.195.51/swagger-ui.html 외부 브라우저 접속 정상 확인
- 카카오 개발자 콘솔에 배포 IP 기준 Redirect URI 추가 등록
  (http://54.180.195.51/login/oauth2/code/kakao) — 기존 ngrok 주소 유지한 채
  추가만 함, 현재 총 3개 URI 등록된 상태. 이건 도메인/SSL 적용 전 임시 조치이며,
  나중에 도메인 확정되면 별도로 도메인 기준 URI를 추가 등록해야 함 (IP 기준은
  지우지 않아도 무방)
- 프론트(최석훈)에게 임시 API 주소 전달 예정: EXPO_PUBLIC_API_BASE_URL=http://54.180.195.51
  (임시값이라는 점, iOS 실기기 테스트 시 app.json에 ATS 예외(HTTP 허용) 설정
  필요하다는 점 함께 전달 필요)
- 도메인 구매(Route 53)는 다음 세션에서 진행 예정 — 도메인은 프론트가 아니라
  백엔드 API 주소 전용이며, SSL 인증서 발급이 IP로는 불가능해 도메인이 필요한 것이
  구매 목적임을 확인

### 다음 단계
- Route 53에서 도메인 구매
- Certbot으로 SSL(HTTPS) 적용
- 카카오 개발자 콘솔에 도메인 기준 Redirect URI 추가 등록
- 프론트 EXPO_PUBLIC_API_BASE_URL을 최종 도메인 주소로 교체

## 2026-09-29 — HTTPS 배포 완료 (api.hongikon.com)

### 도메인 상태
- hongikon.com 구매 완료(가비아, 9/17), 최석훈 명의 가비아 계정에 등록되어 있음
- 소유권(등록자 명의) 이전은 진행하지 않기로 결정 — 최석훈 계정을 계속 공유받아
  DNS 관리 등 기술적 작업을 진행하는 방식으로 확정
- DNS 레코드는 이미 9/23에 설정되어 있었음을 확인 (프론트 세션에서 처리):
  - A @ → 75.2.60.5 (Netlify, 프론트 루트 도메인)
  - CNAME www → hongmap12.netlify.app (Netlify)
  - A api → 54.180.195.51 (백엔드 EC2, 미리 설정되어 있었음)

### 백엔드 HTTPS 적용
- 보안그룹(hongikon-ec2-sg)의 SSH 인바운드 소스 IP 갱신 (공인 IP 변경으로 접속 불가 상태였음 → "내 IP" 재선택으로 해결)
- Nginx server_name을 `_`에서 `api.hongikon.com`으로 변경
- Certbot(`certbot --nginx -d api.hongikon.com`)으로 Let's Encrypt SSL 발급 및 자동 적용
  - 인증서 만료일 2026-12-28, 자동 갱신 스케줄 등록됨
  - HTTP → HTTPS 자동 리다이렉트 적용
- https://api.hongikon.com/status 외부 접속 및 인증서 정상 확인

### 카카오 개발자 콘솔
- Redirect URI에 `https://api.hongikon.com/login/oauth2/code/kakao` 추가 등록
  (localhost, ngrok, IP 기준 기존 3개는 유지)

### 프론트 전달 사항
- 최종 API Base URL: `https://api.hongikon.com`
- iOS ATS 예외 설정(app.json의 NSAppTransportSecurity)은 이제 불필요, HTTP 임시 조치였으므로 제거 요청 필요
- 석훈(프론트) 쪽에서 요청했던 CORS(로컬 + 배포 도메인 허용) 설정은 아직 미착수 — 별도 확인 필요

### 다음 단계
- CORS 설정 검토 및 적용 (SecurityConfig에 미설정 상태 확인됨, 프론트 요청사항)
- t3.micro 메모리 사용량 68%, 스왑 24% 사용 중 — 재부팅 필요 상태(System restart required) 확인, 여유 있을 때 재부팅 권장

## 2026-09-30 — CORS 설정 + 제휴업체(Partner) 리팩토링 및 실데이터 시딩

### CORS 설정 (커밋 `7b6ea5b`)
- SecurityConfig에 `CorsConfigurationSource` 빈 추가, SecurityFilterChain에 `.cors(...)` 적용
- 허용 출처 7개:
  - 로컬: `http://localhost:8080`, `http://localhost:8081`(Expo 웹), `http://10.0.2.2:8080`(안드로이드 에뮬레이터)
  - 프론트 배포: `https://hongikon.com`, `https://www.hongikon.com`, `https://hongmap12.netlify.app`
  - 백엔드 자체: `https://api.hongikon.com`
- 메서드 GET/POST/PUT/PATCH/DELETE/OPTIONS, 헤더 전체 허용
- `allowCredentials=false` — 쿠키 기반 로직 없음 확인 (access 토큰은 Authorization 헤더, refresh 토큰은 JSON body로 주고받음)

### 지난 세션 작업분 재확인
- 학과(department) 43개 시드 관련 이슈 없음 확인
- 뉴스 위치정보(학과/건물) 백필 재확인

### Partner 엔티티 리팩토링 (커밋 `892f6cc`)
- `affiliations`를 `@ElementCollection Set<String>`에서 `PartnerAffiliation` 엔티티(`@OneToMany`, cascade ALL, orphanRemoval)로 전환
- 소속별 혜택 지원: `partner_affiliations.benefit` 컬럼 추가, NULL이면 `partners.benefit`(기본 혜택)으로 서버에서 fallback 처리
- API 응답 변경: `affiliations`가 문자열 배열 → `{affiliation, benefit}` 객체 배열 (프론트 수정 필요)
- `@EntityGraph`/fetch join으로 N+1 쿼리 해결 (목록/소속 필터/상세 모두 쿼리 1회)
- POST 요청에 소속 중복 시 400 반환
- 마이그레이션: `db/recreate_partner_affiliations_table.sql` (테이블 DROP 후 재생성)

### 제휴업체 실데이터 시딩 (커밋 `705ce03`)
- 프론트 `constants/partners.ts`(119개 하드코딩 데이터)를 변환 스크립트로 파싱해 `db/partners_seed.sql` 생성
- 로컬 DB 시딩 완료 (119 partners, 137 affiliations), GET /partners API 검증 완료

### 카카오 개발자 콘솔
- 앱 이름 "홍대로" → "홍익온"으로 수정 확인 (설정값만 변경, 코드 영향 없음)

### 다음 단계
- 운영 DB(RDS)에 `recreate_partner_affiliations_table.sql` → `partners_seed.sql` 순서로 반영 (배포 전 필수, 안 하면 `ddl-auto=validate` 실패)
- 프론트가 실제로 GET /partners를 호출하도록 연동 (현재는 하드코딩 그대로 사용 중) — 하이브리드 방식으로 전환 예정: API 우선, 실패 시 로컬 fallback
- 네이버 지도 secret 재발급 (보류 중, 최석훈 계정 소관)
- 최석훈님이 만든 `src/admin/` 관리자 대시보드 관련 백엔드 API 필요 여부 확인

### 오후 이어서 한 작업
- `feat/admin-console` 브랜치를 main에 병합 (관리자 대시보드, 제보 검토, 문의 기능) — 커밋 `d7bcf25`
- 로컬 검증: `/admin/overview`, `/admin/reports` 조회 및 승인(PATCH) 실제 동작 확인
- POST/DELETE `/partners`를 ADMIN 전용으로 제한 — 커밋 `f5fb22b`
- 존재하지 않는 id로 DELETE 시 500 대신 404 반환하도록 수정 (`ResponseStatusException` 방식) — 커밋 `767cd5f`
- 배포 런북(`docs/deploy-runbook-2026-10.md`)에 제휴업체 시드 SQL 순서 및 ADMIN 정책 반영 — 커밋 `6a204c4`
- 운영 서버 크롤러 정상 작동 확인 (https://api.hongikon.com/news 로 확인)
- 카카오 개발자 콘솔 앱 이름 "홍대로" → "홍익온" 변경

### 다음 단계 (오후 기준, 아직 안 한 것)
- 운영 DB(RDS)에 `recreate_partner_affiliations_table.sql`, `partners_seed.sql`, `alter_admin_console.sql` 반영 필요 (배포 전 필수)
- EC2 재배포 필요 (현재 9/17 빌드 그대로 운영 중)
- `feat/news-board-source`(PR #2) 병합 여부 미결정
- AWS 비용 확인 필요

### 저녁 이어서 한 작업 (운영 배포)
- 운영 RDS에 SQL 5개 반영 완료: `recreate_partner_affiliations_table.sql`, `partners_seed.sql`, `alter_admin_console.sql`, `alter_add_news_media_columns.sql`, `seed_departments.sql`
- EC2 재배포 완료: `git pull`(61커밋 반영, 9/17 빌드에서 갱신) → `./gradlew clean build` → `docker build` → 컨테이너 재기동, 정상 기동 확인(`Started HongmapBackendApplication`)
- 배포 후 확인: `/status` 최신 buildTime 확인, `/v3/api-docs` 401(비로그인 차단) 확인, `/admin/overview` 401(비로그인 차단) 확인, `/news` 정상 응답 확인
- 관리자 권한 부여: id=1(주세원), id=2(최석훈) → `role='ADMIN'`
- 운영 `/admin/overview` API 정상 응답 확인
- 뉴스 위치정보(학과) 백필 실행(`POST /admin/news/backfill-location`) — 대상 11,681건 중 11,286건 갱신, `missingDepartment` 11,681 → 395
- 웹판(hongikon.com) 카카오 로그인 정상 확인

### 오늘 새로 발견한 이슈
- 건축학부 게시판(`arch.hongik.ac.kr`) 크롤링 실패: `news.images` 컬럼(TEXT) 길이 초과로 `DataIntegrityViolationException` 발생, 스케줄 크롤러가 해당 게시판만 계속 저장 실패 중 — 원인 조사 및 수정 필요

### 다음 단계 (9/30 저녁 기준)
- Nginx 보안 설정 (`deploy/setup-https.sh`) 미적용 — IP 직접 접속 차단, 버전 노출 제거, 속도 제한
- 건축학부 크롤링 실패 버그 수정 (`images` 컬럼 길이 초과)
- `feat/news-board-source`(PR #2) 병합 여부 미결정
- AWS 비용 확인 필요
- 프론트 `.env` 임시로 `localhost:8080`으로 바꿔둔 것 원복 필요

- ## 2026-10-01

### 보안그룹 SSH 소스 갱신
- 어제와 다른 네트워크에서 접속 시도 시 타임아웃 — `hongikon-ec2-sg`의 SSH(22) 인바운드 규칙 소스를 현재 네트워크 IP로 갱신하여 해결

### Nginx 보안 설정 적용
- `deploy/setup-https.sh` 실행 완료 — 기존엔 `sites-enabled/default`를 고쳐 쓰고 있어 IP 직접 접속·Host 위조에 취약했던 상태를 해결
- 전용 `hongikon-api` 사이트로 교체: Host 불일치 시 444 차단, 속도 제한(20r/s, burst 40), 보안 헤더 추가
- 기존 Let's Encrypt 인증서 재사용하여 무중단 전환
- 추가로 컨테이너 포트 바인딩을 `0.0.0.0:8080` → `127.0.0.1:8080`으로 변경 (보안그룹이 막고 있었지만 이중 방어 차원)

### 건축학부 크롤링 버그 수정 (커밋 `426605d`)
- 원인: 건축학부 행사 게시판 글 하나(AAVS Korea 2026)의 이미지가 외부 URL이 아니라 `data:image/png;base64,...`로 본문에 직접 박혀있었음(약 97만자) — `news.images`(TEXT, 65535바이트 한도) 저장 시 매번 실패하며 해당 게시판 크롤링 전체가 막힘
- 조사: Jsoup으로 실제 페이지를 파싱해 각 글의 이미지 URL 총 길이를 확인하는 디버그 코드로 원인 확정
- 수정: `BoardParser`에 공통 `extractImageUrls()` 메서드 추가, `data:`로 시작하는 src를 필터링. `ArchBoardParser`/`HongikBoardParser`/`ImwebBoardParser` 세 파서가 모두 이 메서드를 쓰도록 통일 (중복 코드 제거 겸 재발 방지)
- 운영 재배포 후 수동 크롤링 트리거로 검증: 해당 게시글이 에러 없이 저장됨(images=NULL) 확인

### 테스트 설정 수정 (커밋 `870a38e`)
- `HongmapBackendApplicationTests`가 `@ActiveProfiles` 없이 기본 프로필로 돌아 로컬 Windows 계정으로 로컬 MySQL 접속을 시도 → 환경에 따라 실패
- `@ActiveProfiles("test")` 추가하여 `AdminApiIntegrationTest`처럼 H2 인메모리로 전환, 로컬 MySQL 상태와 무관하게 테스트 가능해짐

### PR #2(`feat/news-board-source`) 병합 + 배포
- main에 충돌 없이 병합 — 소식에 수집 게시판 출처(`source_id`) 저장/노출, 대학공지 학사·장학 등 구독 필터링 지원
- 운영 RDS에 `db/alter_add_news_source_id_column.sql` 적용 (컬럼 추가 + 기존 소식 백필, 약 1.3만 건)
- 재배포 후 `GET /news` 응답에 `sourceId` 정상 노출 확인

### 배포 런북(`docs/deploy-runbook-2026-10.md`) 0~6단계 전체 완료 확인
- 사전확인, RDS SQL 6개, 백엔드 재배포, Nginx 보안 설정, 배포 후 확인(버전 미노출·401·sourceId), 웹판 카카오 로그인, 관리자 지정까지 전부 검증 완료

### 오늘 새로 발견한 이슈
- 건축학부 게시판에서 같은 글(예: "AAVS 국제 학생 워크숍")이 크롤링마다 계속 중복 저장되고 있음 — 해당 게시판의 중복 감지 로직 점검 필요 (오늘 수정한 base64 버그와는 별개)

### 다음 단계
- `POST /auth/test-token` 운영에서 제거
- 건축학부 게시글 중복 저장 버그 조사·수정
- 프론트 `.env` 원복 확인 (`localhost:8080` → `https://api.hongikon.com`)
- 최석훈님에게 전달: 프론트 push 내역, `GET /partners` 연동 요청
- AWS 비용 확인 (프리티어 여부)
- `GET /news` 페이지네이션 (별도 설계 필요)

### 2026-10-01 (추가) - GET /news 페이지네이션

- 건축학부 기존 중복 뉴스 9,704건 정리 완료 (RDS, COMMIT 완료) - db/cleanup_arch_duplicate_news.sql
- 프론트엔드 .env의 EXPO_PUBLIC_API_BASE_URL을 localhost:8080 -> https://api.hongikon.com로 복원 확인
- AWS 비용: 프리티어 기간 내, 특이사항 없음
- GET /news 페이지네이션 + sourceId/keyword 필터 서버 이전 (커밋 5f3024a)
  - 응답 형태 변경: { news: [...] } -> { content: [...], page, size, totalElements, totalPages, hasNext }
  - 신규 쿼리 파라미터: page, size(최대 50), sourceId(다중값), keyword(제목 LIKE 검색)
  - 기존 파라미터 유지: category, departmentId, buildingId
  - 배포 완료 및 검증 완료 (buildTime 2026-10-01T04:24:10)
  - 프론트 수정 필요 (res.news -> res.content, 클라이언트 필터링 -> 서버 파라미터 방식) - 석훈에게 전달 완료
- 다음 단계: hongikon-fe 프론트 쪽 뉴스 API 연동 코드 수정 (석훈 작업 예정)

## 2026-10-01 (추가2) - Expo Push 알림 발송 로직

- 새 뉴스 크롤링 시 Expo Push 자동 발송 구현 (커밋 f5dd9e4)
  - 학과 게시판 글: 해당 학과 구독자에게 발송
  - 대학공지(학사/장학 등): 해당 카테고리를 끄지 않은 유저에게 발송 (꺼진 행이 없으면 수신 - 화면 표시와 일치)
  - 키워드 구독: 제목에 키워드 포함 시 위 기준과 무관하게 추가 발송
  - Expo API 100개 배치 발송, DeviceNotRegistered 기기 자동 비활성화
  - 3일 지난 소식은 발송 제외 (초기 크롤링/신규 게시판 추가 시 대량 발송 방지)
  - 테스트 14개 추가 (NewsPushDispatcherTest, ExpoPushClientTest)
  - 배포 완료 및 기동 확인 (buildTime 2026-10-01T05:23:28)
  - 미검증: 실제 기기로 수신 테스트는 아직 안 함 (기기 토큰 등록 후 /crawler/trigger로 확인 필요)
 
## 2026-10-01 (추가2) - 라이브 사이트 점검 및 프론트 작업 목록 정리

- 운영 웹(hongikon.com) 수동 테스트 결과 분석 (지도/소식/설정 탭)
- POST /feedback 정상 동작 확인 (운영 DB 직접 조회로 검증)
- 제보하기 등록 실패 원인 규명: ReportCreateRequest가 buildingId/floor 필수(@NotNull)인데
  프론트가 보내지 않음 — 백엔드 수정 사항 없음(의도된 검증), 프론트 수정 필요
- 제보 사진 업로드 미구현 확인: 백엔드에 업로드 API 자체가 없음 — 추후 백엔드 작업 필요
  (S3 presigned URL 또는 멀티파트 업로드 엔드포인트 신규 설계)
- 지도 탭 데이터 소스 확인: 편의시설/제휴업체는 프론트 하드코딩, 이벤트(제보)만 DB 연동
- 프론트 쪽 필요 작업 전체(buildingId/floor, Alert.alert 웹 미작동 5곳, 북마크 미연동,
  401 재발급 미구현 등)를 정리해 Notion으로 석훈에게 전달 완료
- 다음 단계: 제보 사진 업로드 API 설계/구현 (백엔드), 픽토그램 라우팅(PM 데이터 대기)

## 2026-10-02 — PR #9 제보 사진 최대 3장 (`feat/report-images`, base main)
- 왜: 제보 사진을 1장만 붙일 수 있어 현장 상황(전경·안내문·세부)을 함께 보여주기 어려웠음. #9가 아직 미머지라 후속 마이그레이션 대신 #9 스키마 자체를 바꿈
- 변경
  - `reports.image_key` 컬럼 → **`report_images` 테이블**(id, report_id FK ON DELETE CASCADE, image_key UNIQUE, sort_order, created_at). `Report.images` `@OneToMany`(cascade ALL·orphanRemoval, `@OrderBy sortOrder`, 목록 N+1 방지 `@BatchSize(100)`)
  - `POST /reports`: `imageKeys: string[]`(최대 3장, 중복 불가). 키마다 기존과 같은 검증(형식·HeadObject·5MB·형식·매직 바이트) + 서버 메타데이터 제거·새 키 저장. 장수·빈 값·중복·형식은 S3 호출 전에 한꺼번에 확인. 중간 1장이 실패하면 롤백(앞 장 정리본 삭제, 원래 키는 남아 재시도 가능)
  - 구버전 앱의 `imageKey`(1장)도 계속 받음. 둘 다 오면 `imageKeys` 우선(새 앱이 구버전 서버 대비로 함께 보내도 됨)
  - 응답(`POST/GET /reports`, `GET/PATCH /admin/reports`): `imageUrls`(presigned GET, 순서 유지, 없으면 `[]`) + `imageUrl`(첫 장, 하위 호환)
  - 삭제: 본인 삭제·관리자 반려/삭제·탈퇴 시 모든 장 S3 삭제
  - 업로드 URL 발급 한도 기본 20 → **30회/시간**(사진 1장마다 1회)
  - 같은 PR에서 main(#6 `ReportModeratedEvent`)을 병합해 `AdminReportService.java` 충돌 해소(이벤트 발행 + 사진 삭제 둘 다 유지)
- SQL: `db/alter_add_report_image_key.sql` 삭제 → **`db/create_report_images_table.sql`**(IF NOT EXISTS). 이전 ALTER를 이미 실행했다면 `reports.image_key`는 남겨 둬도 동작(정리 SQL은 파일 끝 주석)
- 환경변수: 변화 없음(`REPORT_IMAGE_UPLOAD_LIMIT_PER_HOUR` 기본값만 30)
- 테스트: +7 → 93개(main 병합 기준). #7·#10·#9·#11·#13·#14·#15 순서로 합친 상태 207개 통과
- 머지 충돌: main·#7·#10·#13·#15 없음. #11 `ReportResponse`·`ReportSummaryResponse` 각 1곳(#11의 `authorNickname(...getDisplayName())` + #9의 `imageUrl`·`imageUrls` 두 줄 유지). #14 `ReportService.java` 2곳(필드 둘 다, `create()`에서 `publishEvent(...)` 뒤 #9의 `return`). #12·#14·#15와 `docs/worklog.md`(파일 끝 덧붙임 → 양쪽 다 남기기)
- 프론트: `feat/report-multi-photo` — 앨범 다중 선택·카메라 1장씩, 썸네일·n/3, 장마다 메타데이터 제거·순차 업로드(재시도 시 올린 키 재사용), `imageKeys`+`imageKey` 전송, 응답에 `imageUrls`가 없으면(구서버) "1장만 첨부" 안내

## 2026-10-02 (밤) — 제보 댓글 (`feat/report-comments`, base main)
- 왜: 지도 제보에 "지금도 줄 있어요?" 같은 짧은 후속 정보를 남길 곳이 없었음. 사용자 생성 콘텐츠라 App Store 1.2(신고·차단·운영자 조치)와 약관 게시물 규정을 처음부터 맞춤
- 변경 (새 패키지 `comment/`에 거의 전부, 기존 파일은 3곳만)
  - API: `GET /reports/{id}/comments?page=&size=&order=`(게스트 가능, ACTIVE 제보만, 최상위 댓글 페이지 + 답글 앞 3개·`replyCount`, `commentCount`=답글 포함 수, 기본 오래된 순·`order=latest`), `GET .../{commentId}/replies`(답글 더 보기), `POST /reports/{id}/comments`(로그인, `parentId` 주면 답글 — 한 단계만, 답글에 답하면 같은 최상위 댓글로, 공백 제거 후 1~200자, 끝난 제보 409, 1분 5개·하루 50개 429), `DELETE /reports/{id}/comments/{commentId}`(본인, DELETED 로 — 공개 답글이 남은 최상위 댓글은 `placeholder: "DELETED"` 자리로 남음), `POST .../{commentId}/flags`(사유는 제보와 같음 + PRIVACY, 본인 400·중복 409, 3개면 자동 숨김)
  - 관리자: `GET /admin/reports/{id}/comments`(숨김·삭제 포함, 작성자 id·원래 닉네임·사유별 신고 수), `PATCH /admin/comments/{id}` `{status: VISIBLE|HIDDEN|DELETED}`. 복원 뒤에는 복원 이후 신고만 센다
  - 작성자 표시는 `authorDisplayName`(#11 규칙) + `authorKey`(#13 `AuthorKeys`와 같은 HMAC 값 — `CommentAuthorKeys`, #13 머지 뒤 교체 가능). users.id 는 공개 응답에 없음
  - 정지 회원: #13 `SuspendedUserInterceptor` 빈이 있으면 댓글 쓰기·신고 경로에 자동 등록(`ReportCommentWebConfig`). #13 과 합쳐 403 확인
  - 지도 목록 `GET /reports` 항목에 `commentCount`(IN + GROUP BY 1쿼리). `ReportSummaryResponse` 끝에 필드 + `@Builder(toBuilder = true)`
  - 푸시 `REPORT_COMMENT`: 제보 작성자 "내 제보에 댓글이 달렸어요"(제보별 10분 1번), 답글이면 부모 댓글 작성자 "내 댓글에 답글이 달렸어요"(부모 댓글별 10분 1번, `commentId` 포함). 본인 제외, 두 사람이 같으면 1번, "내 제보 결과 알림"(report_status_enabled) 설정 따름(메모리 묶음)
  - 삭제: FK ON DELETE CASCADE — 제보 삭제·탈퇴 시 DB 가 댓글·신고를 지움(`UserService` 수정 없음, 테스트로 확인)
- SQL: `db/create_report_comments_table.sql`(report_comments(`parent_id` 자기 참조 FK 포함), report_comment_flags, IF NOT EXISTS). 로컬 MySQL 26.7 에서 두 번 실행·`ddl-auto=validate` 기동·CASCADE(탈퇴→댓글→답글→신고) 확인
- 환경변수: 없음(선택 `report.comment.flag-threshold`=3, `report.comment.rate-per-minute`=5, `rate-per-day`=50, `push.report-comment-coalesce-minutes`=10 — 기본값이 코드에 있어 properties 미수정)
- 테스트: +27 → 197개. #13→#14→#15→#16→#17→#18 + 이 PR 을 합친 상태 280개 통과(가이드의 #14/#17 수정 + 아래 1곳, 정지 회원 403 통합 테스트 포함)
- 머지 충돌: 텍스트 충돌은 `docs/worklog.md`만. **의미 충돌 1곳**: #17 `ReportScheduleIntegrationTest` 의 쿼리 수 상한 `isLessThanOrEqualTo(3)` → 댓글 수 쿼리 때문에 `4`. #17·이 PR 중 나중에 머지하는 쪽에서 고친다
- 남은 일: #14 머지 뒤 댓글 자동 숨김도 관리자 알림(AdminAlertEvent), #13 머지 뒤 `CommentAuthorKeys` → `AuthorKeys.of`

## 2026-10-02 (밤) — 제보 커뮤니티: 🔥·HOT·관심 제보·작성자 알림·조회 수·댓글 👍 (`feat/report-community`, base #19)
- 왜: 지도 제보에 반응(공감)·구독·인기 목록이 없어 "지금 붐비는 곳"을 알기 어려웠음. 작성자가 제보별로 알림을 끌 방법도 없었음
- 변경 (새 패키지 `community/`, 기존 파일은 `ReportService` 1줄·`ReportSummaryResponse` 필드·`SecurityConfig` 1줄·#19 댓글 코드)
  - 🔥 `PUT/DELETE /reports/{id}/fire` — 남의 공개 제보에 한 사람 한 번(내 제보 400, 끝난 제보 409). 최근 60분 🔥 5개 이상이면 `hot`(`REPORT_HOT_THRESHOLD`, `REPORT_HOT_WINDOW_MINUTES`). 🔥 10·50·100 을 처음 넘으면 작성자에게 한 번씩 `REPORT_FIRE` 푸시(조건부 UPDATE 로 중복 없음)
  - `GET /reports` 항목: `fireCount`·`recentFireCount`·`hot`·`firedByMe`·`followedByMe`·`viewCount`·`notifyEnabled`(작성자만) — 네이티브 쿼리 1번. `GET /reports?sort=hot` 은 params 매핑(`ReportCommunityController`)으로 🔥 있는 제보만 최근 🔥 순 20개
  - 관심 `PUT/DELETE /reports/{id}/follow`(최대 100개): 시작·끝나기 30분 전 알림(`ReportFollowScheduler`, 매분 20초), 새 댓글 알림(사람마다 30분 묶음), 끝나거나 내려간 제보의 관심 자동 정리. `REPORT_FOLLOW`(kind START/ENDING/COMMENT)
  - 작성자 "이 제보 알림" `PUT /reports/{id}/notifications {enabled}` — 끄면 댓글·답글·🔥 이정표 알림 안 감. 모든 알림은 "내 제보 결과 알림"(report_status_enabled)도 따름
  - 조회 수 `POST /reports/{id}/views`(게스트 가능) — 계정 또는 `X-Install-Id` 로 하루(KST) 한 번. 원문 id 대신 날짜를 섞은 HMAC 만 2일 보관, IP 저장 안 함
  - 댓글 👍 `PUT/DELETE /reports/{id}/comments/{cid}/like`, 응답에 `likeCount`·`likedByMe`, `order=popular`
  - 🔥·관심·👍 합쳐 1분 20번(429, 메모리). 정지 회원 차단 경로(#13 인터셉터)에 누르기 PUT 추가
  - 누가 눌렀는지·봤는지 목록은 어떤 응답에도 없음(수와 "내가 눌렀는지"만)
- SQL: `db/create_report_community_tables.sql`(report_reactions, report_follows, report_engagement, report_view_marks, report_comment_likes — 모든 FK ON DELETE CASCADE, IF NOT EXISTS). 로컬 MySQL 26.7 임시 DB 에서 두 번 실행·`ddl-auto=validate` 기동·네이티브 쿼리(INSERT IGNORE, 통계) 확인
- 환경변수(모두 선택): `REPORT_HOT_THRESHOLD`(5), `REPORT_HOT_WINDOW_MINUTES`(60), `REPORT_REACTION_RATE_PER_MINUTE`(20), `REPORT_FOLLOW_MAX_PER_USER`(100), `PUSH_REPORT_FOLLOW_CRON`(`20 * * * * *`), `PUSH_REPORT_FOLLOW_ENDING_MINUTES`(30), `PUSH_REPORT_FOLLOW_COMMENT_COALESCE_MINUTES`(30), `REPORT_VIEW_KEY_SECRET`(비우면 JWT_SECRET)
- 테스트: +13 → 210개(`ReportCommunityIntegrationTest`). #13→#14→#15→#16→#17→#18→#19 + 이 PR 합친 상태 292개 통과(가이드 수정 + 아래 1곳)
- 머지 충돌: 텍스트는 `docs/worklog.md`만(#14 의 `build.gradle` `user.timezone=UTC` 는 같은 줄이라 자동 병합). **의미 충돌 1곳**: #17 `ReportScheduleIntegrationTest` 쿼리 수 상한 3 → **5**(#19 댓글 수 +1, 이 PR 통계 +1). 나중에 머지하는 쪽에서 고친다
- 남은 일: #13 머지 뒤 정지 회원의 🔥를 수에서 빼기(지금 브랜치엔 users.status 가 없음), 빈도 제한·알림 묶음을 서버 여러 대면 DB/Redis 로

## 2026-10-04 — 사용자에게 보이는 문구에서 이모지 빼기 (`feat/report-community`)
- 왜: 앱 문구에 🔥·👍 같은 이모지를 쓰지 않기로 함(공감·좋아요로 부름)
- 변경: 공감 이정표 푸시 제목 "내 제보에 🔥가 N개 모였어요" → "내 제보에 공감이 N개 모였어요", 400 메시지 "내 제보에는 🔥를…" → "공감을…", "내 댓글에는 👍를…" → "좋아요를…". Swagger 설명(개발자용)은 그대로
- 테스트: `ReportCommunityIntegrationTest` 기대 문구 수정, 커뮤니티·댓글 테스트 통과
