{ pkgs, flake-utils, commonPackages }:

let
  mkApp = name: text: flake-utils.lib.mkApp {
    drv = pkgs.writeShellApplication {
      inherit name text;
      runtimeInputs = commonPackages ++ [ pkgs.zip ];
    };
  };
in
{
  # Launch all dev processes via process-compose
  dev = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "festival-dev" ''
      exec nix develop .#default --command ${pkgs.process-compose}/bin/process-compose up -f process-compose.yaml
    '';
  };

  # Run just the Macchiato app server in prod mode (NODE_ENV=production from
  # the prod devShell). Mirrors what waldseite.service does on the VPS — useful
  # for monkey-patching prod behavior locally without launching as a service.
  # Assumes a build has already happened (`nix run .#build`); reads settings.edn
  # the same way the systemd unit does.
  prod = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "waldseite-prod" ''
      exec nix develop .#prod --command node server/server.js
    '';
  };

  # Build all Directus extensions
  build-directus-extensions = mkApp "festival-build-directus-extensions" ''
    for ext in directus/extensions/directus-extension-*/; do
      if [ -f "$ext/package.json" ]; then
        echo "Building extension: $ext"
        (cd "$ext" && npx --package=@directus/extensions-sdk directus-extension build)
      fi
    done
  '';

  # Initial project setup
  setup = mkApp "festival-setup" ''
    npm install
    (cd directus && npm install)
    nix run .#build-directus-extensions
    mkdir -p export public public/from_reservoir
  '';

  # Process and resize reservoir images
  process-reservoir = mkApp "festival-process-reservoir" ''
    clj -X:process-reservoir
    bash scripts/resize_images.sh reservoir/imgs/*.JPG
  '';

  # Full production build
  build = mkApp "festival-build" ''
    export NODE_ENV=production
    rm -rf .shadow-cljs
    rm -rf public/compiled/*
    clj -X:gen-directus-links
    clj -X:process-reservoir
    bash scripts/resize_images.sh reservoir/imgs/*.JPG
    npx vite build
    npx shadow-cljs release browser admin server
  '';

  # Create deployable export directory
  export = mkApp "festival-export" ''
    export NODE_ENV=production
    rm -rf export
    mkdir export
    rm -rf .shadow-cljs
    rm -rf public/compiled/*
    clj -X:gen-directus-links
    clj -X:process-reservoir
    bash scripts/resize_images.sh reservoir/imgs/*.JPG
    npx vite build
    cp -r node_modules export/node_modules
    cp -r resources export/resources
    cp package.json export/
    cp package-lock.json export/
    npx shadow-cljs release browser admin server
    cp -r public export/public
    rm -rf export/public/js/compiled/cljs-runtime
    cp server/server.js export/
    cp settings.edn export/
    chmod -R 755 export
  '';

  # Export with staging configuration
  export-stage = mkApp "festival-export-stage" ''
    export MCT_CONFIG_DIRS="../config/prod ../config/stage"
    export NODE_ENV=production
    rm -rf export
    mkdir export
    rm -rf .shadow-cljs
    rm -rf public/compiled/*
    clj -X:gen-directus-links
    clj -X:process-reservoir
    bash scripts/resize_images.sh reservoir/imgs/*.JPG
    npx vite build
    cp -r node_modules export/node_modules
    cp -r resources export/resources
    cp package.json export/
    cp package-lock.json export/
    npx shadow-cljs release browser admin server
    cp -r public export/public
    rm -rf export/public/js/compiled/cljs-runtime
    cp server/server.js export/
    cp settings.edn export/
    chmod -R 755 export
  '';

  # Zip the export directory for upload
  zip = mkApp "festival-zip" ''
    cd export
    zip -r ../upload.zip ./*
  '';

  # Run tests (expects MariaDB already running, e.g. via `nix run .#dev`)
  test = mkApp "festival-test" ''
    export CHROME_BINARY_PATH=${pkgs.chromium}/bin/chromium
    npx shadow-cljs release server browser admin
    clj -M:test -m cognitect.test-runner
  '';

  # Initialize local dev database and directus user
  init-db = mkApp "festival-init-db" ''
    export MYSQL_UNIX_PORT="$PWD/.nix-shell/mysql/mysql.sock"
    bash scripts/init_db.sh
  '';

  # Initialize local Postgres dev database and directus role.
  # Wrapped in `nix develop` so PGHOST/PGPORT/PGUSER/PGDATABASE from the
  # pgEnvHook in flake.nix are in scope — this keeps PGPORT as the single
  # source of truth for the port across dev-infra.
  init-pg-db = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "festival-init-pg-db" ''
      exec nix develop .#default --command bash scripts/init_pg_db.sh
    '';
  };

  # Rebuild local Postgres Directus DB from the MariaDB prod clone via pgloader.
  # Wrapped in `nix develop` so PGHOST/PGPORT/... from pgEnvHook are in scope.
  pg-migrate = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "festival-pg-migrate" ''
      exec nix develop .#default --command bash scripts/pg_migrate.sh
    '';
  };

  # One-time bootstrap of waldseite D8 dump into a fresh D11 + Postgres.
  # After this, do the manual UI cleanup pass and then `nix run .#schema-export`.
  # Pass an optional dump path as the first arg; otherwise the most recent
  # k113427_waldseite_*.sql.zip in the repo root is used.
  waldseite-bootstrap = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "waldseite-bootstrap" ''
      exec nix develop .#default --command bash scripts/waldseite_bootstrap.sh "$@"
    '';
  };

  # Rewrite absolute Directus URLs in WYSIWYG fields to path-only
  # `/directus/assets/<uuid>`. Idempotent. Also runs as a step inside
  # `waldseite-bootstrap`, but exposed standalone for re-runs after a
  # `pull-db-from-prod` if any new absolute URLs slipped in.
  normalize-wysiwyg = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "waldseite-normalize-wysiwyg" ''
      exec nix develop .#default --command bash scripts/normalize_wysiwyg_urls.sh
    '';
  };

  # Pull production Postgres directus DB → local devShell Postgres.
  # Wrapped in `nix develop` so PGHOST/PGPORT/... from pgEnvHook are in scope.
  pull-db-from-prod = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "festival-pull-db-from-prod" ''
      exec nix develop .#default --command bash scripts/pull_db_from_prod.sh
    '';
  };

  # Pull uploaded files from production
  pull-files = mkApp "waldseite-pull-files" ''
    bash scripts/pull_files.sh
  '';

  # Push local uploaded files to production
  push-files = mkApp "waldseite-push-files" ''
    bash scripts/push_files.sh
  '';

  # Push local Postgres directus DB to production (destructive: replaces prod DB).
  # Wrapped in `nix develop` so PGHOST/PGPORT/... from pgEnvHook resolve locally.
  # Requires NOPASSWD sudo on the server for `systemctl start/stop` of
  # festival-directus.service — declared in the server flake at
  # ../servers/vps2/netcup-vps-2/configuration.nix (security.sudo.extraConfig).
  push-db-to-prod = flake-utils.lib.mkApp {
    drv = pkgs.writeShellScriptBin "festival-push-db-to-prod" ''
      exec nix develop .#default --command bash scripts/push_db_to_prod.sh
    '';
  };

  # Generate Directus presentation-links from route metadata
  gen-directus-links = flake-utils.lib.mkApp {
    drv = pkgs.writeShellApplication {
      name = "festival-gen-directus-links";
      text = ''
        clj -X:gen-directus-links
        jq . schema/snapshot.json > schema/snapshot.json.tmp && mv schema/snapshot.json.tmp schema/snapshot.json
      '';
      runtimeInputs = commonPackages ++ [ pkgs.jq ];
    };
  };

  # Export Directus schema snapshot (requires running MariaDB + Directus)
  schema-export = flake-utils.lib.mkApp {
    drv = pkgs.writeShellApplication {
      name = "festival-schema-export";
      text = ''
        mkdir -p schema
        cd directus && npx directus schema snapshot --format json ../schema/snapshot.json
        cd ..
        jq . schema/snapshot.json > schema/snapshot.json.tmp && mv schema/snapshot.json.tmp schema/snapshot.json
        clj -X:gen-directus-links
        jq . schema/snapshot.json > schema/snapshot.json.tmp && mv schema/snapshot.json.tmp schema/snapshot.json
        echo "Schema exported and presentation-links updated."
      '';
      runtimeInputs = commonPackages ++ [ pkgs.jq ];
    };
  };

  # Apply schema snapshot to current Directus instance.
  # In dev, Directus auto-loads directus/.env from cwd. In prod, env comes
  # from the same two-layer chain (app-invariant + per-server file) the
  # systemd unit gets via EnvironmentFile — sourced here so this app works
  # whether invoked manually on the server or from redeploy.sh.
  schema-apply = mkApp "festival-schema-apply" ''
    set -a
    # shellcheck disable=SC1091
    [ -f directus/.env.public ] && . directus/.env.public
    # shellcheck disable=SC1091
    [ -f /home/phylax/projects/waldseite/directus_config ] && . /home/phylax/projects/waldseite/directus_config
    set +a
    cd directus && npx directus schema apply --yes ../schema/snapshot.json
  '';

  # Upgrade psite libs to latest main and prefetch deps
  upgrade-psite = flake-utils.lib.mkApp {
    drv = pkgs.writeShellApplication {
      name = "festival-upgrade-psite";
      runtimeInputs = commonPackages ++ [ pkgs.git ];
      text = ''
        PSITE_DIR="$(cd ../../psite && pwd)"

        if [ ! -d "$PSITE_DIR/.git" ]; then
          echo "Error: psite repo not found at $PSITE_DIR" >&2
          exit 1
        fi

        echo "--- Pulling psite ---"
        git -C "$PSITE_DIR" pull

        NEW_SHA=$(git -C "$PSITE_DIR" rev-parse HEAD)
        OLD_SHA=$(grep -oE 'euporos/psite\.git" :git/sha "[0-9a-f]+' deps.edn \
                  | grep -oE '[0-9a-f]{40}' | head -1)

        if [ -z "$OLD_SHA" ]; then
          echo "Error: could not find current psite SHA in deps.edn" >&2
          exit 1
        fi

        if [ "$OLD_SHA" = "$NEW_SHA" ]; then
          echo "Already at latest: ''${NEW_SHA:0:12}"
          exit 0
        fi

        sed -i "s/$OLD_SHA/$NEW_SHA/g" deps.edn
        echo "Updated psite: ''${OLD_SHA:0:12} → ''${NEW_SHA:0:12}"

        echo "--- Prefetching deps ---"
        clj -P
      '';
    };
  };

  # Deploy waldseite from dev machine.
  # Server checkout lives at /home/phylax/projects/waldseite/app; systemd units
  # are waldseite / waldseite-directus on :7202/:7203. Currently deploys to the
  # staging domain waldseite.olivermotz.net (nginx adds noindex headers); when
  # the migration is judged complete, the server-side nginx vhost is flipped to
  # the real waldseite.de domain — checkout, services, and this app stay put.
  deploy-prod = flake-utils.lib.mkApp {
    drv = pkgs.writeShellApplication {
      name = "waldseite-deploy-prod";
      text = ''
        SERVER="phylax@netcup-vps-2-arm"
        echo "=== Checking local main vs origin/main ==="
        git fetch origin main >/dev/null 2>&1 || true
        LOCAL_MAIN=$(git rev-parse main)
        REMOTE_MAIN=$(git rev-parse origin/main 2>/dev/null || echo "")
        if [ "$LOCAL_MAIN" != "$REMOTE_MAIN" ]; then
          echo "Local main ($LOCAL_MAIN) differs from origin/main ($REMOTE_MAIN)."
          read -r -p "Force push main to origin first? [y/N] " ANSWER
          case "$ANSWER" in
            [yY]|[yY][eE][sS])
              echo "=== Force pushing main ==="
              git push --force-with-lease origin main
              ;;
            *)
              echo "Skipping push. Server will deploy whatever is currently on origin/main."
              ;;
          esac
        else
          echo "main is up to date with origin/main."
        fi
        echo "=== Deploying waldseite ==="
        DEPLOYED_SHA=$(ssh "$SERVER" '
          set -euo pipefail
          cd /home/phylax/projects/libs && git pull >&2
          cd /home/phylax/projects/waldseite/app
          PREV_HEAD=$(git rev-parse HEAD)
          git fetch >&2 && git reset --hard origin/main >&2
          bash scripts/redeploy.sh "$PREV_HEAD" >&2
          git rev-parse HEAD
        ')
        echo "=== Tagging deployed commit $DEPLOYED_SHA ==="
        TAG="deployed-$(date -u +%Y%m%dT%H%M%SZ)"
        git tag "$TAG" "$DEPLOYED_SHA"
        git push origin "$TAG"
        echo "=== Done ($TAG) — https://waldseite.olivermotz.net ==="
      '';
      runtimeInputs = [ pkgs.openssh pkgs.git pkgs.coreutils ];
    };
  };

}
