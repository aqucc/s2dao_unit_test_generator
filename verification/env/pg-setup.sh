#!/usr/bin/env bash
#
# Start the local PostgreSQL 16 cluster and (re)create the smoke-test database + role.
# Idempotent: drops and recreates the role/database each run.
#
# Connection used by verification/env/smoke/dicon/app-pg.dicon:
#   jdbc:postgresql://127.0.0.1:5432/s2daosmoke   user=s2dao  password=s2dao
#
set -euo pipefail

PGVER=16
DB=s2daosmoke
USER=s2dao
PASS=s2dao

# 1) start the cluster if it is down
if ! pg_lsclusters -h 2>/dev/null | awk '{print $4}' | grep -q online; then
  pg_ctlcluster "$PGVER" main start || true
  sleep 2
fi
pg_lsclusters

# 2) (re)create role + database via peer auth as the postgres OS user
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DROP DATABASE IF EXISTS ${DB};
DROP ROLE IF EXISTS ${USER};
CREATE ROLE ${USER} LOGIN PASSWORD '${PASS}';
CREATE DATABASE ${DB} OWNER ${USER};
GRANT ALL PRIVILEGES ON DATABASE ${DB} TO ${USER};
SQL

# 3) make sure the owner can create objects in the public schema (PG15+ tightened this)
sudo -u postgres psql -d "${DB}" -v ON_ERROR_STOP=1 <<SQL
GRANT ALL ON SCHEMA public TO ${USER};
ALTER SCHEMA public OWNER TO ${USER};
SQL

# 4) verify TCP + password auth works (JDBC uses the same path)
PGPASSWORD="${PASS}" psql -h 127.0.0.1 -U "${USER}" -d "${DB}" -c "select current_user;"
echo "PostgreSQL ready: jdbc:postgresql://127.0.0.1:5432/${DB} (user=${USER})"
