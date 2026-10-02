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

## 2026-09-30 ~ 10-02 — 출시 준비 PR #4~#13 (미머지, 배포 대기)

> 아래 PR은 모두 OPEN 상태(10/2 기준). 운영은 아직 10/1 빌드. 머지·배포 순서와 충돌 해결법은 `docs/deploy-order-2026-10.md`(PR #12) 참고.
> 테스트 수는 각 브랜치의 `@Test` 메서드 수(main 31개 기준 증감) + PR 본문의 `./gradlew test` 결과. 가이드 순서대로 #4~#11을 합친 상태에서 163개, #13까지 합쳐 176개 통과 확인(1611b59 이전 기준).

### PR #4 — 소식 장학 분류 개선, `/error` permitAll, JVM UTC 고정 (`fix/news-category-and-error-401`)
- 왜
  - 장학 게시판의 "든든 학업지원금 공고문"이 제목의 '공고' 때문에 **취업**으로 분류됨 (분류기가 게시판을 안 보고 제목 키워드만 봄)
  - 처리 중 예외가 `/error`로 포워드되는데 여기가 `anyRequest().authenticated()`에 걸려 **400·500이 전부 본문 없는 401**로 내려감 (운영 재현: `GET /news/abc`, 잘못된 JSON `POST /feedback` → 401). 앱이 "로그인 만료"로 오안내 + 토큰 재발급 후 요청 재전송 → 500 난 POST 중복 처리 위험
  - 앱은 제보 시각을 UTC(`Z`)로 보내는데 JVM이 KST면 `LocalDateTime.now()`가 9시간 앞서 `endsAt @Future` 검증에서 모든 제보가 400 (로컬 맥에서 재현, 운영 컨테이너는 UTC라 현재는 정상)
- 변경
  - `NewsCategoryClassifier`: 장학 게시판(`source_id='장학'`) 글, 제목에 장학/등록금/학자금, 본문에 '장학' → 장학. 취업 키워드에서 '공고' 제거
  - `SecurityConfig`: `/error` permitAll
  - `HongmapBackendApplication`: 클래스 로딩 시점에 JVM 기본 시간대 UTC 고정 (커밋 `3a59cb2`)
- SQL: `db/update_news_category_2026_10_01.sql` (기존 행 CASE 재분류, 멱등). 실행 전후 `SELECT category, COUNT(*) FROM news GROUP BY category;`로 비교
- 환경변수: 없음
- 테스트: +7 (분류기 5, 오류 상태 코드 회귀 1, 앱 형식 제보 생성 회귀 1) → 38개
- 배포 메모: 배포 후 SQL 실행 (순서 무관). 401 위장 문제 때문에 가장 먼저 배포 권장
- 리스크: 본문에 '장학'이 들어간 비장학 글이 장학으로 갈 수 있음 (오분류 방향을 장학 쪽으로 기울인 선택)

### PR #5 — 게시판 구독 기반 푸시 대상 선정 (`feat/board-subscriptions`)
- 왜: 대학공지(학사·장학 등 6개)는 카테고리를 끄지 않은 **전원**에게 발송되고 있었고, 게시판 단위 on/off 수단이 없었음
- 변경
  - `user_board_subscriptions` 신설, API `GET /users/me/subscriptions`, `PUT/DELETE /users/me/subscriptions/{sourceId}` (로그인 필요, `CrawlerBoards.ALL`의 sourceId만 허용, 유저당 최대 100개)
  - 푸시 대상: (구독 + `alertEnabled` + 카테고리 안 끔) 또는 (제목 키워드 일치). `UserDeviceRepository.findPushTargets` 한 쿼리로 조회 (N+1 없음)
  - `/users/me/departments`는 소속 정보 용도로만 남고 푸시 대상에서 제외. 탈퇴 시 구독 삭제
- SQL: `db/create_user_board_subscriptions.sql` (테이블 생성 + 기존 `user_departments` → 학과 게시판 구독 백필, `INSERT IGNORE`로 멱등)
- 환경변수: 없음
- 테스트: +12 → 43개 (`BoardSubscriptionApiIntegrationTest` 10, `NewsPushDispatcherTest` 새 규칙으로 교체)
- 배포 메모: SQL → 서버 → 프론트(게시판 구독 화면) 순
- 리스크: **동작 변경** — 백필은 학과 구독만 옮기므로 배포 직후 대학공지 구독자 0명. 앱에서 구독을 다시 받기 전까지 대학공지 푸시는 키워드 일치분만 나감. 학과 글도 카테고리를 끈 유저에게는 더 이상 안 감

### PR #6 — 제보 승인·반려 알림, 캠퍼스 새 제보 알림 (`feat/report-alerts`, #5 위에 쌓은 브랜치)
- 왜: 작성자가 제보 승인·반려 여부를 알 방법이 없었음. "근처 제보 알림" 요구가 있었지만 앱은 GPS를 수집하지 않으므로 캠퍼스 단위 옵트인으로 대체
- 변경
  - 푸시 `REPORT_STATUS`(승인/반려 → 작성자, 기본 켜짐), `REPORT_NEW`(처음 ACTIVE 될 때 → 옵트인 유저, 작성자 제외, 기본 꺼짐)
  - 새 제보 알림은 유저당 30분 1회 (`PUSH_REPORT_NEW_THROTTLE_MINUTES`). 조건부 UPDATE 한 번으로 선점해 동시 승인에도 중복 발송 없음
  - `ReportModeratedEvent` → `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 비동기 발송, `@EnableAsync` 추가. 배치·DeviceNotRegistered 처리는 `ExpoPushSender`로 분리
  - API `GET/PATCH /users/me/notification-settings` (`newReportsScope`는 `CAMPUS`만)
  - 커밋 `2e0ba13`: Expo 거부 로그에 푸시 토큰 원문 대신 끝 4자만(`PushTokenMasker`), 오류 메시지 속 토큰도 가림. PATCH 요청에 `@Valid` + `newReportsScope @Size(max=20)`
- SQL: `db/create_user_notification_settings.sql`
- 환경변수: `PUSH_REPORT_NEW_THROTTLE_MINUTES` (선택, 기본 30)
- 테스트: #5 대비 +18 → 61개 (`ReportPushDispatcherTest` 8, `NotificationSettingApiIntegrationTest` 7+, `ExpoPushSenderLogTest`)
- 배포 메모: #5 다음에 머지. 프론트는 이 API가 404여도 로컬 설정으로 동작
- 리스크: Expo 발송이 실패해도 선점은 유지됨(30분간 재발송 안 함). 비동기 실패는 로그만 남음

### PR #7 — Sign in with Apple (`feat/apple-login`)
- 왜: iOS v1.0.0 출시에 필요(카카오 등 소셜 로그인 제공 시 Apple 로그인 필수), 계정 삭제 시 Apple 토큰 폐기(App Store 5.1.1(v))
- 변경
  - `POST /auth/apple` (permitAll): identity token을 JWKS(RS256)로 직접 검증, iss/aud/exp 확인. 응답은 카카오 교환과 같은 `TokenResponse`
  - **nonce 필수**: 토큰의 nonce == `sha256hex(raw)`만 허용 (원본 일치는 거부 — 탈취 토큰 재사용 방지, 보안 점검 M4). 누락 시 400
  - 이메일은 저장하지 않음 (앱도 EMAIL scope 미요청). 닉네임은 첫 동의 때 이름, 없으면 `Apple 사용자 XXXX`
  - Apple refresh 토큰을 **AES-256-GCM**으로 암호화 저장 (`AppleTokenCipher`, `v1:` 접두사)
  - 탈퇴 커밋 후 revoke 호출(best effort). 실패 시 `apple_pending_revocations`에 암호화해 넣고 매시 17분 재시도, 72회(약 3일) 실패하면 ERROR 로그 후 삭제
  - prod에서 Apple 키가 하나라도 없으면 기동 실패 (`AppleStartupCheck`). 일부러 끄려면 `APPLE_CLIENT_IDS=`(빈 값)
  - 커밋 `dfbf23d`/`cd4861a`: `APPLE_CLIENT_IDS` 기본값을 새 앱 ID `com.hongikon.app`, `.preview`, `.dev`로 변경 (기존 `com.hongmap.alimi*`)
- SQL: `db/alter_users_add_apple_columns.sql`, `db/create_apple_pending_revocations.sql`
- 환경변수: `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_TOKEN_ENC_KEY` (prod 필수), `APPLE_CLIENT_IDS`, `APPLE_REVOCATION_RETRY_CRON` (선택)
- 테스트: +34 → 65개 (`AppleIdentityTokenVerifierTest`, `AppleTokenCipherTest`, `AppleAuthClientTest`, `AppleLoginIntegrationTest`)
- 배포 메모: `tmp` 커밋(`4d5c312`)이 있으니 squash merge 권장. Apple 로그인은 서버 배포 + 새 iOS 네이티브 빌드가 함께 필요
- 리스크: `APPLE_TOKEN_ENC_KEY`를 바꾸면 기존 저장 토큰을 못 읽음(다음 로그인 때 갱신). 키 없이 가입한 Apple 회원은 탈퇴 시 폐기 불가

### PR #8 — 회원탈퇴 정리 (`fix/withdraw-cleanup`)
- 왜: `withdraw`가 `notification_categories`를 안 지워서, 분야 알림을 한 번이라도 바꾼 유저가 탈퇴하면 FK 위반 500 → **탈퇴 불가** (App Store 5.1.1(v)·Play 계정 삭제 정책 위반, launch-readiness R7)
- 변경
  - `NotificationCategoryRepository.deleteByUser_Id`를 users 삭제 전에 호출
  - `FeedbackRepository.detachUser`: 문의의 `user_id`를 NULL로 (스키마의 `ON DELETE SET NULL`에 기대지 않음)
  - 커밋 `31ab5f7`: 문의 `contact`(답변용 이메일)도 NULL로 비움 — 처리방침 "작성자와의 연결을 끊은 상태로 내용만 남음"과 일치 (보안 점검 H2)
- SQL: 없음
- 환경변수: 없음
- 테스트: +2 → 33개 (`UserWithdrawIntegrationTest`: 모든 연관 데이터를 만든 뒤 탈퇴 → 본인 데이터 정리, 문의는 user_id·contact NULL로 남음, 타인 데이터 유지)
- 배포 메모: **출시 차단 이슈라 #4 바로 다음**. 배포 후 `SHOW CREATE TABLE notification_categories;`로 FK 확인
- 리스크: #5·#6 머지 후 `UserWithdrawIntegrationTest`에 게시판 구독·알림 설정 행을 추가해 두는 것이 좋음 (아직 안 함)

### PR #9 — 제보 사진 S3 업로드 (`feat/report-images`)
- 왜: 제보 사진이 서버로 가지 않아 작성자 기기에서만 보였음 (launch-readiness R5)
- 변경
  - `POST /reports/images` → S3 presigned PUT URL 발급(5분 유효, jpeg/png, 1장, 5MB, 유저당 시간당 20회). 앱이 S3로 직접 업로드 후 `POST /reports`에 `imageKey` 첨부
  - 등록 시 서버 검증: 키 형식(`^reports/{uuid}\.(jpg|png)$`), HeadObject로 실제 업로드·크기·타입 확인, 키 재사용 금지, 매직 바이트 확인
  - **서버 측 메타데이터 재정리** (보안 점검 H4): 본문을 받아 `ImageMetadataSanitizer`로 EXIF·XMP·GPS 등 제거(재인코딩 없이 컨테이너만 재작성, 방향만 최소 EXIF로 보존)한 사본을 새 키로 저장하고 원본 키는 커밋 후 삭제
  - 응답의 `imageUrl`은 presigned GET(1시간). 비공개 버킷 유지
  - 삭제: 본인 삭제, 관리자 **반려·삭제** 시 S3 객체 삭제(숨김은 유지), **탈퇴** 시 커밋 후 삭제. 나머지는 수명 주기 30일로 정리
  - AWS SDK v2 `s3` 의존성 추가 (jar 약 10MB 증가)
- SQL: `db/alter_add_report_image_key.sql` (`reports.image_key VARCHAR(200) NULL`)
- 환경변수: `AWS_S3_BUCKET`, `AWS_REGION`(`ap-northeast-2`), 선택 `REPORT_IMAGE_MAX_BYTES`, `REPORT_IMAGE_UPLOAD_LIMIT_PER_HOUR`
- 테스트: +16 → 47개 (`ImageMetadataSanitizerTest`, `ReportImageIntegrationTest`, `ReportImageServiceTest`)
- 배포 메모: `AWS_S3_BUCKET`이 비면 사진 기능만 꺼짐(`POST /reports/images` 503). S3 버킷·CORS·수명 주기·IAM 역할·IMDS hop limit 2 설정은 배포 가이드의 "S3 설정" 절차대로. #10 다음 머지 시 `AdminReportService.java` 충돌 1곳
- 리스크: 응답마다 URL이 바뀌어 앱 이미지 캐시 효율 저하, 인스턴스 역할 자격 증명 만료가 먼저 오면 URL이 1시간보다 일찍 만료. 업로드 한도는 서버 메모리 기준(재시작 시 초기화). 처리방침의 "사진은 서버로 전송되지 않음" 문구 수정 필요(프론트)

### PR #10 — 보안·개인정보 점검 반영 (`fix/security-audit`)
- 왜: 2026-10 보안 점검에서 백엔드에서 바로 고칠 수 있는 항목 반영
- 변경
  - 카카오 로그인 1회용 code에 **PKCE(S256)** 적용 — 안드로이드에서 `hongikon://` 스킴을 가로챈 앱이 code를 교환하는 공격 차단. challenge 없는 구버전 앱은 기존대로 동작(하위 호환)
  - RequestCache를 꺼서 401 응답마다 JSESSIONID 세션이 쌓이던 문제 해결. 운영 세션 쿠키 `Secure`, `SameSite=Lax`, 10분 만료
  - `JWT_SECRET`이 비었거나 **32바이트 미만이면 기동 실패** (이전엔 기동 후 인증만 조용히 실패)
  - `/admin/**`, `/crawler/**`, 제휴업체 쓰기 요청을 `ADMIN_AUDIT` 로거로 기록(계정·IP·메서드·경로·상태) — 안전성 확보조치 기준 제8조 대응
  - nginx(`deploy/nginx/hongikon-api.conf`): `/actuator` 차단, IP 직접 HTTPS 거절 블록은 주석(nginx 1.19.4+ 필요). 쓰기 요청 IP 제한은 Netlify 프록시 때문에 제외
  - 공개 제보 작성자 익명화는 #11로 이관(커밋 `8d4adba`에서 revert)
- SQL: 없음
- 환경변수: 신규 없음, 기존 `JWT_SECRET` 길이 확인 필수
- 테스트: +11 → 42개 (`SecurityHardeningIntegrationTest`, `JwtTokenProviderSecretTest`, `PkceLoginCodeTest`)
- 배포 메모: 배포 후 nginx 설정 적용 `sudo nginx -t && sudo systemctl reload nginx`
- 리스크: `ADMIN_AUDIT`은 지금 컨테이너 stdout에만 남아 재배포 시 사라짐 → 1년 보관하려면 CloudWatch/파일 전송 인프라 작업 별도 필요. `AuthController` 교환 메서드에서 #7과 충돌 가능(`consume(request.code(), request.codeVerifier())` 유지)

### PR #11 — 앱 닉네임 + 공개 작성자 이름 가리기 (`feat/app-nickname`)
- 왜: 제보 작성자 이름으로 카카오 닉네임(대개 실명)이 그대로 공개되고 있었음
- 변경
  - `users.app_nickname`(선택, 유니크·대소문자 무시). API `GET /users/me`, `PUT/DELETE /users/me/nickname`
  - 검증: 2~12자, 한글·영문·숫자·`_`, 예약어(운영·관리자·admin·홍익온 등) 금지 → 400, 중복 409, 24시간 5회 초과 429(서버 메모리 기준)
  - 표시 이름: 앱 닉네임이 있으면 그대로, 없으면 첫 글자만 남기고 마스킹(`홍길동` → `홍**`, 빈 값 → `익명`). 서버에서 가리므로 공개 응답에 원문이 실리지 않음
  - 공개 제보 응답의 `authorNickname`에 표시 이름을 넣고 `authorDisplayName` 신규 필드 추가(구버전 앱도 바로 가린 이름을 봄). 관리자 응답은 원문 유지
- SQL: `db/alter_users_add_app_nickname.sql`
- 환경변수: 없음
- 테스트: +18 메서드 → PR 본문 기준 `./gradlew test` 63개 (`DisplayNamesTest`, `AppNicknamePolicyTest`, `AppNicknameIntegrationTest`)
- 배포 메모: 머지 시 `ReportResponse`·`ReportSummaryResponse` 충돌 2곳 — `.authorNickname(report.getUser().getDisplayName())`로, #9의 `.imageUrl(imageUrl)`은 유지
- 리스크: 처리방침에 "앱 닉네임(선택)" 수집 항목과 작성자 이름 공개 방식 추가 필요(프론트 쪽 정리)

### PR #12 — 배포 가이드 (`docs/deploy-order-2026-10`)
- `docs/deploy-order-2026-10.md` 신규: PR #4~#13 머지·배포 순서, PR별 선행 SQL, 손으로 풀어야 하는 충돌 2곳, 새 환경변수 표, Apple 키 준비(새 앱 ID `com.hongikon.app`), S3 설정 절차, 출시 전 운영·보안 점검 체크리스트
- 가이드 순서대로 #4~#11 8개를 합쳐 테스트 163개 통과 확인
- 이 worklog 섹션도 이 PR에 포함
- 10-02 밤 배포 가이드 갱신(`docs/deploy-order-2026-10.md`): #4–#11 머지됨, 남은 순서 #13 → #14 → #15 → #16 → #17, PR별 SQL·env(`AUTHOR_KEY_SECRET`·`KAKAO_ADMIN_KEY`는 #13 전에, `SCHEDULING_POOL_SIZE`), `AdminAlertDispatcherTest` 167행 수정, worklog 양쪽 유지, 배포 뒤 스모크 체크리스트. 5개 합친 상태 240개 통과·MySQL validate 기동 확인

### PR #13 — UGC 관리 (`feat/ugc-moderation`)
- 왜: App Store 가이드라인 1.2(사용자 생성 콘텐츠) 출시 차단 항목(launch-readiness R6), 탈퇴 시 카카오 연결 끊기(security-audit M7), 크롤러 UA(P7)
- 변경
  - 공개 제보 응답에 `authorKey`(유저 id의 HMAC-SHA256 앞 16자, base64url) 추가 — id 역추적 불가. 앱은 이 값으로 "이 사용자의 제보 숨기기"를 기기에 저장
  - 신고 사유에 `PRIVACY`(개인정보 노출) 추가 (`report_flags.reason`이 varchar(30)이라 DB 변경 없음)
  - `users.status`(ACTIVE/SUSPENDED) + `suspended_reason`, `suspended_at`. 정지 회원은 `/reports`, `/reports/*/flags`, `/reports/images`, `/feedback`, `/users/me/nickname` 쓰기 요청 시 403 (`SuspendedUserInterceptor`). 로그인·조회·본인 제보 삭제·탈퇴는 가능
  - 관리자 API: `GET /admin/users?q=`(숫자면 id, 아니면 닉네임 일부, 비면 정지 회원 목록), `GET /admin/users/{id}`, `POST /admin/users/{id}/suspend`(사유 필수, 관리자 정지 불가), `POST /admin/users/{id}/unsuspend`
  - **오늘(10/2) 추가, 커밋 `1611b59`**: `POST /admin/users/{id}/grant-admin`(정지 회원은 400), `POST /admin/users/{id}/revoke-admin`(자기 자신 해제 불가 → 관리자 0명 방지). 이미 그 상태면 그대로 반환(멱등)
  - 카카오 회원 탈퇴 시 커밋 후 `POST https://kapi.kakao.com/v1/user/unlink`(`KakaoAK {KAKAO_ADMIN_KEY}`) 호출, best effort, 회원번호는 로그에 안 남김
  - 크롤러 기본 UA `HongikOnBot/1.0 (+https://hongikon.com/support; hongikonsupport@gmail.com)`
- SQL: `db/alter_users_add_status.sql`
- 환경변수: `AUTHOR_KEY_SECRET`(권장, 비면 `JWT_SECRET`에서 파생), `KAKAO_ADMIN_KEY`(권장, 비면 연결 끊기 건너뜀), `CRAWLER_USER_AGENT`(운영 `.env`에 옛 값이 있으면 삭제)
- 테스트: +15 → 46개 (`UserModerationIntegrationTest` 9 — 관리자 지정·해제 2개 포함, `KakaoUnlinkClientTest` 3, `AuthorKeysTest` 3). 가이드 순서로 전부 합쳐 176개 통과(1611b59 이전 기준, 합친 상태 재확인 필요)
- 배포 메모: 가이드 표 9번째(#11 다음). #4~#11 각각과 `git merge-tree` 충돌 없음
- 리스크: `AUTHOR_KEY_SECRET`을 바꾸면(또는 비워 둔 채 `JWT_SECRET`을 바꾸면) 사용자 기기의 숨김 목록이 풀림. 관리자 지정·해제·정지는 일반 로그로만 남고, `ADMIN_AUDIT`은 #10 머지 후에야 함께 기록됨

### 배포 순서 (요약)
- 상세는 `docs/deploy-order-2026-10.md`. 운영 DB가 `ddl-auto=validate`라 **각 PR의 SQL을 배포 전에 RDS에서 먼저 실행**

| 순서 | PR | 먼저 실행할 SQL |
|---|---|---|
| 1 | #4 | `db/update_news_category_2026_10_01.sql` |
| 2 | #8 | 없음 |
| 3 | #5 | `db/create_user_board_subscriptions.sql` |
| 4 | #6 | `db/create_user_notification_settings.sql` |
| 5 | #7 | `db/alter_users_add_apple_columns.sql`, `db/create_apple_pending_revocations.sql` |
| 6 | #10 | 없음 (배포 후 nginx 적용) |
| 7 | #9 | `db/alter_add_report_image_key.sql` |
| 8 | #11 | `db/alter_users_add_app_nickname.sql` |
| 9 | #13 | `db/alter_users_add_status.sql` |

### 운영(사람) 할 일
- [ ] `JWT_SECRET`이 32바이트 이상인지 확인 (#10 이후 짧으면 기동 실패)
- [ ] `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_TOKEN_ENC_KEY`(`openssl rand -base64 32`, 이후 변경 금지) — Apple Developer에서 `com.hongikon.app`(+`.preview`) 등록, Sign in with Apple 키(.p8) 발급. 준비 전이면 `APPLE_CLIENT_IDS=`(빈 값)로 Apple 로그인만 끄고 배포
- [ ] `KAKAO_ADMIN_KEY` 운영 `.env`에 추가 (처리방침에 "탈퇴 시 카카오 연결 해제"를 적었으므로 필수)
- [ ] `AUTHOR_KEY_SECRET` 생성·보관 (`openssl rand -base64 32`, 한 번 정하면 변경 금지)
- [ ] 운영 `.env`의 옛 `CRAWLER_USER_AGENT` 삭제
- [ ] S3: 버킷 생성(퍼블릭 차단 4개, SSE-S3), CORS, 수명 주기 30일(`reports/`), IAM 역할(`reports/*` Put/Get/Delete + ListBucket), EC2에 역할 연결, **IMDSv2 hop limit 2**, `.env`에 `AWS_S3_BUCKET`/`AWS_REGION`
- [ ] `ADMIN_AUDIT` 로그를 CloudWatch 또는 마운트한 파일로 보내 1년 이상 보관, Docker 로그 크기 제한
- [ ] 첫 관리자: 이미 운영 DB에서 id=1, id=2를 SQL(`UPDATE users SET role='ADMIN' ...`)로 지정해 둠. 이후 추가·해제는 #13 배포 후 `grant-admin`/`revoke-admin` API로 (회원번호로 지정)
- [ ] #10 배포 후 nginx 설정 반영, #8 배포 후 `notification_categories` FK 확인
- [ ] 네이버 지도 Client Secret 재발급, RDS 암호화·백업 보존 기간 확인, 루트 MFA·CloudTrail

### 결정 사항
- 앱 ID를 출시 전에 `com.hongikon.app`(테스트 `.preview`, 개발 `.dev`)으로 변경 — Apple `aud` 기본값도 이 세 개
- 출시는 **한국 한정**
- **이메일은 수집하지 않음** (Apple 로그인도 EMAIL scope 미요청). 그래서 관리자 지정은 이메일이 아니라 **회원번호(users.id)** 기준으로 함 — `GET /admin/users?q=<id>`로 찾고 `grant-admin`
- 제보 사진은 비공개 버킷 + presigned GET (CloudFront는 트래픽이 커지면 검토)
- 근처 제보 알림은 GPS 없이 캠퍼스 단위 옵트인으로

### 다음 단계
- PR #4~#13을 가이드 순서대로 머지 → SQL → 배포 (#4·#8 먼저)
- 위 "운영(사람) 할 일" 처리 (특히 Apple 키, `KAKAO_ADMIN_KEY`, S3)
- #13(1611b59 포함)까지 합친 상태에서 전체 테스트 재실행
- #5·#6 머지 후 `UserWithdrawIntegrationTest`에 게시판 구독·알림 설정 행 추가
- 배포 후 실기기로 푸시(새 소식·제보 승인/반려·새 제보) 수신 확인
- 처리방침 문구 반영 (사진 전송, 앱 닉네임, 카카오 연결 해제) — 프론트와 함께
- 이전 목록에서 남은 것: `POST /auth/test-token` 운영 노출 여부 확인·제거, 건축학부 게시글 중복 저장 버그, AWS 비용 확인

## 2026-10-02 (오후) — PR 현황 갱신

**머지됨(main)**: #4, #5, #6, #8. **남은 순서**: #7 → #10 → #9 → #11 → #13 → #14 → #15 (`docs/deploy-order-2026-10.md`). 각 PR 의 자세한 기록은 그 PR 이 붙인 이 파일의 섹션에 있다 — #9·#12·#14·#15 가 모두 이 파일 끝에 덧붙여 머지 때 이 파일만 충돌하니 양쪽 다 남긴다.

- **#7 Apple 로그인**: 앱 ID 변경에 맞춰 `APPLE_CLIENT_IDS` 기본값 `com.hongikon.app,.preview,.dev`, 테스트 픽스처도 새 ID(65개 통과). Apple Developer 에 새 App ID·Sign in with Apple 키 생성 완료(Team ID `GB56N8GWDQ`) — Key ID·.p8 은 석훈 → 세원 직접 전달.
- **#9 제보 사진**: 최대 3장으로 변경. `reports.image_key` 대신 `report_images` 테이블(`db/create_report_images_table.sql`, 옛 ALTER 파일 삭제). `imageKeys[]`(최대 3) + 구버전 `imageKey` 호환, 응답 `imageUrls[]` + `imageUrl`. main 병합으로 `AdminReportService` 충돌 해소. 93개 통과, 가이드 순서 전체 병합 207개 통과.
- **#13 UGC 관리**: 관리자 지정·해제 API(`POST /admin/users/{id}/grant-admin`, `/revoke-admin`) 추가 — 자기 자신 해제 불가(관리자 0명 방지), 정지 회원 지정 불가.
- **#14 관리자 알림** (신규, main 기준): 새 PENDING 제보·문의·신고 자동 숨김 시 ADMIN 기기로 `[관리]` 푸시(Android `admin` 채널), 2분 묶음, 관리자 끄기 설정 `admin_alerts_enabled`(`db/alter_user_notification_settings_add_admin_alerts.sql`). 자동 숨김을 조건부 UPDATE 로 바꿔 동시 신고 시 알림 중복 방지. 82개 통과.
- **#15 회원 번호** (신규, base #13): `users.member_code` 영문 대문자·숫자 10자리, 가입 시 발급. SQL 하나로 기존 회원(#1·#2 포함) 백필·검증 SELECT·NOT NULL(`db/alter_users_add_member_code.sql`). **RDS 스냅샷 → SQL → 머지 → 배포**, SQL 직후 바로 배포. 관리자 검색이 회원 번호(대소문자 무시)도 찾음. 공개 제보 응답엔 없음. 56개 통과(#7·#11 포함 병합 122개).
- **확인**: `POST /auth/test-token` 은 `@Profile("local")` 이라 운영엔 없음(운영 401 확인).

**운영(사람) 추가 할 일**: 첫 관리자는 #15 배포 후 `UPDATE users SET role='ADMIN' WHERE member_code='<10자리>';`(그전엔 id 로), 이후엔 앱 관리 탭에서 지정. `KAKAO_ADMIN_KEY`(처리방침에 카카오 연결 끊기를 적음), `AUTHOR_KEY_SECRET`, `APPLE_*`·`APPLE_TOKEN_ENC_KEY`, S3 버킷(사진 3장) 준비.

## 2026-10-02 (저녁) — PR 현황

**머지됨(main)**: #4–#11. **남은 순서**: #13 → #14 → #15 → #16 → #17 (#12 문서).
- **#14**: 검토 리마인더 추가 — 30분·2시간 대기 제보를 관리자에게 묶어 알림, 00–08시(KST) 조용한 시간, 조건부 UPDATE 로 중복 방지. SQL `db/alter_reports_add_admin_reminder.sql` 추가(관리자 알림 SQL 과 함께 배포 전 실행).
- **#16 (신규) 내 제보 내역**: `GET /users/me/reports`, `/count`. 신고로 숨겨진(HIDDEN) 제보는 작성자 삭제 불가(409) — 검토 전 삭제로 제재 근거가 사라지는 것 방지. SQL·env 없음.
- **#17 (신규) 예정 제보**: 시작 최대 14일 뒤, 진행 최대 7일(`REPORT_MAX_DURATION_DAYS`), `include=upcoming`, 시작 전 승인 시 작성자에게 "…부터 지도에 보여요", 캠퍼스 새 제보 알림은 시작 시각에(`ReportStartPushScheduler`). SQL 없음. #14·#17 중 나중 머지 쪽에서 `AdminAlertDispatcherTest` 167행 고정 과거 시각 수정 필요. 앱 화면은 #17 배포 후 OTA.
- **결정**: 게스트 기기 기반 알림(서버 개편)은 하지 않음 — 알림은 계정 기준 유지, 앱은 로그인 후 알림 권한을 묻는다.

## 2026-10-02 (밤) — 버그 헌트 (#13~#17 통합 검증)

#13→#17 을 배포 순서대로 임시 통합해 테스트(최종 240개 통과), 새 SQL 을 로컬 MySQL 에 적용 후 `ddl-auto=validate` 로 기동 확인. 자세한 내용 `be-bughunt-1002.md`(작업 폴더). 각 PR 에 수정 커밋·한국어 코멘트·worklog 한 줄, PR 제목·본문을 10단 템플릿(요약·배경·변경·DB·env·충돌·테스트·배포·앱 상태·한계)으로 다시 씀.

- **#14 신고 자동 숨김이 운영에서 한 번도 안 됐음** (`6915750`): 기존 규칙이 "검토된 적 없는 ACTIVE" 만 숨겨, 승인을 거쳐 지도에 올라간 제보는 숨겨질 수 없었다(약관 제8조 4항과 어긋남). → 마지막 검토(`reviewedAt`) 이후 신고만 세어 임계값(기본 3) 이상이면 숨김 + 관리자 "자동 숨김" 알림 한 번. 재승인 전 신고는 세지 않음. 테스트 JVM `user.timezone=UTC`(테스트 DB 의 KST/UTC 9시간 차이 때문).
- **#14 스케줄러 스레드 1개** (`8dd91d2`): 매시 크롤링이 리마인더·Apple 폐기 재시도·#17 시작 알림을 막음 → 4개(`SCHEDULING_POOL_SIZE`).
- **#14 리마인더가 이미 끝난 대기 제보까지 셈** (`b1bc537`).
- **#16 숨긴 제보 삭제 잠금이 영원히 409** (`330bc28`): 검토 뒤에도 잠김 → 검토 이후 신고가 있을 때만 잠금, 삭제 시 신고 기록 명시적 삭제.
- **#17 지도 목록 작성자 N+1** (`ecf0d2b`): `JOIN FETCH` 로 6→3 쿼리.
- **#13 관리자 회원 검색이 앱 닉네임을 못 찾음** (`cb7d565`) + main 병합(`1d1dc1e`), #15 가 갱신된 #13 병합(`178c230`).
- **배포 가이드(#12) 다시 씀** (`aa3436c`, `3a05215`): 남은 순서 #13→#14→#15→#16→#17, PR별 SQL(확인 쿼리·롤백)·env(`AUTHOR_KEY_SECRET`·`KAKAO_ADMIN_KEY` 는 #13 전에, `SCHEDULING_POOL_SIZE` 선택), 손으로 풀 충돌(#14/#17 중 나중 쪽 `AdminAlertDispatcherTest` 167행), 배포 후 확인 목록.
- **남은 제안(미수정)**: 승인 전·본인 제보 신고 허용, 탈퇴가 숨긴 제보·신고를 지워 증거 잠금 우회, 카카오 연결 끊기 재시도 없음·동기 호출(탈퇴 최대 15초), 정지 사유가 앱 로그에 남음.
