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
## 2026-10-02 — 내 제보 내역 (`feat/my-reports`, base main)
- 왜: 작성자가 승인·반려 푸시를 놓치면 자기 제보가 어떻게 됐는지(특히 지도에 안 뜨는 반려·숨김) 확인할 곳이 없었음
- 변경
  - **`GET /users/me/reports?page=&size=`**(로그인 필수): 본인 제보만 최신 등록순(`created_at DESC, id DESC`), `PageResponse` 형식(size 기본 20·최대 50). 관리자가 지운 `DELETED` 도 포함(행 자체를 지운 건 당연히 없음)
    - 필드: id, title, category, customCategoryLabel, buildingId, buildingName, floor, lat, lng, startsAt, endsAt, status, **displayStatus**, moderationNote, reviewedAt, createdAt, imageUrl, imageUrls(presigned GET)
    - `displayStatus` = 저장 상태 + 시간: PENDING / SCHEDULED(승인·시작 전) / ACTIVE(지도에 표시 중) / ENDED(기간 지남 — 승인 대기 중 끝난 것 포함) / REJECTED / HIDDEN / DELETED
    - `moderationNote` 는 REJECTED·HIDDEN 일 때만(ACTIVE·DELETED 의 관리자 메모는 내보내지 않음). 작성자 이름·신고자 정보 없음
  - **`GET /users/me/reports/count`** → `{ total, pending }` (설정 화면 배지용)
  - 삭제는 기존 `DELETE /reports/{id}` 그대로(본인 것만, 아니면 403, 상태 무관 hard delete + S3 사진 삭제)
  - 다른 PR(제보 일정 `feat/report-schedule`·#14)이 고치는 `ReportRepository`·`ReportService`·`ReportController` 를 건드리지 않으려고 `MyReportRepository`·`MyReportService`·`MyReportController` 를 새로 둠
- SQL·환경변수: 없음
- 테스트: `MyReportIntegrationTest` +5(본인 것만·최신순·상태별 displayStatus·사유 노출 범위, 페이지·상한 50, 개수, 비로그인 401, 남의 제보 삭제 403)
- 프론트: `feat/my-reports` — 설정 > 계정 "내 제보 내역"(승인 대기 배지), 반려 알림을 누르면 내역으로

- **10-02 추가 (#16)**: 신고 누적 등으로 숨겨진(HIDDEN) 제보는 작성자가 `DELETE /reports/{id}` 로 지울 수 없음(409 "신고로 검토 중인 제보는 운영진 검토가 끝난 뒤에 지울 수 있어요."). 검토 전에 지우면 신고 기록까지 사라져 제재 근거가 남지 않기 때문(약관 제8·10조, App Store 1.2). 반려·재공개 뒤에는 지울 수 있음. 테스트 1개 추가.
- 10-02 버그 점검 반영: 숨김(HIDDEN) 삭제 잠금을 "마지막 검토 뒤 들어온 신고가 있을 때"로 좁힘 — 운영진이 검토해 숨긴 제보는 작성자가 지울 수 있음(전엔 영영 409). 삭제 시 신고도 명시적으로 지움. 테스트 +1, 177개 통과
## 2026-10-02 — 예정 제보: 시작 시각 미리 지정 (`feat/report-schedule`, base main)
- 왜: "내일 11:00~15:00 붕어빵 트럭"처럼 미리 알고 있는 일을 당일에 다시 올려야 했음. 지금은 `startsAt` 검증이 사실상 없고(과거·먼 미래 모두 통과) `endsAt`만 "지금+7일"로 막고 있었음
- 규칙(서버 UTC 기준, `ReportService.validateSchedule`)
  - `startsAt`: 지금 − 10분(앱·서버 시계 오차 여유) ~ 지금 + **14일** (`report.startsAt.maxDays`)
  - `endsAt`: `startsAt`보다 뒤, 진행 기간 최대 **7일** (`report.maxDurationDays`, 여러 날 행사 가능 — 길이는 작성자가 정하고 관리자가 검토), 이미 지났으면 400(`@Future`)
  - 오류 문구 해요체: "시작 시각이 이미 지났어요…", "시작 시각은 오늘부터 14일 안으로 골라 주세요.", "종료 시각은 시작 시각보다 뒤여야 해요.", "진행 기간은 최대 7일까지 정할 수 있어요.", "종료 시각이 이미 지났어요…"
  - `report.endsAt.maxDays`(7일) 삭제 — 새 두 값이 대신함
- 지도: `GET /reports` 기본은 그대로 진행 중(startsAt ≤ 지금 ≤ endsAt)만 → 구버전 앱엔 시작 전 제보가 안 보이고, 시작 시각이 되면 자동으로 뜸. `?include=upcoming`이면 **24시간 안에 시작할** ACTIVE 제보를 시작 순으로 뒤에 덧붙임(응답 형식 그대로, `startsAt > 지금`이면 예정)
- 관리자: 시작 전에도 승인 가능. 응답엔 원래 `startsAt`/`endsAt`가 있어 앱 관리 화면에서 일정만 보여주면 됨
- 푸시
  - 시작 전 승인: 작성자에게 "제보가 승인됐어요" / "붕어빵 트럭\n10/3(토) 11:00부터 지도에 보여요"(KST)
  - 캠퍼스 새 제보 알림(REPORT_NEW)은 **지도에 실제로 뜨는 시작 시각에** 보냄 — `ReportStartPushScheduler`(매분 30초, `push.report-start-cron`)가 "직전 확인 ~ 지금" 사이에 시작한, 시작 전에 승인된(`reviewed_at < starts_at`) ACTIVE 제보를 찾아 보냄. 승인 때 "내일 11:00 · …"로 미리 보내는 안은 알림을 눌러도 지도에 제보가 없어서 버림
  - 상태 컬럼 없이 확인 구간만 메모리에 둠(SQL 없음). 서버 시작 시 10분 거슬러 봄 → 같은 제보를 다시 집어도 유저당 30분 빈도 제한이 중복을 막음. 10분 넘게 꺼져 있던 사이 시작한 제보는 알림 없이 지도에만 뜸. 서버 1대 기준
  - 이미 시작한 제보 승인은 기존과 같음(바로 승인 알림 + 새 제보 알림)
- `ReportModeratedEvent`에 `startsAt` 추가(기존 9인자 생성자 유지 → 다른 PR 호출부 그대로 컴파일)
- SQL: 없음. 환경변수: 선택 `REPORT_STARTS_AT_MAX_DAYS`(14), `REPORT_MAX_DURATION_DAYS`(7), `PUSH_REPORT_START_CRON`. 운영 `.env`에 `REPORT_ENDS_AT_MAX_DAYS`가 있으면 지워도 됨(안 쓰임)
- 여러 날 제보: 지도 조회는 `start ≤ 지금 ≤ end` 그대로라 기간 내내 뜨고(`ix_reports_live(status, ends_at, starts_at)` 사용), 시작 알림 스케줄러는 시작 시각이 확인 구간에 들 때만 집어 기간 중 다시 보내지 않음(테스트로 확인). 승인 알림·`include=upcoming`(24시간 안 시작)도 기간 길이와 무관
- 테스트: +12 → 182개 통과(`ReportScheduleIntegrationTest` 7, `ReportStartPushSchedulerTest` 3, `ReportPushDispatcherTest` +2). `AdminApiIntegrationTest`의 고정 과거 `startsAt`(2026-10-01)을 지금으로 바꿈
- 머지 충돌(`git merge-tree`, 10-02 오후 갱신): #13 없음. #15·#12 `docs/worklog.md`만(양쪽 유지). #14(main 병합 후 버전) `src/test/resources/application-test.properties` 끝 한 곳 — `push.report-start-cron=-`와 `push.admin-reminder-cron=-` 둘 다 남김. **#14 테스트 `AdminAlertDispatcherTest` 167행이 고정 과거 `startsAt`("2026-10-01T08:00:00.000Z")을 보내 이 PR과 합치면 400** → 뒤에 머지하는 쪽에서 `Instant.now().toString()`으로 바꿀 것. 이렇게 고쳐 main+이 PR+#13+#15+#14 합친 상태 228개 통과
- 배포 순서: #15 다음(마지막). SQL 없음 → 머지 후 배포만. 앱(`feat/report-schedule`)은 서버 배포 뒤에 OTA — 구서버도 미래 `startsAt`을 받고 지도엔 시작 뒤에만 띄우지만, 승인 즉시 "지도에 올라갔어요"·새 제보 알림을 보내(누르면 지도에 없음) 앱이 먼저 나가면 안 됨
- 10-02 버그 점검 반영: 지도 목록(`findLiveReports`·`findUpcomingReports`)에 `JOIN FETCH r.user` — 작성자 이름 때문에 작성자 수만큼 추가 쿼리가 나가던 N+1 제거(6→3 쿼리, 테스트 추가). 정각 크롤링이 시작 알림 스케줄러를 막던 단일 스케줄러 스레드 문제는 #14에서 고침(`SCHEDULING_POOL_SIZE`). 테스트 183개 통과
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

## 2026-10-04 — 댓글 내용 필터 (`feat/report-comments`, PR #19)
- 왜: App Store 1.2 는 "불쾌한 사용자 생성 콘텐츠를 거르는 방법"을 요구. 댓글은 사후 검토(바로 공개)라 신고·숨김만으로는 부족 → 올리는 순간 서버에서 한 번 거름. 제보는 사전 검토(PENDING → 관리자 승인)라 거르지 않음
- 변경
  - 새 `common/moderation/ContentFilter`(+ `ContentViolation`): 외부 API 없이 정규식·목록, 결정적. `ReportCommentService.create`(댓글·답글 공통)에서 공백·길이 검사 바로 뒤 호출 → 걸리면 400 + 아래 문구(앱은 400 의 serverMessage 를 그대로 보여 줌). 막힌 글은 저장하지 않아 빈도 제한에도 안 셈
  - 연락처 → "댓글에 연락처나 오픈채팅 주소는 쓸 수 없어요.": 010/011…·지역번호·070·050X(하이픈·점·공백·괄호, +82, "공일공"), 이메일, 오픈채팅/오픈톡/open.kakao/카톡 아이디·카톡id/텔레·라인·인스타 아이디 등(공백 지운 글에서)
  - 링크 → "댓글에는 링크를 쓸 수 없어요.": http(s)://, www., `xxx.com/.kr/.co.kr/.net/.io/.me/.ly…`, "naver 닷 com"·"닷컴"·"dot com"·"(.)"·"[dot]", 전각 문자. 한글 바로 뒤 점은 com/net/org/kr 만(문장 끝 "좋아요. Me" 오탐 방지)
  - 욕설·혐오·성적 표현 → "부적절한 표현이 있어 댓글을 올릴 수 없어요. 표현을 바꿔 다시 시도해 주세요.": `src/main/resources/moderation/banned-words.txt`(약 100개, 한 줄에 하나·# 주석). 낱말마다 소문자·숫자/문장부호/제로폭·한글 채움 문자 제거·한글 사이 영문 제거·반복 줄이기 후 부분 문자열 검사, 한 글자 낱말끼리는 붙여서 봄("시 발", "시1발", "ㅅ ㅂ", "개 새 끼"). 두 글자 이상 낱말끼리는 붙이지 않음("다시 발급", "3시 발표" 통과)
  - 오탐 막기: `moderation/allowed-words.txt`(시발점·다시발·수박씨·솜씨·등신대·닥쳐오 …)를 먼저 가린 뒤 검사. 보지·자지·새끼·미친·꺼져·졸라·시바·개같 처럼 흔한 낱말에 들어가는 짧은 말은 목록에서 뺌
  - 목록은 리소스 파일만 고쳐 늘릴 수 있음(재배포 필요). banned-words.txt 가 없거나 비면 기동 실패(필터가 조용히 꺼지는 것 방지)
  - 개인정보: 댓글 내용은 로그에 안 남김. 차단 시 `댓글 필터 차단 reason=… reportId=…` 만 INFO
- 테스트: +107 → 304개 통과. `ContentFilterTest`(링크 20·연락처 22·욕설 32 우회 표기 포함·정상 글 28 오탐 확인·목록/허용 목록), 통합 테스트 1개(링크/닷컴/전화/오픈채팅/욕설/답글 → 400·문구 확인·저장 안 됨, 정상 문장 201)
- 남은 일: 숫자로 쓴 욕("18놈"), 한글로 읽은 전화번호("공일공 일이삼사"는 "공일공"만), 이미지 속 글자는 못 거름 → 신고·자동 숨김이 받침. 운영 신고 데이터 보고 목록 보강. 제보 제목·설명·닉네임에도 쓸지 검토(지금은 사전 검토라 안 씀)

## 2026-10-04 — 탈퇴 회원 부정 이용 방지 기록 1년 보관 + 이용 제한 고지 (`feat/withdraw-retention`, base main)
- 왜: 정지되거나 위반 제보로 삭제 처리된 회원이 탈퇴 후 같은 소셜 계정으로 바로 재가입하면 이력이 모두 사라져 운영진이 알 수 없었음. 법무 검토(공정위 2019 불공정약관 심사 지침): 이용 제한 시 사유 고지·이의 제기 기회 필요
- 범위(운영자 결정, 법무 검토로 두 번 축소 — 개인정보 보호법 제3조·제16조 최소 수집, 제15조 제1항 제6호 정당한 이익의 필요성·비례성)
  - 대상: 탈퇴 시점 `status = SUSPENDED` 또는 `suspended_at` 있음, 또는 **관리자가 삭제(`DELETED`)한 제보**(`reviewed_at` 있음)가 1건 이상인 회원만. `DELETED`는 관리자만 만들 수 있는 상태(본인 삭제는 행 삭제, 신고 누적은 `HIDDEN`)
  - 제외: 신고만 받은 제보(자동 숨김 `HIDDEN` 포함), 반려(`REJECTED` — "중복 제보"·"캠퍼스 밖" 같은 단순 반려가 섞임), 남의 제보에 단 신고. 그 밖의 회원은 지금처럼 즉시 삭제, 아무것도 안 남김
  - **사진은 보관하지 않는다**: 관리자 삭제 시점에 이미 지워지고(`AdminReportService.moderate`, 기존 동작 유지), 탈퇴 때 남은 제보 사진도 지금처럼 사본 없이 모두 삭제
- 변경 — 탈퇴 기록(`user/retention/WithdrawRetentionService`)
  - 별도 테이블 `withdraw_retentions`(users FK 없음): `social_type` + `social_id_hash`(HMAC-SHA256 hex, 원문 저장 안 함), 정지 여부·사유·시각, `violation_report_count`, `snapshot`(JSON), `withdrawn_at`, `retain_until`(= 탈퇴 + 1년), `rejoined_user_id`·`rejoined_at`
  - 스냅숏(`version` 2): 탈퇴마다 정지 정보 + 위반 확정 제보 요약(`id`, `category`, `customCategoryLabel`, `title`, `content` 앞 200자, `status`, `createdAt`, `moderationNote`, `flagCount`, `flagReasons`). 위치·기간·사진·위반 아닌 제보·단 신고·닉네임·이메일·Apple 토큰 없음
  - 만료 정리: `purgeExpired` 매일 03:40 UTC(`WITHDRAW_RETENTION_PURGE_CRON`) — `retain_until` 지난 행만 삭제(회당 최대 200건)
  - 재가입 감지: 카카오(`CustomOAuth2UserService`)·Apple(`AppleLoginService`) 신규 가입 직후 같은 HMAC의 보관 중 기록이 있으면 `rejoined_user_id`·`rejoined_at` 연결 + 관리자 알림 `MEMBER_REJOINED`(data.type `ADMIN_MEMBER_REJOINED`, `userId`, 기존 묶음 규칙). 자동 정지 없음, 가입은 막지 않음
  - 재탈퇴: 같은 계정이면 행을 새로 만들지 않고 이어 붙임(`withdrawals` 추가, 수 합산, 정지 정보는 새 값이 있을 때만 교체, `retain_until` = 새 탈퇴 + 1년, 재가입 연결 해제). 새 이력이 없어도 보관 중 기록이 있으면 갱신
  - 관리자 API: `GET /admin/users`·`/admin/users/{id}`(및 정지/해제 등 응답) 회원에 `priorHistory`(`withdrawnAt`, `retainUntil`, `rejoinedAt`, `suspendedAt`, `suspendedReason`, `wasSuspendedAtWithdrawal`, `violationReportCount`, 없으면 null). `GET /admin/users/{id}/prior-history` → `{userId, priorHistory, withdrawals[]}`(없으면 404). `docs/admin-api-spec.md` "회원" 절
- 변경 — 이용 제한 고지
  - 관리자 정지/해제 → 커밋 뒤 본인에게 푸시(`AccountStatusPushDispatcher`, `UserSuspensionChangedEvent`). 정지: "이용이 제한됐어요" / "사유: …\n이의가 있으면 14일 안에 hongikonsupport@gmail.com 으로 알려 주세요", data.type `ACCOUNT_SUSPENDED`. 해제(정지 중이었을 때만): "이용 제한이 풀렸어요", `ACCOUNT_UNSUSPENDED`. 알림 설정과 무관(서비스 고지), 활성 Expo 기기 없으면 없음, 발송 실패해도 관리자 작업은 성공
  - 정지 회원 쓰기 403 메시지에 사유 포함: "운영 정책 위반으로 이용이 제한된 계정이에요(사유: …). 제보·신고·문의를 할 수 없어요. 이의 제기: hongikonsupport@gmail.com"(사유 없으면 사유 부분 생략)
  - `GET /users/me`에 `status`(ACTIVE/SUSPENDED), `suspendedReason`, `suspendedAt` 추가 — 앱 배너용
- SQL: `db/create_withdraw_retentions_table.sql`(IF NOT EXISTS, 배포 전이라 파일 자체를 최종 형태로 고침) — ddl-auto=validate라 배포 전 실행
- 환경변수: `WITHDRAW_RETENTION_KEY_SECRET`(선택, 비우면 JWT_SECRET에서 파생 — **운영은 별도 값을 넣고 이후 바꾸지 말 것**, 바꾸면 기존 기록과 대조 불가), `WITHDRAW_RETENTION_PURGE_CRON`(선택, 기본 `0 40 3 * * *`). S3·IAM 설정 변경 없음(사진을 보관하지 않으므로)
- 테스트: 210 → 222개 전부 통과. `WithdrawRetentionIntegrationTest` 8(신고만 받은·자동 숨김 작성자 기록 없음, 반려만 된 작성자 기록 없음, 정지 회원 해시·1년·정지 정보만, 관리자 삭제 제보 요약만·사진 미보관, 재가입 알림·연결·관리자 API, 첫 가입 무반응, 만료 정리, 재탈퇴 갱신), `AppleLoginIntegrationTest` +1(Apple 재가입), `AccountStatusPushDispatcherTest` 3. `UserModerationIntegrationTest` 403 문구 기대값 변경
- 남은 일
  - **FE 개인정보 처리방침 문구 갱신 필요**(보관 대상: 정지 이력·관리자 삭제 제보가 있는 회원만 / 항목: 소셜 계정 식별값의 해시, 정지 정보, 삭제된 제보 요약 / 1년 / 사진은 보관 안 함) — 메인 에이전트가 FE 레포에서 진행 중. 배포 전 문구와 이 구현이 맞는지 확인
  - FE: 관리 탭 회원 카드에 `priorHistory` 표시, `ADMIN_MEMBER_REJOINED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_UNSUSPENDED` 알림 라우팅, `/users/me` 정지 배너
  - 한계: 정지 해제(`unsuspend`)가 `suspended_at`을 비우므로 "정지됐다가 해제된 뒤 탈퇴"한 회원은 정지 이력으로 잡히지 않음(관리자 삭제 제보가 있으면 그쪽으로 잡힘). 필요하면 정지 이력 별도 컬럼 검토
  - 재가입 감지는 가입 시 1회만(기록 연결은 가입 트랜잭션 안). 키 미설정(JWT_SECRET도 없음) 환경에선 기록·감지 모두 꺼짐(WARN 로그)

## 2026-10-04 — 다중 로그인 세션 + refresh 재발급 유예 (`fix/multi-session-refresh`, base main 00f26d1)
- 왜: "로그인이 만료됐어요"로 반복 로그아웃 제보. access 30분·refresh 14일 설정은 정상이고, 원인은 `refresh_tokens`가 **유저당 1 row**(`uq_refresh_token_user`)라 로그인·재발급마다 그 한 줄을 덮어쓴 것
  1. 다른 기기·앱+웹·관리자 웹 콘솔에서 로그인하면 기존 세션의 refresh 해시가 바뀜 → 그쪽 다음 재발급 401 → 로그아웃
  2. **웹 여러 탭**: 탭들이 localStorage 의 같은 refresh 토큰을 공유하는데 프론트 single-flight 는 탭(JS 컨텍스트) 안에서만 동작 → 두 탭이 동시에 재발급하면 먼저 온 쪽이 로테이션, 나중 쪽 401 → 웹 클라이언트가 공유 저장소를 비워 **다른 탭까지 로그아웃**
  3. 모바일에서 재발급은 서버에서 성공했는데 응답이 유실 → 앱은 옛 토큰으로 재시도 → 401 → 로그아웃
  - 덤: refresh 토큰에 `jti`가 없어 같은 유저·같은 초에 만든 토큰은 바이트까지 같았음
- 변경
  - 세션(로그인 1번으로 시작되는 토큰 사슬)당 1 row. 로그인은 새 row 추가(기존 세션 유지). 유저당 상한 `jwt.max-sessions-per-user`(기본 10) — 넘으면 가장 오래 안 쓴(`updated_at`) 세션부터 삭제, 로그인 때 그 유저의 만료 세션도 정리
  - 재발급: row 를 `SELECT ... FOR UPDATE`로 잠그고 찾음
    - 현재 해시 → 로테이션(바뀌기 전 해시를 `previous_token_hash`, `rotated_at=now`)
    - 직전 해시 + `rotated_at`부터 `jwt.refresh-reuse-grace-seconds`(기본 60초) 안 → 동시 재발급/응답 유실 재시도로 보고 **새 세션 row 를 하나 더 만들어**(fork) 새 토큰 쌍 발급. 같은 row 를 다시 로테이션하면 먼저 받은 쪽 토큰이 previous 로 밀려 유예가 끝난 뒤(다음 재발급은 보통 30분 뒤) 401 이 되므로, 사슬을 갈라 두 쪽 모두 자기 토큰으로 계속 재발급 가능. 안 쓰인 사슬은 만료·정기 정리·상한으로 사라짐
    - 직전 해시인데 유예 지남 → 401 + 경고 로그(세션은 지우지 않음 — 탈취 의심 시 세션 전체 폐기 방식은 늦게 재시도한 정상 기기 하나 때문에 같은 세션의 다른 쪽까지 로그아웃시켜 이번 수정 목적과 어긋남. 로테이션으로 옛 토큰은 유예 뒤 어차피 못 씀)
    - 동시 요청 두 번째는 첫 번째 커밋까지 행 잠금에서 기다렸다가 previous 로 찾음 → 둘 다 200
  - 로그아웃(`/auth/logout`): 그 토큰의 세션 row 만 삭제(현재 해시, 없으면 직전 해시). 다른 기기 로그인 유지. 탈퇴는 기존대로 `deleteByUser_Id`로 전부 삭제
  - access·refresh 토큰에 `jti`(UUID) 추가. 검증은 jti 를 보지 않아 **배포 전 발급된 토큰도 만료 전까지 그대로 유효**(로그인 유지)
  - `RefreshTokenCleanup` — 만료된 세션 row 매일 04:40 삭제(`@Scheduled`, 테스트는 `-`로 끔)
  - Swagger 설명(재발급 유예·로그아웃 범위) 갱신
- SQL: **`db/alter_refresh_tokens_multi_session.sql` — 배포 전에 실행**(ddl-auto=validate 라 컬럼이 없으면 새 서버가 안 뜸). 재실행 안전(information_schema 확인 후 조건부 DDL)
  1. `idx_refresh_token_user(user_id)` 추가 → 2. `uq_refresh_token_user` 삭제(FK 때문에 1 먼저) → 3·4. `previous_token_hash varchar(64) NULL`, `rotated_at datetime NULL` → 5. `uq_refresh_token_hash(token_hash)` → 6. `idx_refresh_token_prev_hash` → 7. 확인 SELECT
  - 옛 서버는 새 컬럼(NULL 허용)을 몰라도 그대로 동작하므로 SQL 먼저 실행해도 안전. 기존 row 는 token_hash 가 지금 쓰는 토큰이라 배포 후 첫 재발급에서 정상 로테이션 → **배포로 로그아웃되는 사용자 없음**
  - 로컬 MySQL 로 기존 데이터 있는 상태 실행·재실행·같은 유저 2번째 row INSERT·되돌리기 SQL(파일 끝 주석) 확인
- 환경변수(모두 선택): `JWT_REFRESH_REUSE_GRACE_SECONDS`(기본 60, 0=유예 없음), `JWT_MAX_SESSIONS_PER_USER`(기본 10), `JWT_REFRESH_CLEANUP_CRON`(기본 `0 40 4 * * *`)
- 테스트: +9 → 231개 통과(main 기준 222). `RefreshTokenSessionIntegrationTest` — 두 기기 로그인 둘 다 재발급 / 로그아웃은 그 세션만 / 유예 안 재사용 → 두 쪽 모두 유예 뒤에도 재발급 / 실제 동시 2요청 둘 다 200 / 유예 지난 직전 토큰 401·현재 토큰 유지 / 세션 상한 10 / 탈퇴 시 전부 삭제 / jti 없는 옛 토큰+옛 row 재발급 / 만료 세션 정리. `AppleLoginIntegrationTest`는 `findByUser_Id`(삭제) 대신 `countByUser_Id`
- 남은 일
  - 프론트(웹): 탭 간 single-flight(`navigator.locks` 또는 BroadcastChannel)와 "401 받았는데 저장소 토큰이 그사이 바뀌었으면 비우지 말고 새 토큰으로 재시도" — 서버 유예로 대부분 막히지만 60초 넘게 멈춘 탭은 여전히 401
  - 모바일 응답 유실 후 60초 넘게 지나 재시도하면 여전히 401(앱이 백그라운드로 간 경우 등). 운영에서 401 경고 로그(`유예가 지난 직전 refresh 토큰 재사용`) 빈도를 보고 유예를 늘릴지 결정
  - "다른 기기 모두 로그아웃"·세션 목록 API는 없음(필요하면 후속)
- 리스크: 유예 안에서는 탈취된 직전 토큰으로도 새 세션을 받을 수 있음(60초 창, 해시 저장·HTTPS 전제). 동시 재발급이 MySQL 데드락으로 끝나면 한쪽이 401 이 아닌 500 — 이론상 가능, 드묾. 세션당 row 가 늘어 테이블이 커지지만 상한 10·만료 정리로 유저당 최대 10행

## 2026-10-05 — 관리자 제보 검토: 끝난 제보를 '노출 중'으로 세지 않기, 등록일(날짜) 조회 (`fix/overview-live-count`)
- 왜: 승인(ACTIVE)한 제보는 끝나는 시각이 지나도 상태가 ACTIVE 로 남고 지도 목록(live)에서만 빠진다. 관리자 대시보드 '노출 중' 수가 끝난 제보까지 세서 실제보다 컸다. 검토 목록을 날짜로 좁혀 보고 싶다는 요청도 있었다
- 변경
  - `GET /admin/overview` `reports.active`: `countByStatusAndEndsAtAfter(ACTIVE, now)` — 지도 목록과 같은 기준(endsAt > now)
  - `GET /admin/reports?from=&to=`: 등록일(한국 날짜 yyyy-MM-dd, 둘 다 포함, 선택). DB 는 UTC 라 한국 0시를 UTC 로 바꿔 [from, to+1) 로 거른다. to < from 이면 400. 기간 안에서 최신순 최대 200건(기존과 같음). 파라미터 없으면 기존과 동일
  - FE(관리 탭)는 '노출 중'(끝나지 않은 ACTIVE)과 '종료'(끝난 ACTIVE) 탭을 화면에서 나누고, 기간 칩(전체·오늘·7일·30일·날짜 지정)으로 from·to 를 보낸다
- SQL·환경변수: 없음
- 테스트: +2 → 224개 통과(대시보드 노출 중 수, 날짜 경계 한국 10/3 01:00 = UTC 10/2 16:00 포함·to<from 400)

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
## 2026-10-02 — 소식 0건 게시판 10개 수집 (`fix/crawler-missing-boards`, base main)
- 왜: 앱 구독 게시판 49개(FE `TREE_DATA`) 중 10개가 운영 `/news?sourceId=`에서 0건. 9개는 `CrawlerBoards`에 아예 없었고(구독 PUT도 400), 조소과는 설정돼 있는데 저장된 글이 없었음
- 원인·조치(사이트는 robots.txt 확인 후 크롤러 UA로 몇 건만 요청해 확인)
  - 새 게시판: 기초과학과 `science/0401.do`(표 summary `학과공지사항`), 자율전공 → 서울캠퍼스 자율전공 `fm/0401.do`, 디자인엔지니어링전공 `smpd/0401.do`, 바이오헬스융합학부 → 바이오헬스 혁신융합대학사업단 Imweb `biohealth.hongik.ac.kr/22`
  - 자체 게시판 없음 → 상위 게시판 별칭(`CrawlerBoards.SOURCE_ALIASES`): 디자인경영·예술경영전공 → 디자인예술경영학부, 데이터사이언스 → 산업데이터공학과, 사물인터넷공학 → 전자전기공학부, 지능로봇공학 → 기계시스템디자인공학과. 글은 상위 sourceId로 한 번만 저장하고 `GET /news` 필터·구독 검증·새 소식 푸시 대상에서 펼침
  - 조소과·기초과학과: URL·마크업·파서 모두 정상, 게시판 자체가 비어 있음("등록된 글이 없습니다"). 글이 올라오면 수집됨
  - 크롤링 끝에 첫 페이지 목록 0건 게시판을 `WARN 목록 0건 게시판 N개: ...`로 한 줄 기록
- DB·환경변수: 변화 없음
- 테스트: 저장한 목록 HTML 픽스처(`src/test/resources/crawler`)로 파서 확인, FE 49개 id 전부 구독 가능 확인, 별칭 조회·푸시·구독 테스트 추가. `./gradlew test` 통과
- **10-02 추가 (#18)**: 사물인터넷공학전공·지능로봇공학전공은 상위 학부(전자전기공학부·기계시스템디자인공학과) 게시판 연결을 해제(`UNLINKED_APP_SOURCE_IDS`) — 학부 전체 공지까지 받게 되는 걸 막기 위해(사용자 결정). 구독 요청은 그대로 받되(앱 400 방지) 자체 게시판이 생기기 전까지 소식·푸시 없음.

## 2026-10-05 — 관리자 콘솔 로그인 닉네임 가리기 (`feat/admin-hide-login-name`, base main)
- 왜: 관리자 화면 곳곳(제보 카드 "실명 (앱 표시: 와우)", 회원 카드, 신고 목록, 문의 작성자)에 카카오/Apple 로그인 닉네임 원문이 그대로 보였음. 실명인 경우가 많아 개인정보 보호법 제3조(목적에 필요한 최소한)에 맞춰 평소엔 앱에 보이는 이름 + 공개 회원 번호로만 회원을 가리키고, 원문은 필요할 때 버튼으로만 열람하도록 바꿈(오너 승인)
- 변경
  - 관리자 응답에서 로그인 닉네임 원문 제거. 구버전 화면이 깨지지 않게 옛 키는 남기되 **값을 앱 표시 이름으로** 바꿈(새 화면은 새 키를 씀)
    - `AdminReportResponse`(목록·상태 변경): `authorNickname` = `authorDisplayName`, 끝에 `authorMemberCode` 추가
    - `AdminReportFlagListResponse.Item`: `reporterNickname` = 표시 이름, `reporterId`·`reporterDisplayName`·`reporterMemberCode` 추가
    - `FeedbackResponse`(`/admin/feedback` 전용): `userNickname` = 표시 이름, `userDisplayName`·`userMemberCode` 추가
    - `AdminUserResponse`: `nickname` = `displayName`, 끝에 `appNickname`(없으면 null — 회원 카드 제목은 앱 닉네임, 없으면 "홍**" + "앱 닉네임 없음") 추가. email 은 원래 없음
    - 댓글 관리자 응답(`AdminCommentResponse`)·재가입 이력(`priorHistory`)은 main 에 아직 없음 → 해당 브랜치에서 같은 규칙 적용 필요(남은 일)
  - 회원 조회 `GET /admin/users?q=`: 로그인 닉네임 검색 제거. 회원 번호(10자리, 대소문자 무시)·숫자 id·앱 닉네임 일부만. `UserRepository.findTop50ByAppNicknameContainingOrderByIdDesc`. Swagger 설명 갱신
  - 새 API `GET /admin/users/{id}/login-name` → `{userId, loginNickname, socialType}`. ADMIN 전용(SecurityConfig `/admin/**`, 비로그인 401·일반 403), `Cache-Control: no-store`, 없는 회원 404
  - 열람 기록: **별도 테이블 없이 서버 로그 한 줄**(오너 결정 — 화면·처리방침에 열람 기록 기능을 드러내지 않음). `ADMIN_AUDIT` 로거에 `admin-login-name-view adminId={} targetUserId={}` (id 만, 닉네임 값은 절대 안 씀). 「개인정보의 안전성 확보조치 기준」 제8조 접속기록은 이 로그 + 기존 `AdminAuditInterceptor` 접속 로그로 최소한 충족 — 운영에서 ADMIN_AUDIT 로그를 1년 이상 보관해야 하는 건 기존과 같음
  - (처음엔 `admin_pii_access_logs` 테이블·정리 스케줄로 만들었다가 오너 결정으로 같은 브랜치에서 걷어냄 — SQL·엔티티·환경변수 없음)
- SQL: 없음
- 환경변수: 없음
- 테스트: +5 → 227개 통과(main 222). `AdminPiiAccessIntegrationTest` 5(제보·신고·문의·회원 응답에 로그인 닉네임 없음 + 회원 번호, 로그인 닉네임 검색 불가·앱 닉네임/회원 번호/id 검색, 열람 401/403/200 + no-store + 로그에 id 만·값 없음, 없는 회원 404·로그 없음). 기존 `AdminApiIntegrationTest`·`UserModerationIntegrationTest`·`MemberCodeIntegrationTest` 기대값 갱신
- 프론트: `feat/admin-hide-login-name` — 표시 이름 + 회원 번호로 표시, 회원 카드 제목을 앱 닉네임으로, "로그인 닉네임 보기" 버튼(404면 "서버 업데이트 후 사용 가능"), 처리방침 문구 정확히(운영진도 평소엔 앱 닉네임·회원 번호로 확인)
- 남은 일
  - 댓글 관리자 응답(`AdminCommentResponse` — 댓글 브랜치)과 재가입 이력 응답에도 같은 규칙(로그인 닉네임 제거, 회원 번호 추가)
  - 구버전 관리자 화면이 모두 사라지면 옛 키(`authorNickname`·`reporterNickname`·`userNickname`·`nickname`) 삭제

## 2026-10-05 — 버그 점검 수정 (`fix/be-bughunt-1005`, base main `00f26d1`)
- 왜: 동시 요청 경합(유니크 키 위반 500), 남용 제한 부재(문의·제보), 입력 범위 미검증(DB 오류 500), 반려·삭제 제보 재공개(사진 없이 공개 + 새 제보 알림 재발송), 지도 목록 N+1, UTC 날짜로 센 "오늘"
- 변경
  - 공통: `GlobalExceptionHandler`에 `DataIntegrityViolationException` → 409 "이미 처리된 요청이에요. 잠시 후 다시 확인해 주세요." (로그엔 제약 이름만 — MySQL 메시지에 푸시 토큰·키워드 값이 들어 있음). `common/persistence/UniqueConflictRetry`(유니크 위반 시 REQUIRES_NEW 새 트랜잭션으로 1회 재시도), `common/ratelimit/SlidingWindowRateLimiter`(기존 `acquireQuota` 방식을 공용으로)
  - 기기 등록: 같은 토큰 동시 등록 → 재시도로 둘 다 성공·행 하나. `DeviceRegisterRequest` `@Size(max=255)` + EXPO 토큰 형식(`ExponentPushToken[…]`/`ExpoPushToken[…]`) → 400
  - 제보 신고: `ReportFlag`에 `uq_flag` 명시(운영엔 이미 있음, `create_report_flags_table.sql`) + `saveAndFlush` → 동시 중복 신고 409 "이미 신고한 제보예요.". ACTIVE 아닌 제보 404, 본인 제보 400. 응답 `flagCount` → `flagged`(앱 미사용, 다른 사람 신고 수 노출 불필요)
  - 제보 등록: lat ±90·lng ±180·층 -10~30(0층 400) → 400(전엔 DECIMAL(10,7) 초과로 500), 좌표는 소수 7자리로 맞춰 저장, 고른 건물 중심과 300m 넘게 떨어지면 400. 승인 대기 3건(DB 카운트)·1시간 5건(메모리, 사진 검증까지 통과한 요청만 셈) 넘으면 429
  - 관리자 제보 처리: REJECTED → ACTIVE/HIDDEN, DELETED → 무엇이든 400 "반려·삭제한 제보는 다시 공개할 수 없어요. 작성자에게 다시 올려 달라고 해 주세요." 반려 사유 수정(REJECTED→REJECTED)·반려→삭제·숨김→다시 공개는 그대로
  - `GET /reports`: `JOIN FETCH r.user`(작성자 N+1 제거, ManyToOne 이라 중복 행 없음), 최신순 최대 300건
  - 문의 `POST /feedback`: 접속 IP(+로그인 시 사용자)마다 10분에 5건 → 429. IP 는 `request.getRemoteAddr()`(nginx 가 X-Forwarded-For 덮어씀, `AdminAuditInterceptor`와 같음). 본문 검증 통과한 요청만 셈
  - 북마크·학과 구독 생성: 이미 있으면 기존 행 반환(멱등, 전엔 409 — 앱은 두 API 모두 안 씀), 경합 재시도. 삭제는 조합 벌크 삭제(중복 행 있어도 500 없음). 학과는 isPrimary 재요청 시 주 학과 전환
  - 키워드 구독: 앞뒤 공백 제거, 대소문자 무시 중복 409(앱이 409 를 "이미 등록한 키워드예요"로 표시하므로 유지), 유저당 30개(앱 20개) 400, 경합도 409
  - 알림 설정 첫 저장·게시판 구독 upsert: `UniqueConflictRetry`로 동시 요청 둘 다 성공
  - KST: `NewsPushDispatcher` 오래된 소식 기준일을 한국 날짜로(KST 00~09시에 하루 밀리던 문제), `NewsCrawlStorageService` 작성일 대체값 `now(KST)`(한 줄 — 크롤러 PR #26 과 겹침 최소화)
- SQL: `db/alter_add_unique_user_lists.sql` — bookmarks(user_id, news_id)·keyword_subscriptions(user_id, keyword)·user_departments(user_id, department_id) 중복 정리 후 같은 컬럼 조합 유니크 인덱스가 **없을 때만** 추가(재실행 안전, 로컬 MySQL 로 중복 정리·재실행 확인). 배포 전후 아무 때나 실행해도 앱은 뜸(`ddl-auto=validate`는 유니크 비교 안 함), 다만 실행 전까진 경합 시 중복 행이 생길 수 있음 → 배포 직전 실행 권장. RDS 스냅샷 먼저. `report_flags.uq_flag`는 운영에 이미 있어 SQL 없음
- 환경변수(모두 선택, 기본값): `REPORT_CREATE_LIMIT_PER_HOUR`(5), `REPORT_CREATE_MAX_PENDING`(3), `REPORT_BUILDING_MAX_DISTANCE_METERS`(300), `FEEDBACK_RATE_LIMIT_MAX_REQUESTS`(5), `FEEDBACK_RATE_LIMIT_WINDOW_MINUTES`(10)
- 테스트: +24 → 246개 통과(기준 222). `UserDeviceRegisterRaceIntegrationTest` 3(스파이로 결정적 경합 + 두 스레드), `GlobalExceptionHandlerTest` 1, `ReportAbuseGuardIntegrationTest` 7, `SlidingWindowRateLimiterTest` 2, `FeedbackRateLimitIntegrationTest` 2, `NewsPushCutoffTest` 2, `NewsCrawlStorageKstTest` 1, `UserListIdempotencyIntegrationTest` 6. 경합 테스트는 수정 전 코드에서 실패 확인. `AdminAlertDispatcherTest` 자동 숨김 테스트는 숨겨진 뒤 4번째 신고가 404 인 것으로 수정
- 남은 일
  - 프론트: 관리자 제보 화면 `actionsFor('REJECTED')`에서 '승인' 버튼 제거(삭제만), DELETED 는 이미 버튼 없음. 층 휠 지하를 B10 까지로(지금 B30 까지 고를 수 있음 → 서버 400). `ReportFlagResult.flagCount` 타입을 `flagged`로(앱은 값을 안 읽어 동작 영향 없음). 문의·제보 429 문구는 서버 메시지 그대로 표시됨(`SERVER_MESSAGE_STATUSES`에 429 포함)
  - 메모리 제한은 서버 1대 기준(재시작 시 초기화, 여러 대면 대수만큼 느슨). 승인 대기 3건 확인은 동시 등록 시 1건 넘칠 수 있음(도배 방지 목적엔 충분)
  - 캠퍼스 와이파이처럼 IP 하나를 여럿이 쓰면 문의 한도를 함께 씀(10분 5건이라 실제로 걸릴 일은 드묾)
  - `ReportPushDispatcher`의 "반려 → 승인 = 첫 공개" 분기는 이제 도달하지 않음(남겨 둠)

## 2026-10-05 — 통합 배포 준비: PR #16 #17 #18 #19 #20 #21 #24 #25 #26 #27 #28 을 main 에 합침 (`release/2026-10-05`)

- 열한 개 PR 을 한 브랜치에 차례로 합치며 충돌을 풀었다. 배포 순서·SQL·환경변수는 `docs/deploy-runbook-2026-10-05.md`.
- 충돌 해결: 회원 검색은 회원 번호(#15)로 찾고 탈퇴 기록 요약(#21)을 붙이며 로그인 닉네임으로는 찾지 않는다(#27).
  `AdminUserResponse` 는 `memberCode`·`appNickname`·`priorHistory` 를 모두 싣는다. 크롤러는 #26 구현을 쓴다(#18 의 0건 게시판 로그가 이미 들어 있다).
  제보 생성자는 #28 의 명시 생성자에 #19·#20 의 댓글 수·공감 통계 의존성을 더했다. 지도 목록 300건 상한(#28)을 include=upcoming(#17)·HOT 목록(#20)에도 걸었다.
- #27 규칙을 #19 에도 적용: 관리자 댓글 응답 `authorNickname` 이 로그인 닉네임 원문이던 것을 앱에 보이는 이름으로 바꾸고 `authorMemberCode` 를 더했다.
- 테스트: 목록 쿼리 수 상한에 댓글 수·공감 통계(건수와 무관하게 1번씩)를 더했다. 지난 고정 시각(10-01)을 startsAt 으로 쓰던 테스트는 #17 의 과거 시각 검증에 걸려 지금 시각으로 바꿨다. 전체 468개 통과.
- 대시보드 '노출 중'을 지도 목록과 같은 기준(startsAt ≤ now ≤ endsAt, `countLive`)으로 센다. #25 의 `endsAt > now` 기준은 #17 의 예정 제보(승인했지만 시작 전)까지 '노출 중'으로 셌다. 테스트에 예정 제보를 더했다.
- 예정 제보(`include=upcoming`) 범위를 24시간 → 48시간으로 늘렸다(모레 아침 행사도 미리 보이게, 10-05 요청). 앱은 서버가 준 목록을 그대로 그려 앱 수정은 없다.

## 2026-10-05 — 관리자에게 같은 제보 알림이 두 번 가던 문제 (`fix/admin-no-duplicate-new-report`)

- 관리자는 제보 등록 때 "[관리] 새 제보 승인 대기"를 받고, 직접 승인한 뒤 일반 "새 제보 · 장소"(REPORT_NEW)를 또 받았다.
- `claimNewReportRecipients` 에서 관리자 알림을 켠 관리자(role=ADMIN, admin_alerts_enabled=true)를 뺀다. 관리자 알림을 끈 관리자는 일반 사용자처럼 받는다. DB 변경 없음.

## 2026-10-05 — 운영 배포 기록 (#22, #15/#23, #29, #30, #31)
- 배포 방식: SQL을 운영 DB에 먼저 실행한 뒤 코드 배포(prod는 ddl-auto=validate). 배포 전 이미지 태그와 DB 스냅샷으로 롤백 지점 확보.
- 10-04: #22(기기 토큰 중복 INSERT 500 수정), #15/#23(회원 번호 member_code). #15는 base가 main이 아니어서 #23으로 다시 올림.
- 10-05 통합 릴리스 #29(#16~#21, #24~#28): 구성 PR이 자동 종료되도록 merge commit으로 머지.
  - 실행한 SQL: create_report_comments_table, create_report_community_tables, create_withdraw_retentions_table, alter_refresh_tokens_multi_session
  - alter_add_unique_user_lists는 운영 DB에 유니크 인덱스가 이미 있어 실행하지 않음(중복 0건 확인)
  - 환경변수 추가: WITHDRAW_RETENTION_KEY_SECRET (한번 정하면 변경 금지, 바꾸면 기존 보관 기록 식별 불가)
- #30 제보 사진 S3 업로드, #31 .gitignore에 *.p8 추가
- 제보 사진 S3 설정: 전용 버킷(reports/ 경로만 사용, 30일 후 자동 삭제), CORS(PUT/GET, 서비스 도메인만), 최소 권한 IAM 정책을 EC2 인스턴스 역할로 연결, IMDSv2 필수 + hop limit 2(컨테이너에서 자격 증명 사용). 환경변수 AWS_S3_BUCKET, AWS_REGION. 미설정 시 사진 기능은 비활성(업로드 503).
- 검증: 앱에서 사진 업로드, DB 저장, 앱 표시, S3 저장 확인. 로그에 접근 거부 오류 없음.
- 애플 로그인: 백엔드(/auth/apple, 탈퇴 시 토큰 revoke) 운영 반영됨. 잘못된 토큰에 401 응답 확인. iOS 실기기 로그인/탈퇴 테스트는 FE 새 빌드 후 진행 예정.
- 알려진 사항: 지도 목록은 시작 시각 이후에만 노출(예정 제보는 include=upcoming 48시간). 서버 시간대는 UTC.
- 아직 운영 미배포: #32(관리자 중복 알림 수정)
- 남은 일: 스모크 테스트 일부(로그인 유지, 댓글/반응, 관리자 탭), 애플 로그인 iOS 실기기 테스트, retained/ 366일 보관 규칙 사용 여부 확인(FE), PR #12 정리
- 예정 제보 범위를 다시 48시간 → 3일(72시간)으로 늘렸다(10-05 요청). DB 변경 없음.

## 2026-10-05 — 웹(hongikon.com) Apple 로그인 받기 (`feat/apple-web-login`)

- `app.apple.web-client-id`(APPLE_WEB_CLIENT_ID, 기본 com.hongikon.web)·`app.apple.web-redirect-uri`(기본 https://hongikon.com/auth/apple/callback) 추가.
- identity token aud 로 웹 Services ID 도 허용(`AppleProperties.allClientIds`). 웹 로그인의 authorization code 교환에는 redirect_uri 를 함께 보낸다
  (안 보내면 교환이 실패해 탈퇴 때 Apple 토큰을 폐기할 수 없다 — 5.1.1(v)). 앱 교환은 그대로.
- Apple Developer 에서 Services ID(com.hongikon.web)를 만들고 Sign in with Apple 켜기·Primary App ID com.hongikon.app·도메인 hongikon.com·Return URL 등록이 필요하다. DB 변경 없음.

## 2026-10-06 — 댓글 신고 검토 보강 (`feat/comment-moderation`, base main `0b7746f`)

- 관리자 알림 `AdminAlertType.COMMENT_FLAGGED`(data.type `ADMIN_COMMENT_FLAGGED`, `reportId`·`commentId`): 마지막 검토 뒤 첫 신고, 신고 누적 자동 숨김 때
  발행(ReportCommentService 의 TODO 해소). 기존 `AdminAlertThrottle` 로 종류별 120초 묶음("신고된 댓글 N건 검토 필요").
  본문은 제보 제목만 — 댓글 내용·신고자·사유는 싣지 않는다. `AdminAlertEvent` 에 `commentId`·`autoHidden` 을 더하고 기존 6인자 생성자는 남겼다.
- `GET /admin/comments?filter=flagged`: 검토 뒤 신고가 있는 공개·자동 숨김 댓글(작성자가 지운 것 제외), 최근 신고 순 200건. 항목은 관리자 댓글 응답 필드
  (표시 이름·회원 번호만) + `reportTitle`·`reportStatus`·`pendingFlagCount`·`lastFlaggedAt`. 쿼리 4번(목록·수·사유별·검토 뒤 신고), N+1 없음.
  `GET /admin/overview` 에 `comments.flaggedPending` 추가.
- 검토 완료(유지): 별도 API 없이 기존 `PATCH /admin/comments/{id}` 에 `status=VISIBLE`(이미 공개 중) — 원래도 `reviewedAt` 을 남기므로 그 전 신고는
  자동 숨김·목록에서 빠진다. 문서화와 테스트만 더했다.
- 작성자 알림(이용약관 제10조): 관리자 숨김·삭제, 자동 숨김이 커밋된 뒤 `ReportCommentModeratedEvent` → `CommentModerationPushDispatcher`(@Async, AFTER_COMMIT)가
  "댓글이 운영 정책에 따라 숨겨졌어요/삭제됐어요" + 사유(관리자 `reason`, 없으면 "운영 정책 위반"; 자동 숨김은 "운영진 확인 전까지") + 14일 이의 제기 안내.
  data.type `COMMENT_MODERATED`(`reportId`·`commentId`·`status`). 제보 결과 알림과 같은 `report_status_enabled` 설정을 따른다(끄면 안 감).
  자동 숨김된 댓글을 관리자가 숨김으로 확정하면 사유와 함께 한 번 더 알린다. 작성자가 스스로 지운 댓글·같은 상태 재지정·복원은 알리지 않는다.
  PATCH 에 `reason`(선택, 200자) 추가 — DB 에 남기지 않고 로그에도 "있음/없음"만 남긴다(개인정보).
- 신고 빈도 제한: `CommentFlagLimiter`(SlidingWindowRateLimiter, 10분 10번, `report.comment.flag-rate-per-10-minutes`) → 429
  "신고를 너무 자주 하고 있어요. 잠시 뒤에 다시 시도해 주세요." 검증을 다 통과한 신고만 센다(잘못된 사유·중복 409 는 안 깎음). 서버 메모리 기준.
- `CommentResponse.flaggedByMe`: 목록·답글 페이지마다 쿼리 1번(`findFlaggedCommentIds`)으로 붙인다. 자리 표시·게스트는 false.
- 신고 API 가 제보 공개 여부를 확인한다(`requireVisibleReport` — 비공개 제보 댓글은 404). 잘못된 사유 400 메시지에 받은 값을 되비추지 않는다.
- 테스트: `CommentModerationIntegrationTest`(신규 7개), `AdminAlertDispatcherTest` 2개 추가. 전체 479개 통과.
- DB 변경 없음(새 컬럼·테이블 없음, 기존 `reviewed_at`·`report_comment_flags.created_at` 사용) — 배포 전 SQL 없음.

