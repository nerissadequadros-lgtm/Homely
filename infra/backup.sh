#!/usr/bin/env bash
set -euo pipefail
umask 077
target="/var/backups/homely/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$target"
sudo -u postgres pg_dump -Fc homely > "$target/database.dump"
tar -czf "$target/media.tar.gz" -C /var/lib/homely media
tar -czf "$target/config.tar.gz" -C /etc homely
# Retention is deliberately manual until an off-host destination is configured.
find /var/backups/homely -maxdepth 1 -type d -name '20*' -printf '%f\n'
