#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
test "$(pwd -P)" = /www/wwwroot/orion-zr68
test -f compose.yaml
test ! -e .env
test -z "$(docker ps -aq --filter label=com.docker.compose.project=orion-zr68)"
umask 077
DB_PASSWORD=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -hex 48)
printf 'DB_PASSWORD=%s\nJWT_SECRET=%s\n' "$DB_PASSWORD" "$JWT_SECRET" > .env
chmod 700 .
docker compose config --quiet
printf 'Configuration validated. No services started.\n'

