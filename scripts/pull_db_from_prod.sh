#!/usr/bin/env bash
set -euo pipefail

# Pull production Postgres `directus` DB → local devShell Postgres.
# Destructive on local: resets the `public` schema in $PGDATABASE.
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

DUMP_REMOTE="/tmp/waldseite-directus-clone-$(date -u +%Y%m%dT%H%M%SZ).sql"
DUMP_LOCAL="/tmp/$(basename "$DUMP_REMOTE")"

LOCAL_DB="${PGDATABASE:-directus}"
LOCAL_USER="${PGUSER:-directus}"

echo "=== Pull production DB → local ==="
echo "  Source: $SERVER → $PROD_USER@$PROD_HOST:$PROD_PORT/$PROD_DB"
echo "  Target: ${PGUSER:-?}@${PGHOST:-?}:${PGPORT:-?}/$LOCAL_DB"
echo ""
echo "This will DROP schema 'public' in your local '$LOCAL_DB' DB and replace"
echo "it with the contents of production."
echo ""
read -r -p "Type 'yes' to continue: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 1; }

echo "--- Dumping production DB on $SERVER → $DUMP_REMOTE ---"
ssh "$SERVER" bash -s "$REMOTE_SECRET_ENV" "$DUMP_REMOTE" "$PROD_HOST" "$PROD_PORT" "$PROD_USER" "$PROD_DB" <<'REMOTE'
set -euo pipefail
ENV_FILE="$1"
DUMP="$2"
export PGHOST="$3"
export PGPORT="$4"
export PGUSER="$5"
SOURCE_DB="$6"

PGPASSWORD="$(grep -E "^DB_PASSWORD=" "$ENV_FILE" | head -n1 | cut -d= -f2- | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/")"
[[ -n "$PGPASSWORD" ]] || { echo "Missing DB_PASSWORD in $ENV_FILE" >&2; exit 1; }
export PGPASSWORD

pg_dump --no-owner --no-privileges --clean --if-exists \
        --dbname="$SOURCE_DB" \
        > "$DUMP"
REMOTE

echo "--- Downloading dump ---"
scp "$SERVER:$DUMP_REMOTE" "$DUMP_LOCAL"
ssh "$SERVER" "rm -f '$DUMP_REMOTE'"

echo "--- Resetting schema 'public' in local $LOCAL_DB ---"
psql -v ON_ERROR_STOP=1 -d "$LOCAL_DB" <<SQL
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public AUTHORIZATION "$LOCAL_USER";
GRANT ALL ON SCHEMA public TO "$LOCAL_USER";
GRANT USAGE ON SCHEMA public TO PUBLIC;
SQL

echo "--- Restoring dump into local $LOCAL_DB ---"
psql -v ON_ERROR_STOP=1 -d "$LOCAL_DB" -f "$DUMP_LOCAL" >/dev/null

echo "--- Reassigning public-schema ownership to '$LOCAL_USER' ---"
# The dump was created with --no-owner, so restored objects are owned by the
# devShell superuser ($PGUSER). Directus connects as $LOCAL_USER and would
# otherwise hit "permission denied". Rewrite owners and grant defaults.
psql -v ON_ERROR_STOP=1 -d "$LOCAL_DB" <<SQL
ALTER SCHEMA public OWNER TO "$LOCAL_USER";
DO \$\$
DECLARE r record;
BEGIN
  FOR r IN SELECT tablename  FROM pg_tables   WHERE schemaname='public' LOOP
    EXECUTE format('ALTER TABLE public.%I OWNER TO %I', r.tablename, '$LOCAL_USER');
  END LOOP;
  FOR r IN SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema='public' LOOP
    EXECUTE format('ALTER SEQUENCE public.%I OWNER TO %I', r.sequence_name, '$LOCAL_USER');
  END LOOP;
  FOR r IN SELECT viewname  FROM pg_views    WHERE schemaname='public' LOOP
    EXECUTE format('ALTER VIEW public.%I OWNER TO %I', r.viewname, '$LOCAL_USER');
  END LOOP;
  FOR r IN SELECT matviewname FROM pg_matviews WHERE schemaname='public' LOOP
    EXECUTE format('ALTER MATERIALIZED VIEW public.%I OWNER TO %I', r.matviewname, '$LOCAL_USER');
  END LOOP;
  -- Enums and domains only; skip composite types because table row types
  -- (typtype='c', typrelid<>0) must be re-owned via ALTER TABLE, not ALTER TYPE.
  FOR r IN SELECT t.typname
           FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
           WHERE n.nspname='public' AND t.typtype IN ('e','d') AND t.typrelid = 0 LOOP
    EXECUTE format('ALTER TYPE public.%I OWNER TO %I', r.typname, '$LOCAL_USER');
  END LOOP;
END
\$\$;
GRANT ALL ON SCHEMA public TO "$LOCAL_USER";
SQL

rm -f "$DUMP_LOCAL"
echo "=== Done ==="
