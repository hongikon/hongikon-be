# 백엔드 HTTPS 적용 안내 (EC2에서 실행)

**대상:** EC2(`hongikon-be-server`, `54.180.195.51`)에 SSH로 접속할 수 있는 사람.
**목적:** `api.hongikon.com`에 HTTPS(Let's Encrypt)를 적용한다. 지금은 `http://api.hongikon.com`만
응답하고 `https://api.hongikon.com`은 443 포트 자체가 막혀 있어(Connection refused) 연결이
거부되는 상태다 — 즉 프로덕션 앱은 백엔드에 아예 붙지 못한다. 아래 절차로 이걸 연다.

## 0. 사전 조건 (이미 완료됨, 확인만)

- [x] `api.hongikon.com` DNS A 레코드가 이 서버 IP(`54.180.195.51`)를 가리키도록 반영 완료
  (2026-09-23 확인: `dig +short api.hongikon.com` → `54.180.195.51`)
- [x] 백엔드 컨테이너가 이미 떠서 `http://api.hongikon.com/status`가 200을 반환 중

## 1. EC2 접속

기존에 쓰던 방식대로 SSH 접속 (pem 키 위치는 팀 내 공유된 걸 사용):

```bash
ssh -i <키파일 경로> ubuntu@54.180.195.51
```

## 2. 백엔드 저장소를 최신으로

```bash
cd ~/hongikon-be
git pull
```

(원격 저장소는 이미 최신 상태 — 로컬에서 확인함, 새로 당겨올 커밋 없을 수도 있음. 그래도 실행해서 확인)

## 3. 백엔드가 떠 있는지 확인

```bash
docker ps
curl -fsS http://127.0.0.1:8080/status
```

응답이 없으면 스크립트가 여기서 바로 실패한다 — 컨테이너부터 살린다.

## 4. HTTPS 스크립트 실행

```bash
sudo EMAIL=<인증서 만료 알림 받을 이메일> bash deploy/setup-https.sh
```

이 스크립트(`deploy/setup-https.sh`)가 하는 일 (몇 번을 다시 돌려도 안전):
1. DNS가 이 서버를 가리키는지 재확인 (`checkip.amazonaws.com`으로 공인 IP 대조) — 다르면 여기서 중단
2. `certbot` 설치
3. `deploy/nginx/hongikon-api.conf`를 `/etc/nginx/sites-available/hongikon-api`로 설치 + 활성화
4. 배포 초기에 만들어둔 "모든 Host → 8080" 기본 사이트(`sites-enabled/default`) 비활성화
   (IP로 직접 접속하거나 Host 헤더를 위조해서 위 설정을 우회하는 걸 막기 위함)
5. `certbot --nginx -d api.hongikon.com`으로 인증서 발급 + 자동 갱신 등록 + HTTP→HTTPS 리다이렉트 + HSTS
6. 갱신 드라이런으로 자동 갱신 경로 점검
7. 8080 포트가 `127.0.0.1`이 아니라 외부에 열려 있으면 경고 출력 (`-p 127.0.0.1:8080:8080`으로
   컨테이너를 다시 띄우는 걸 권장 — 지금은 보안그룹이 막아주고 있지만 방어를 겹치는 목적)
8. 마지막에 `https://api.hongikon.com/status` 호출해서 성공 여부 출력

## 5. 완료 확인 (내 컴퓨터/아무 데서나)

```bash
curl -s https://api.hongikon.com/status
```

200과 함께 상태 응답이 오면 끝. (이 시점부터 프로덕션 앱 빌드가 백엔드에 정상 연결된다.)

## 6. ⚠️ 이어서 반드시 해야 하는 것 — 카카오 개발자 콘솔

HTTPS가 열리면 카카오 로그인이 요청하는 redirect_uri가 `https://api.hongikon.com/login/oauth2/code/kakao`로
바뀐다. **카카오 개발자 콘솔(developers.kakao.com) → 내 애플리케이션 → 카카오 로그인 → Redirect URI**에
이 주소가 등록돼 있는지 확인하고, 없으면 추가한다.

- `docs/worklog.md`(2026-09-17 기록)에 따르면 지금까지는 **IP 기준 주소만**
  (`http://54.180.195.51/login/oauth2/code/kakao`) 등록돼 있고, 도메인 기준 주소 등록은
  "다음 단계"로 남아있던 채 완료 기록이 없다 — 즉 이 단계를 안 하면 로그인 시 카카오가
  `KOE006`(Redirect URI 불일치) 에러를 낸다.
- IP 기준 주소는 지우지 않아도 무방(개발용으로 유지).

## 문제 생길 때

| 증상 | 원인 | 조치 |
|---|---|---|
| 스크립트가 "DNS 미반영" 메시지로 바로 중단 | DNS 전파가 아직 이 서버까지 덜 됨 (로컬 확인과 EC2가 보는 DNS 캐시가 다를 수 있음) | 몇 분 기다렸다가 재실행 |
| "localhost:8080/status 응답 없음" | 백엔드 컨테이너가 죽어있음 | `docker ps`, `docker logs <컨테이너>`로 확인 후 재기동, 그다음 스크립트 재실행 |
| certbot 발급 실패(rate limit 등) | 같은 도메인으로 짧은 시간에 여러 번 실패 시도 | Let's Encrypt rate limit — 보통 1시간~1주 대기 필요, `certbot certificates`로 기존 발급 이력 확인 |
| 스크립트는 성공했는데 앱이 여전히 로그인 실패 | 카카오 콘솔에 도메인 기준 Redirect URI 미등록 | 위 6번 항목 진행 |
