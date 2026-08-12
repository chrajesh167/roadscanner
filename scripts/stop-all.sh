#!/usr/bin/env bash
#
# Stops everything ./scripts/start-all.sh started.
#
# Usage:
#   ./scripts/stop-all.sh              stop services + UI, leave containers running
#   ./scripts/stop-all.sh --all        also stop the infrastructure containers
#   ./scripts/stop-all.sh --wipe       also stop containers AND delete their volumes
#
# --wipe destroys every local database. It is the right tool for "start clean", and the wrong
# tool for anything else, so it asks first.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$REPO_ROOT/logs"

MODE="${1:-}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'; BOLD=$'\033[1m'; NC=$'\033[0m'
info() { echo "${BOLD}==>${NC} $*"; }
ok()   { echo "  ${GREEN}✓${NC} $*"; }
warn() { echo "  ${YELLOW}!${NC} $*"; }

NAMES=(auth-service search-service provider-integration-service inventory-service
       booking-service payment-service notification-service customer-web)
PORTS=(8081 8082 8083 8084 8085 8086 8087 5173)

info "Stopping application processes"
for name in "${NAMES[@]}"; do
  pidfile="$LOG_DIR/$name.pid"
  [[ -f "$pidfile" ]] || continue
  pid="$(cat "$pidfile")"
  if kill -0 "$pid" 2>/dev/null; then
    # Kill the process group: mvnw forks a child JVM and npm forks next, so signalling only the
    # recorded pid leaves the actual server holding its port.
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    ok "$name (pid $pid) signalled"
  fi
  rm -f "$pidfile"
done

sleep 3

# A JVM that ignored SIGTERM still owns its port, and the next start-all would refuse to run.
info "Releasing ports"
for port in "${PORTS[@]}"; do
  pids="$(lsof -ti tcp:"$port" 2>/dev/null)"
  if [[ -n "$pids" ]]; then
    echo "$pids" | xargs kill -9 2>/dev/null
    ok "port $port released"
  fi
done

case "$MODE" in
  --all)
    info "Stopping infrastructure containers"
    docker compose -f "$REPO_ROOT/docker-compose.yml" down >/dev/null 2>&1
    ok "containers stopped (volumes kept — your data is intact)"
    ;;
  --wipe)
    echo
    warn "${RED}This deletes every local database volume (auth, search, provider, inventory, booking, payment, notification).${NC}"
    read -r -p "  Type 'wipe' to confirm: " confirm
    if [[ "$confirm" == "wipe" ]]; then
      docker compose -f "$REPO_ROOT/docker-compose.yml" down -v >/dev/null 2>&1
      ok "containers stopped and volumes deleted"
    else
      warn "aborted — nothing was deleted"
    fi
    ;;
  "")
    warn "infrastructure containers left running (use --all to stop them)"
    ;;
  *)
    warn "unknown option '$MODE' — expected --all or --wipe"
    ;;
esac

echo
ok "Done."
