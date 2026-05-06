#!/usr/bin/env bash
# Load a waldseite Directus 8 SQL dump into a temp MariaDB database
# (waldseite_directus). Source for the pg_migration pgloader pass.
#
# Usage: bash scripts/load_waldseite_dump.sh [path/to/dump.sql.zip]
# Default dump: the most recent k113427_waldseite_*.sql.zip in repo root.
#
# Idempotent: drops + recreates waldseite_directus each run.
# Strips directus_* tables from the dump before loading — D11 manages those
# system tables itself, and the D8 shapes would collide with D11 bootstrap.

set -euo pipefail

DUMP_ZIP="${1:-}"
if [ -z "$DUMP_ZIP" ]; then
  DUMP_ZIP=$(ls -t k113427_waldseite_*.sql.zip 2>/dev/null | head -1 || true)
fi
if [ -z "$DUMP_ZIP" ] || [ ! -f "$DUMP_ZIP" ]; then
  echo "Error: no dump file found. Pass a path, or place k113427_waldseite_*.sql.zip in the repo root." >&2
  exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

echo "--- Unzipping $DUMP_ZIP ---"
unzip -q "$DUMP_ZIP" -d "$WORK"
RAW_SQL=$(ls "$WORK"/*.sql | head -1)

echo "--- Stripping directus_* tables from dump ---"
# Remove every block: from `-- Table structure for table \`directus_...\`` up
# to (but not including) the next `-- Table structure for table \`...\``,
# *and* the matching `-- Dumping data for table \`directus_...\`` block.
# Awk is the cleanest tool here.
FILTERED_SQL="$WORK/filtered.sql"
awk '
  /^-- Table structure for table `directus_/ ||
  /^-- Dumping data for table `directus_/ {
    skip = 1; next
  }
  /^-- Table structure for table `/ ||
  /^-- Dumping data for table `/ {
    skip = 0
  }
  !skip { print }
' "$RAW_SQL" > "$FILTERED_SQL"

echo "--- (Re)creating waldseite_directus database ---"
mysql -u root <<SQL
DROP DATABASE IF EXISTS waldseite_directus;
CREATE DATABASE waldseite_directus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

echo "--- Loading dump into MariaDB (this can take a minute) ---"
mysql -u root waldseite_directus < "$FILTERED_SQL"

TABLE_COUNT=$(mysql -u root -N -B -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='waldseite_directus';")
echo "Loaded $TABLE_COUNT user tables into waldseite_directus."
