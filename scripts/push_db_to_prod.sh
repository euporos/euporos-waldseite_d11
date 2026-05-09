#!/usr/bin/env bash
set -euo pipefail

# Push the local Postgres `directus` DB to production.
# Destructive on prod: replaces all data in the prod directus database.
#
# Local connection: PGHOST/PGPORT/PGUSER/PGDATABASE from the devShell pgEnvHook.
# Prod connection: host/port/db/user are tracked in the server flake at
# netcup-vps-2/waldseite/directus.env (so we hardcode them here to match);
# DB_PASSWORD is the only secret and is read from the untracked .env on the VPS.

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

# Read only the password from the secret-only env file.
PGPASSWORD="$(grep -E "^DB_PASSWORD=" "$ENV_FILE" | head -n1 | cut -d= -f2- | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/")"
[[ -n "$PGPASSWORD" ]] || { echo "Missing DB_PASSWORD in $ENV_FILE" >&2; exit 1; }
export PGPASSWORD

echo "    Stopping waldseite-directus.service"
sudo systemctl stop waldseite-directus.service

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

echo "    Starting waldseite-directus.service"
sudo systemctl start waldseite-directus.service
REMOTE

rm -f "$DUMP_LOCAL"
echo "=== Done ==="
