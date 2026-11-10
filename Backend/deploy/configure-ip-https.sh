#!/usr/bin/env bash
set -euo pipefail

# Run as root on the approved Sydney EC2 host after its Elastic IP and
# conditional ports 80/443 have been enabled. The Java service stays on loopback.
ip="${1:?Usage: configure-ip-https.sh ELASTIC_IPV4}"
if [ "$(id -u)" -ne 0 ]; then
  echo 'Run as root' >&2
  exit 2
fi
python3 - "$ip" <<'PY'
import ipaddress
import sys
address = ipaddress.ip_address(sys.argv[1])
if not isinstance(address, ipaddress.IPv4Address) or not address.is_global:
    raise SystemExit('Expected a public Elastic IPv4 address')
PY

imds_token="$(curl -fsS -X PUT -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  http://169.254.169.254/latest/api/token)"
assigned_ip="$(curl -fsS -H "X-aws-ec2-metadata-token: $imds_token" \
  http://169.254.169.254/latest/meta-data/public-ipv4)"
if [ "$assigned_ip" != "$ip" ]; then
  echo 'Elastic IP does not match this instance' >&2
  exit 2
fi

dnf install -y nginx python3.13
if [ ! -x /opt/aitherapy/certbot/bin/certbot ]; then
  python3.13 -m venv /opt/aitherapy/certbot
fi
/opt/aitherapy/certbot/bin/python -m pip install --disable-pip-version-check \
  'certbot>=5.4,<6'

install -d -m 0755 /var/www/aitherapy-acme/.well-known/acme-challenge
cp -n /etc/nginx/nginx.conf /etc/nginx/nginx.conf.before-aitherapy
cat > /etc/nginx/nginx.conf <<'NGINX_HTTP'
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /run/nginx.pid;
events { worker_connections 1024; }
http {
  include /etc/nginx/mime.types;
  default_type application/octet-stream;
  server_tokens off;
  server {
    listen 80 default_server;
    location ^~ /.well-known/acme-challenge/ {
      root /var/www/aitherapy-acme;
      default_type text/plain;
      try_files $uri =404;
    }
    location / { return 308 https://CERT_IP$request_uri; }
  }
}
NGINX_HTTP
sed -i "s/CERT_IP/$ip/g" /etc/nginx/nginx.conf
nginx -t
systemctl enable --now nginx
systemctl reload nginx

certbot=/opt/aitherapy/certbot/bin/certbot
certbot_args=(certonly --non-interactive --agree-tos --register-unsafely-without-email
  --preferred-profile shortlived --webroot -w /var/www/aitherapy-acme
  --ip-address "$ip")
if [ ! -e "/etc/letsencrypt/live/$ip/fullchain.pem" ]; then
  "$certbot" "${certbot_args[@]}" --staging --cert-name "$ip-staging"
  "$certbot" "${certbot_args[@]}" --cert-name "$ip"
  "$certbot" delete --non-interactive --cert-name "$ip-staging"
fi

python3 - "$ip" <<'PY'
from pathlib import Path
import sys

ip = sys.argv[1]
path = Path('/etc/nginx/nginx.conf')
config = path.read_text()
if not config.endswith('}\n'):
    raise SystemExit('Unexpected nginx configuration')
https = '''
  server {
    listen 443 ssl default_server;
    ssl_certificate /etc/letsencrypt/live/CERT_IP/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/CERT_IP/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_tickets off;
    client_max_body_size 1m;
    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    location / {
      proxy_pass http://127.0.0.1:8080;
      proxy_http_version 1.1;
      proxy_set_header Host CERT_IP;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $remote_addr;
      proxy_set_header X-Forwarded-Proto https;
      proxy_set_header X-Forwarded-Host CERT_IP;
      proxy_set_header Connection "";
      proxy_buffering off;
      proxy_read_timeout 120s;
      proxy_send_timeout 120s;
    }
  }
'''.replace('CERT_IP', ip)
path.write_text(config[:-2] + https + '}\n')
PY
nginx -t
systemctl reload nginx

cat > /etc/systemd/system/aitherapy-cert-renew.service <<'SERVICE'
[Unit]
Description=Renew AiTherapy short-lived IP certificate
After=network-online.target nginx.service
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/opt/aitherapy/certbot/bin/certbot renew --quiet --deploy-hook /usr/bin/systemctl reload nginx
ExecStartPost=/usr/bin/openssl x509 -in /etc/letsencrypt/live/REPLACE_IP/fullchain.pem -checkend 172800 -noout
SERVICE
sed -i "s/REPLACE_IP/$ip/" /etc/systemd/system/aitherapy-cert-renew.service
cat > /etc/systemd/system/aitherapy-cert-renew.timer <<'TIMER'
[Unit]
Description=Daily AiTherapy IP certificate renewal check

[Timer]
OnCalendar=*-*-* 09:15:00 UTC
Persistent=true
RandomizedDelaySec=30m
Unit=aitherapy-cert-renew.service

[Install]
WantedBy=timers.target
TIMER
systemctl daemon-reload
systemctl enable --now aitherapy-cert-renew.timer
curl --fail --silent --show-error "https://$ip/api/v1/actuator/health/readiness" \
  | grep -q '"status":"UP"'
echo "Trusted IP HTTPS ready at https://$ip"
