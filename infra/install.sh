#!/usr/bin/env bash
set -euo pipefail
cd /opt/homely
test -d server
test -f /etc/homely/ca.crt
python3 - <<'PY'
import secrets, subprocess, pathlib
def sql(command):
 return subprocess.check_output(['sudo','-u','postgres','psql','-At','-v','ON_ERROR_STOP=1','-c',command],text=True).strip()
env=pathlib.Path('/etc/homely/api.env')
if not env.exists():
 password=secrets.token_hex(32)
 if sql("SELECT 1 FROM pg_roles WHERE rolname='homely'"):
  raise RuntimeError('homely role already exists without app configuration; inspect before continuing')
 sql("CREATE ROLE homely LOGIN PASSWORD '"+password+"'")
 sql("CREATE DATABASE homely OWNER homely")
 env.write_text('DATABASE_URL=postgresql://homely:'+password+'@127.0.0.1:5432/homely\nHOST=127.0.0.1\nPORT=3187\nPUBLIC_URL=https://82.29.61.16:8443\nSTORAGE_DIR=/var/lib/homely/media\nLOG_LEVEL=info\nNODE_ENV=production\n')
 env.chmod(0o600)
PY
set -a
source /etc/homely/api.env
set +a
cd server
npm ci --include=dev --no-audit --no-fund
npm run build
npm run migrate
npm run seed
chown -R homely:homely /var/lib/homely
cd ..
install -m 644 infra/homely.service /etc/systemd/system/homely.service
install -m 644 infra/nginx-homely.conf /etc/nginx/sites-available/homely
ln -sfn /etc/nginx/sites-available/homely /etc/nginx/sites-enabled/homely
nginx -t
systemctl daemon-reload
systemctl enable --now homely
systemctl restart homely
systemctl reload nginx
ufw allow 8443/tcp comment 'Homely HTTPS'
install -m 750 infra/backup.sh /opt/homely/backup.sh
install -m 750 infra/renew-certificate.sh /opt/homely/renew-certificate.sh
install -m 644 infra/homely-backup.service infra/homely-backup.timer infra/homely-cert.service infra/homely-cert.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now homely-backup.timer homely-cert.timer
for attempt in 1 2 3 4 5; do curl --fail --silent --cacert /etc/homely/ca.crt https://82.29.61.16:8443/health && break || sleep 2; done
