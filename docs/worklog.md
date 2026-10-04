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

## 2026-10-02 — 공개 회원 번호 `K7Q2M9XA4D` (PR #15, `feat/member-code`, base `feat/ugc-moderation`)

- 왜: 설정 화면 "회원 번호"와 관리자 지정에 `users.id`(순번)를 보여 주면 가입자 수가 드러나고 남의 번호를 추측하기 쉬움. 내부 `users.id`는 그대로 PK·JWT sub
- 변경
  - `users.member_code` varchar(10) UNIQUE NOT NULL. 영문 대문자·숫자 10자리(접두사·하이픈 없음), 36^10 ≈ 3.6×10^15가지
  - 발급: `MemberCodeAssigner`(User 엔티티 리스너 `@PrePersist`)가 저장 직전에 채움 → 카카오·테스트 토큰·(#7 머지 후) Apple 가입 모두 코드 수정 없이 적용. `SecureRandom`으로 36자에서 고르게 10자, 이미 쓰인 번호면 최대 5번 다시 뽑음(중복 확인은 JdbcTemplate — 콜백 안에서 영속성 컨텍스트를 건드리지 않게). 확인~INSERT 사이 경합은 유니크 인덱스가 막음(그 가입 1건 실패, 재로그인 시 새 번호)
  - API `GET /users/me/member-code` → `{"memberCode":"K7Q2M9XA4D"}` (#11의 `GET /users/me`와 독립 — #11 없이도 머지 가능)
  - 관리자 `AdminUserResponse` 맨 끝에 `memberCode`. `GET /admin/users?q=`가 회원 번호도 찾음(정확히 일치, 대소문자 무시). 숫자면 id, 아니면 닉네임 일부도 함께 찾아 합침
  - 공개 제보 응답에는 싣지 않음(작성자 숨기기는 계속 `authorKey`)
  - 충돌을 줄이려고 `User.java`는 필드 1개(마지막 필드 뒤)·메서드 1개·리스너 어노테이션만, DTO는 맨 끝 필드만 추가
- SQL: `db/alter_users_add_member_code.sql` — 혼자 완결되고 처음부터 다시 실행해도 안전한 스크립트
  1. 컬럼(NULL 허용)·유니크 인덱스를 없을 때만 추가(information_schema + PREPARE)
  2. 기존 회원 전원(관리자 id 1·2 포함) `UPDATE IGNORE ... RANDOM_BYTES(8)`→36진수 끝 10자리로 채움, 3줄 = 충돌 재시도
  3. 확인 SELECT: `still_null`·`duplicated`·`bad_format` 모두 0, 관리자 번호 조회
  4. `MODIFY ... NOT NULL` (NULL이 남으면 실패해서 아무것도 안 바뀜)
  - 앱 쪽 백필 러너 없음. 로컬 MySQL로 20,000행 백필(중복 0)·전체 재실행·일부 NULL 상태에서 재실행 확인
- 배포 절차: **RDS 스냅샷 → SQL 전체 실행(확인 SELECT 0) → 머지 → 배포**. `ddl-auto=validate`라 SQL보다 앱을 먼저 배포하면 서버가 안 뜸. SQL~배포 사이에는 옛 서버로 "새 가입"만 실패(기존 회원 영향 없음) → SQL 직후 바로 배포
- 환경변수: 없음
- 테스트: +10 → 56개 (`MemberCodesTest` 5, `MemberCodeIntegrationTest` 5). #7+#11과 함께 합친 상태 122개 통과
- 머지 순서·충돌: 가이드 표 10번째(#13 다음). `git merge-tree` — #4·#5·#6·#7·#8·#9·#10·#11·#13·main 충돌 없음, #12·#14와는 `docs/worklog.md`만(모두 파일 끝에 덧붙인 것 → 양쪽 섹션 다 남기기). `docs/deploy-order-2026-10.md`는 #12에만 있는 파일이라 여기서 고치지 않음 → #12 표에 추가할 줄:
  ```
  | 10 | #15 | 공개 회원 번호(영문·숫자 10자리) | `db/alter_users_add_member_code.sql` | RDS 스냅샷 먼저. SQL 직후 바로 배포(그 사이 새 가입만 실패) |
  ```
- 운영 메모: 관리자 지정은 이제 회원 번호로 찾아서(`q=K7Q2M9XA4D`) 지정 — API 경로는 그대로 id
- 리스크: #11 머지 후 `GET /users/me`에도 `memberCode`를 넣을지는 후속(앱은 `/users/me/member-code`를 쓰고, 없으면 예전 `#id` 표시)
- 10-02 버그 점검 반영: 갱신된 #13(main 병합·앱 닉네임 검색) 병합. `AdminUserService.search` 충돌 — 회원 번호·id 검색은 유지하고 닉네임 검색을 로그인 닉네임·앱 닉네임 둘 다로. 응답에 `displayName`·`memberCode` 둘 다. 테스트 196개 통과
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

## 2026-10-02 — PR #14 관리자 알림 (`feat/admin-alerts`, base main)
- 왜: 새 제보 승인 대기·새 문의·신고 누적 자동 숨김이 생겨도 관리 탭을 열기 전엔 알 수 없었음
- 변경
  - `AdminAlertEvent`를 제보 등록(PENDING)·문의 등록·자동 숨김에서 발행 → `AdminAlertDispatcher`가 커밋 후 비동기로 ADMIN 유저의 활성 Expo 기기에 발송(`ExpoPushSender` 재사용, 토큰 마스킹 유지)
  - 본인(actor) 제외, `user_notification_settings.admin_alerts_enabled=false`인 관리자 제외(기본 켜짐)
  - 묶음: 종류마다 `push.admin-alert-window-seconds`(기본 120초)에 한 번, 첫 건 즉시·나머지는 "새 제보 N건 승인 대기"로. 서버 메모리 기준
  - 구분: 제목 `[관리]`, Android `channelId`/`categoryId` `admin`, data.type `ADMIN_REPORT_PENDING`·`ADMIN_FEEDBACK`·`ADMIN_REPORT_FLAGGED`(+ `reportId`/`feedbackId`, `count`)
  - 본문은 제보 제목·건물·층만. 문의는 "새 문의가 도착했어요"(내용·연락처 없음)
  - 자동 숨김을 조건부 UPDATE(`updateStatusIf` ACTIVE→HIDDEN)로 바꿔 동시 신고에도 한 번만
  - `GET/PATCH /users/me/notification-settings`에 `adminAlerts` 추가
- SQL: `db/alter_user_notification_settings_add_admin_alerts.sql` (`admin_alerts_enabled boolean NOT NULL DEFAULT TRUE`)
- 환경변수: `PUSH_ADMIN_ALERT_WINDOW_SECONDS` (선택, 기본 120)
- 테스트: +12 → 82개 (`AdminAlertThrottleTest` 5, `AdminAlertDispatcherTest` 7)
- 배포 메모: #6 이후 아무 때나, 가이드 표 기준 10번째(#13 다음) 권장. #9와 `ReportService.java` 충돌 2곳(필드·`create()` 끝, 둘 다 유지)
- 리스크: 재시작하면 묶음 상태 초기화, 서버 여러 대면 인스턴스별로 셈. 발송 실패 재시도 없음
- 프론트: `feat/admin-alerts` — "관리자 알림" Android 채널, ADMIN_* 알림 → 관리 탭 해당 섹션, 관리자 전용 토글, 앱이 열려 있어도 표시
- 추가(같은 PR) — 승인 대기 제보 리마인드
  - 왜: 새 제보 알림을 놓치거나 미뤄 두면 PENDING 제보가 몇 시간씩 방치됨
  - `AdminReportReminder`(10분마다, `PUSH_ADMIN_REMINDER_CRON`): PENDING 30분(`PUSH_ADMIN_REMINDER_AFTER_MINUTES`)·2시간(`PUSH_ADMIN_REMINDER_REPEAT_AFTER_MINUTES`) 넘은 제보를 회차당 한 번 묶어 "[관리] 검토 대기 중인 제보가 N건 있어요" / "가장 오래된 것 M분 전", data `{type: ADMIN_REPORT_REMINDER, count, oldestReportId}`, 채널 `admin`. N은 30분 넘게 대기 중인 PENDING 수
  - 제보당 최대 2번: `reports.admin_reminder_count`/`admin_reminded_at`을 조건부 UPDATE로 선점(행 수 0이면 안 보냄) → 서버 여러 대·재시작에도 중복 없음. 2번째는 1번째에서 90분 이상 지나야(08:00 요약 직후 연달아 오지 않게)
  - 방해 금지 KST 00–08시(`PUSH_ADMIN_REMINDER_QUIET_START_HOUR`/`_END_HOUR`) — 선점·발송 안 함, 08:00 회차에 한 번 요약. 받을 관리자 기기 없으면(모두 끔 포함) 선점 안 함
  - 시각은 주입 Clock(UTC) — 테스트는 고정 Clock
  - SQL: `db/alter_reports_add_admin_reminder.sql` (`admin_reminder_count TINYINT NOT NULL DEFAULT 0`, `admin_reminded_at DATETIME NULL`) — ddl-auto=validate라 배포 전 실행
  - 테스트: `AdminReportReminderTest` 9개, 전체 191개 통과(main 병합 기준)
  - main 병합(#9 등 22커밋): `ReportService.java` 필드·`create()` 끝(둘 다 유지), worklog 정리
  - 충돌(`git merge-tree`): #13 없음. #17 `application-test.properties` 끝 한 줄씩(둘 다 유지). #12·#15·#16·#17 `docs/worklog.md`(끝 덧붙임 → 둘 다 남기기)
  - 프론트: `feat/admin-reminder-route` — ADMIN_REPORT_REMINDER를 ADMIN_REPORT_PENDING처럼 라우팅(관리 탭 → 제보 검토 → 승인 대기, oldestReportId 강조)
- 10-02 버그 점검 반영: 리마인드에서 이미 끝난(ends_at 지남) PENDING 제보 제외(개수·가장 오래된 것·선점), `@Scheduled` 스레드 1→4(`SCHEDULING_POOL_SIZE`, 정각 크롤링이 리마인드·Apple 재시도를 막던 문제). 테스트 193개 통과
- 10-02 자동 숨김 규칙 변경(결정 반영): 승인된 제보도 **마지막 검토(reviewedAt) 뒤 신고 수 ≥ 임계치(3)**면 자동 숨김 + 관리자 알림. 검토 전이면 전부 셈, 다시 공개하면 그 전 신고는 안 셈(이용약관 제8조 4항). 전엔 `reviewedAt == null` 조건이라 승인된 제보는 절대 안 숨겨졌음. 테스트 JVM `user.timezone=UTC`(H2 시각 9시간 어긋남 방지). 테스트 194개 통과

## 2026-10-02 — PR #13 UGC 관리 (`feat/ugc-moderation`, base main)
- 10-02 버그 점검 반영: main 병합(#11 앱 닉네임 필요). 관리자 회원 조회(`GET /admin/users?q=`)가 로그인 닉네임만 찾아 앱에 보이는 이름(앱 닉네임)으로는 못 찾던 문제 — 둘 다 찾고 응답에 `displayName` 추가(테스트 추가). 테스트 186개 통과

## 2026-10-05 — 크롤링 최적화 (`perf/crawler-optimize`, base main)
- 왜: 매시간 크롤링이 새 글이 없어도 게시판 47개 × 목록 2페이지 = **94회 요청**, 글마다 `exists` 쿼리(평시 ~4,500회) + 페이지마다 `UPDATE` 94회. 죽은 게시판은 매시간 재시도 3회+타임아웃(최대 ~60초). 같은 서버에 게시판 사이 간격 없이 연달아 요청
- 확인(크롤러 UA로 목록 4건만 요청): 학교 게시판·건축·Imweb 모두 `ETag`/`Last-Modified` 없음(`no-store`) → 조건부 GET 불가, 안 함. 학과 `.do` 게시판은 서브도메인이 달라도 **전부 같은 IP**(203.249.66.153) → 호스트명 기준 병렬화는 학교 서버 한 대를 동시에 때림. 목록 1페이지 응답 0.12~0.39초
- 변경
  - 증분 수집: 페이지의 **가장 오래된(마지막) 글**이 이미 저장돼 있으면 다음 페이지를 안 받음. "아는 글이 하나라도 있으면 멈춤"은 상단 고정 공지 때문에 쓰면 안 됨. 실패한 게시판은 다음 성공 때까지 끝까지 훑음(1페이지 저장 뒤 2페이지 실패 → 2페이지 누락 방지). `maxItems` 도달 시에도 다음 페이지 안 받음
  - 저장된 글 판단을 페이지당 `SELECT sourceUrl, sourceId ... WHERE source_url IN (...)` 1회로. `source_id` 채우기 UPDATE는 빈 행이 있을 때만. 건축학부(링크가 매번 바뀜)는 콜레이션 차이로 중복 저장되지 않게 기존 글별 판단 유지. 저장된 글 상세 미요청·수정글 미갱신은 기존 그대로
  - 서버(IP)별 묶음끼리만 병렬(기본 2), 같은 서버는 한 스레드가 순차 + **게시판 사이에도 `request-delay-ms`**. 실제로 겹치는 건 건축·도시공학과뿐(학과 .do는 한 서버라 순차)
  - `CrawlerBoardCircuitBreaker`: 게시판(목록 URL)이 3회 연속 실패하면 6시간 건너뜀 → 지나면 1회 재시도(성공 시 정상화). 서버 메모리 기준
  - 실행 요약 `CrawlResult` + 로그 한 줄(`크롤링 요약: 게시판 N개(실패·건너뜀), 요청 N회, 신규 N건, Nms`). `GET /admin/overview` `crawler`에 `lastRequestCount`·`lastDurationMs`·`lastFailedBoards`·`lastSkippedBoards` 추가(필드 추가만, 기존 필드 그대로)
  - 새 소식 푸시는 게시판 설정 순서대로 모아 한 번(기존과 같음). 분류·위치 매칭·푸시 로직 변경 없음
- 추정(평시 = 새 글 0건, 게시판 47개): 요청 94 → **47회**(−50%), DB 쿼리 ~4,600 → **47회**, 시간 ~45~50초 → **~30초**(목록 0.25초×47 + 같은 서버 간격 0.4초×43). 새 글 1건당 상세 1회는 그대로. 죽은 게시판: 매시간 4회·최대 ~60초 → 3회 실패 뒤 6시간에 1번. 실측은 DB 상태가 필요해 하지 않음(학교 서버에 전체 크롤 반복 X) — 코드 기준 계산 + 목록 응답시간 실측
- SQL: 없음(새 쿼리는 기존 `source_url` UNIQUE 인덱스 사용)
- 환경변수(모두 선택, 기본값 있음): `CRAWLER_INCREMENTAL`(true, false면 예전처럼 전 페이지 — 비상 스위치), `CRAWLER_PARALLEL_SERVERS`(2, 1이면 완전 순차), `CRAWLER_FAILURE_THRESHOLD`(3, 0이면 끔), `CRAWLER_FAILURE_COOLDOWN_MINUTES`(360)
- 테스트: +15 → 237개 통과(기존 222). `CrawlerServiceTest` 9(증분 중단·고정 공지·끄기·페이지당 1회 판단·실패 뒤 전체 훑기·건너뛰기/복구·서버별 순차 병렬·푸시 순서), `CrawlerBoardCircuitBreakerTest` 3, `CrawlerRunTrackerTest` 1, `NewsCrawlStorageServiceTest` +2(H2)
- 충돌: #18(`fix/crawler-missing-boards`)과 `CrawlerService.java`의 `crawlAll`/`crawlBoard`가 겹침 — #18의 "목록 0건 게시판" WARN과 `firstPageCount`를 이 브랜치에 같은 문구로 넣어 둠 → 충돌 시 **이 브랜치 쪽을 택하면 #18 동작도 유지**. `CrawlerBoards`·`NewsPushDispatcher`는 안 건드림. `docs/worklog.md`는 끝 덧붙임(둘 다 남기기)
- 남은 일
  - 관리자 대시보드(FE)에 요청 수·소요 시간·건너뛴 게시판 표시
  - 서버 여러 대로 늘리면 차단기·증분 실패 기록이 인스턴스별(지금은 1대)
  - `NewsLocationMatcher.matchBuilding`이 새 글마다 `buildings` 전체 조회 — 새 글 수에만 비례해 그대로 둠(새 게시판 첫 수집 때만 수십 회)
  - 게시판이 수정된 글을 반영하지 않는 건 기존과 같음(필요하면 별도 갱신 주기)
