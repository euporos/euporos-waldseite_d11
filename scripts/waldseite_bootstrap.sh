#!/usr/bin/env bash
# One-time bootstrap of waldseite content into a fresh Directus 11 + Postgres.
# After this completes, do the manual UI cleanup pass (translations, M2M
# junctions, M2O fields, singletons), then `nix run .#schema-export` and commit
# the resulting schema/snapshot.json. Future imports use that snapshot.
#
# Preconditions:
#   - MariaDB and Postgres are running (e.g. via `nix run .#dev`)
#   - directus/.env points at Postgres (DB_CLIENT=pg)
#   - A waldseite SQL dump zip is in the repo root (or path passed as $1)

set -euo pipefail

DUMP_ZIP="${1:-}"

# 0. Preflight
mysql -u root -e 'SELECT 1' >/dev/null
pg_isready -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" >/dev/null

# 1. Load dump into temp MariaDB
bash scripts/load_waldseite_dump.sh ${DUMP_ZIP:+"$DUMP_ZIP"}

# Stop Directus if process-compose has it running, so it doesn't fight the bootstrap.
pc_running() { process-compose process list >/dev/null 2>&1; }
if pc_running; then
  process-compose process stop directus >/dev/null 2>&1 || true
fi

# 2. Reset PG target — drop/recreate role and DB
psql -d postgres -v ON_ERROR_STOP=1 <<SQL
DROP DATABASE IF EXISTS directus;
DROP ROLE     IF EXISTS directus;
CREATE ROLE directus LOGIN PASSWORD 'password';
CREATE DATABASE directus OWNER directus;
SQL

# 3. Bootstrap Directus system tables (creates directus_*; PG admin user etc).
export DB_CLIENT=pg
export DB_HOST="$PGHOST"
export DB_PORT="$PGPORT"
export DB_DATABASE=directus
export DB_USER=directus
export DB_PASSWORD=password
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
(cd directus && npx directus bootstrap)

# 4. Pump waldseite user tables MariaDB → PG (creates tables + indexes + data)
pgloader pg_migration/waldseite.first.load

# 5. Bring Directus back up so it auto-detects the new collections.
if pc_running; then
  process-compose process start directus >/dev/null 2>&1 || true
fi

# 6. Wait for Directus to come up, then transcribe D8 metadata into D11.
echo "--- Waiting for Directus to be ready ---"
for _ in $(seq 1 30); do
  if curl -fsS http://localhost:8055/server/health >/dev/null 2>&1; then break; fi
  sleep 1
done

# Ensure the admin user exists (npx directus bootstrap only creates one when
# ADMIN_EMAIL/ADMIN_PASSWORD are set, which they are above — but if a previous
# run created the role/db without env vars, we'd be stuck without an admin).
if ! curl -fsS -X POST http://localhost:8055/auth/login \
       -H "Content-Type: application/json" \
       -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" >/dev/null 2>&1; then
  echo "--- Creating admin user $ADMIN_EMAIL ---"
  ROLE_ID=$(PGPASSWORD=password psql -h "$PGHOST" -p "$PGPORT" -U directus -d directus \
            -t -c "SELECT id FROM directus_roles LIMIT 1;" | tr -d ' \n')
  (cd directus && npx directus users create \
     --email "$ADMIN_EMAIL" --password "$ADMIN_PASSWORD" --role "$ROLE_ID" >/dev/null)
fi

echo "--- Tracking collections (icons, hidden, singleton flags) ---"
python3 scripts/track_collections.py http://localhost:8055 "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
echo "--- Tracking field metadata ---"
python3 scripts/track_fields.py http://localhost:8055 "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
echo "--- Creating M2O and M2M relations ---"
python3 scripts/track_relations.py http://localhost:8055 "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
echo "--- Creating translations relations ---"
python3 scripts/track_translations.py http://localhost:8055 "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
echo "--- Applying D8 field interface overrides (WYSIWYG, etc.) ---"
python3 scripts/track_interfaces.py "$DUMP_ZIP" http://localhost:8055 "$ADMIN_EMAIL" "$ADMIN_PASSWORD"

cat <<'EOF'

==============================================================================
waldseite_bootstrap complete.

  Login: admin@example.com / admin   (override via ADMIN_EMAIL/ADMIN_PASSWORD env)

Remaining manual work:
  - Two M2M sides to directus_files: skipped due to UUID vs integer mismatch.
    Will be wired after the file migration assigns new UUIDs to existing rows.

Once happy:
  nix run .#schema-export    # regenerate schema/snapshot.json
  git add schema/ scripts/ pg_migration/ apps.nix
  git commit
==============================================================================
EOF
