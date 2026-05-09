#!/usr/bin/env bash
set -euo pipefail

# Push the local Postgres `directus` DB to production.
# Destructive on prod: replaces all data in the prod directus database.
#
# Local connection: PGHOST/PGPORT/PGUSER/PGDATABASE from the devShell pgEnvHook.
# Prod connection: host/port/db/user are hardcoded here to the values that
# also live in the per-server file at $REMOTE_SECRET_ENV (which we source on
# the VPS to pick up DB_PASSWORD).

SERVER="phylax@netcup-vps-2-arm"
REMOTE_SECRET_ENV="/home/phylax/projects/waldseite/directus_config"
PROD_HOST="127.0.0.1"
PROD_PORT="5432"
PROD_DB="waldseite_directus"
PROD_USER="directus"

DUMP_LOCAL="/tmp/waldseite-directus-push-$(date -u +%Y%m%dT%H%M%SZ).sql"
DUMP_REMOTE="/tmp/$(basename "$DUMP_LOCAL")"

LOCAL_DB="${PGDATABASE:-directus}"

echo "=== Push local DB → production ==="
echo "  Source: ${PGUSER:-?}@${PGHOST:-?}:${PGPORT:-?}/$LOCAL_DB"
echo "  Target: $SERVER → $PROD_USER@$PROD_HOST:$PROD_PORT/$PROD_DB"
echo ""
echo "This will WIPE the production database '$PROD_DB' on $SERVER and replace"
echo "its contents with your local '$LOCAL_DB' DB. Production Directus will be"
echo "briefly stopped."
echo ""
read -r -p "Type 'yes' to continue: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 1; }

echo "--- Dumping local DB → $DUMP_LOCAL ---"
pg_dump --no-owner --no-privileges --clean --if-exists \
        --dbname="${PGDATABASE:-directus}" \
        > "$DUMP_LOCAL"

echo "--- Uploading dump ---"
scp "$DUMP_LOCAL" "$SERVER:$DUMP_REMOTE"

echo "--- Restoring on production ---"
ssh "$SERVER" bash -s "$DUMP_REMOTE" "$REMOTE_SECRET_ENV" "$PROD_HOST" "$PROD_PORT" "$PROD_USER" "$PROD_DB" <<'REMOTE'
set -euo pipefail
DUMP="$1"
ENV_FILE="$2"
export PGHOST="$3"
export PGPORT="$4"
export PGUSER="$5"
TARGET_DB="$6"

# Source the secret-only env file (same approach as schema-apply) and
# pick up DB_PASSWORD from it.
[ -f "$ENV_FILE" ] || { echo "Missing env file: $ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
[[ -n "${DB_PASSWORD:-}" ]] || { echo "DB_PASSWORD not set after sourcing $ENV_FILE" >&2; exit 1; }
export PGPASSWORD="$DB_PASSWORD"

echo "    Stopping waldseite-directus.service + waldseite.service"
# Both services hold connections to $TARGET_DB; either one would block
# DROP SCHEMA public CASCADE.
sudo systemctl stop waldseite-directus.service
sudo systemctl stop waldseite.service

echo "    Resetting schema 'public' in $TARGET_DB"
# Drop and recreate the schema rather than the DB, since the directus role
# owns the schema but lacks CREATEDB. All Directus tables live in `public`.
psql -v ON_ERROR_STOP=1 -d "$TARGET_DB" <<SQL
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public AUTHORIZATION "$PGUSER";
GRANT ALL ON SCHEMA public TO "$PGUSER";
GRANT USAGE ON SCHEMA public TO PUBLIC;
SQL

echo "    Restoring dump"
psql -v ON_ERROR_STOP=1 -d "$TARGET_DB" -f "$DUMP"

echo "    Verifying restore"
COUNT=$(psql -tAq -d "$TARGET_DB" -c "SELECT count(*) FROM directus_migrations")
if ! [[ "$COUNT" =~ ^[0-9]+$ && "$COUNT" -gt 0 ]]; then
  echo "ERROR: directus_migrations is empty or missing after restore" >&2
  echo "       Leaving waldseite-directus.service stopped." >&2
  exit 1
fi
echo "    directus_migrations: $COUNT rows"

rm -f "$DUMP"

echo "    Starting waldseite-directus.service + waldseite.service"
sudo systemctl start waldseite-directus.service
sleep 3
sudo systemctl start waldseite.service
REMOTE

rm -f "$DUMP_LOCAL"
echo "=== Done ==="
