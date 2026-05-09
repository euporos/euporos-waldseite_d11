#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

CONFIG_PATH="${CONFIG_PATH:-/home/phylax/projects/waldseite/directus_config}"
export CONFIG_PATH

# Previous HEAD passed by deploy-prod for rollback support
PREV_HEAD="${1:-}"

echo "=== Waldseite Deploy ==="
echo "Project: $PROJECT_DIR"
if [[ -n "$PREV_HEAD" ]]; then
    echo "Rollback target: ${PREV_HEAD:0:12}"
fi
echo ""

# Roll back to previous HEAD, reinstall deps, and rebuild
rollback() {
    if [[ -z "$PREV_HEAD" ]]; then
        echo "!!! No rollback target — aborting without rollback ==="
        return
    fi
    echo ""
    echo "!!! Rolling back to ${PREV_HEAD:0:12} ==="
    git reset --hard "$PREV_HEAD"
    npm install
    (cd directus && npm install)
    nix run .#build-directus-extensions
    nix run .#build
    echo "!!! Previous version restored ==="
}

# Step 1: Install dependencies
echo "--- npm install (app) ---"
if ! npm install; then
    rollback
    exit 1
fi

echo "--- npm install (directus) ---"
if ! (cd directus && npm install); then
    rollback
    exit 1
fi

# Step 2: Build Directus extensions
echo "--- Build Directus extensions ---"
if ! nix run .#build-directus-extensions; then
    rollback
    exit 1
fi

# Step 3: Prefetch Clojure git deps (uses SSH agent forwarding from deploy session)
echo "--- Prefetch Clojure deps ---"
if ! clj -P; then
    rollback
    exit 1
fi

# Step 4: Production build
echo "--- Build ---"
if ! nix run .#build; then
    rollback
    exit 1
fi

# Step 5: Stop services (brief downtime starts)
echo "--- Stopping services ---"
sudo systemctl stop waldseite.service
sudo systemctl stop waldseite-directus.service

# Step 6: Apply Directus schema (idempotent, database is quiescent)
# Schema apply runs outside the systemd unit, so source the same three-layer
# env chain (app-invariant → server-tracked → secrets) the unit gets via
# EnvironmentFile. The server-tracked file lives in the netcup-vps-2 flake
# checkout at /etc/nixos/.
echo "--- Schema apply ---"
(
  set -a
  # shellcheck disable=SC1091
  . directus/.env.public
  # shellcheck disable=SC1091
  . /etc/nixos/waldseite/directus.env
  # shellcheck disable=SC1091
  . /home/phylax/projects/waldseite/directus_config
  set +a
  cd directus && npx directus schema apply --yes ../schema/snapshot.json
)

# Step 7: Start services (downtime ends)
echo "--- Starting services ---"
sudo systemctl start waldseite-directus.service
sleep 3
sudo systemctl start waldseite.service

echo "=== Deploy complete ==="
