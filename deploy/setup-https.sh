#!/usr/bin/env bash
# api.hongikon.com 에 Nginx 프록시 + Let's Encrypt 인증서를 적용한다. EC2에서 실행한다.
#
#   사전 조건: 도메인 DNS에 A 레코드 api.hongikon.com → 이 서버 Elastic IP 가 반영돼 있을 것
#   사용법:    sudo EMAIL=you@example.com bash deploy/setup-https.sh
#
# 여러 번 돌려도 안전하다(설정 덮어쓰기 + 이미 발급된 인증서는 certbot이 재사용).
set -euo pipefail

DOMAIN="${DOMAIN:-api.hongikon.com}"
EMAIL="${EMAIL:?인증서 만료 알림을 받을 EMAIL 환경변수를 지정하세요}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE=/etc/nginx/sites-available/hongikon-api

if [[ $EUID -ne 0 ]]; then
  echo "root 권한이 필요합니다: sudo EMAIL=... bash $0" >&2
  exit 1
fi

# DNS가 아직 이 서버를 안 가리키면 certbot 검증이 실패하고, 반복 실패는 Let's Encrypt 발급 제한에 걸린다.
PUBLIC_IP="$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')"
RESOLVED_IP="$(getent ahostsv4 "$DOMAIN" | awk 'NR==1 {print $1}')"
if [[ "$RESOLVED_IP" != "$PUBLIC_IP" ]]; then
  echo "DNS 미반영: $DOMAIN → '${RESOLVED_IP:-없음}', 이 서버 공인 IP → $PUBLIC_IP" >&2
  echo "도메인 관리 화면에서 A 레코드를 확인하고, 반영된 뒤 다시 실행하세요." >&2
  exit 1
fi

# 백엔드가 떠 있어야 프록시 검증이 의미가 있다.
curl -fsS -o /dev/null http://127.0.0.1:8080/status || {
  echo "localhost:8080/status 응답 없음 — 백엔드 컨테이너 상태를 먼저 확인하세요 (docker ps)." >&2
  exit 1
}

apt-get update -qq
apt-get install -y -qq certbot python3-certbot-nginx

sed "s/api\.hongikon\.com/${DOMAIN}/g" "$SCRIPT_DIR/nginx/hongikon-api.conf" > "$SITE"
ln -sf "$SITE" /etc/nginx/sites-enabled/hongikon-api
nginx -t
systemctl reload nginx

certbot --nginx -d "$DOMAIN" \
  --non-interactive --agree-tos -m "$EMAIL" \
  --redirect --keep-until-expiring

nginx -t
systemctl reload nginx

# 갱신은 certbot 패키지의 systemd 타이머가 하루 두 번 확인한다. 드라이런으로 갱신 경로만 점검.
certbot renew --dry-run --quiet

curl -fsS "https://${DOMAIN}/status" && echo && echo "✓ https://${DOMAIN} 적용 완료"
