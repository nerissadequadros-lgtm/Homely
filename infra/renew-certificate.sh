#!/usr/bin/env bash
set -euo pipefail
if openssl x509 -checkend 2592000 -noout -in /etc/homely/server.crt; then exit 0; fi
openssl x509 -req -in /etc/homely/server.csr -CA /etc/homely/ca.crt -CAkey /etc/homely/ca.key -CAserial /etc/homely/ca.srl -out /etc/homely/server.crt.new -days 365 -sha256 -extfile /etc/homely/server.ext
openssl verify -CAfile /etc/homely/ca.crt /etc/homely/server.crt.new
cp -a /etc/homely/server.crt /etc/homely/server.crt.previous
mv /etc/homely/server.crt.new /etc/homely/server.crt
nginx -t
systemctl reload nginx
