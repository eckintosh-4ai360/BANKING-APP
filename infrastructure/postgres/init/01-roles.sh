#!/bin/bash
# Creates the PostgreSQL roles and database used by banking-core.
# Runs once, when the postgres container starts with an empty data volume.
#
#   banking_migrator  owns the schema and runs Flyway migrations
#   banking_app       runtime role: DML only, not owner, not superuser, subject to row-level security
set -euo pipefail

: "${BANKING_MIGRATOR_PASSWORD:?BANKING_MIGRATOR_PASSWORD is required}"
: "${BANKING_APP_PASSWORD:?BANKING_APP_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v migrator_password="$BANKING_MIGRATOR_PASSWORD" \
  -v app_password="$BANKING_APP_PASSWORD" <<'EOSQL'
CREATE ROLE banking_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE banking_app LOGIN PASSWORD :'app_password';
CREATE DATABASE banking OWNER banking_migrator;
REVOKE ALL ON DATABASE banking FROM PUBLIC;
GRANT CONNECT ON DATABASE banking TO banking_app;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname banking <<'EOSQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA core AUTHORIZATION banking_migrator;
EOSQL
