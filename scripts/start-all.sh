#!/usr/bin/env bash
#
# Starts the whole RoadScanner stack locally: infrastructure containers, every implemented
# backend service, and the customer-web UI.
#
# There is deliberately no docker-compose entry for the services themselves — docker-compose.yml
# holds infrastructure only, and each service's application-local.yml assumes "localhost" for
# Postgres/Redis/Kafka. That is the fast-iteration workflow (rebuild a service without rebuilding
# an image), so this script runs the services natively against the containers.
#
# Usage:
#   ./scripts/start-all.sh            start everything
#   ./scripts/start-all.sh --no-ui    backend only
#
# Logs:  logs/<service>.log     PIDs: logs/<service>.pid
# Stop:  ./scripts/stop-all.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$REPO_ROOT/logs"
SERVICES_DIR="$REPO_ROOT/backend/services"
UI_DIR="$REPO_ROOT/frontend/apps/customer-web"

START_UI=true
[[ "${1:-}" == "--no-ui" ]] && START_UI=false

# name:port. Order matters only for readability — Spring Boot services boot independently and
# resolve each other lazily on the first call, so none blocks on another's startup.
#
# notification-service inherits its SMTP credentials from this shell's environment
# (NOTIFICATION_EMAIL_*, see its application-local.yml). They are deliberately not set here and
# never committed. With them unset the service still starts and still consumes booking-events;
# email is then recorded FAILED in notification_log rather than silently faked.
SERVICES=(
  "auth-service:8081"
  "provider-integration-service:8083"
  "inventory-service:8084"
  "search-service:8082"
  "booking-service:8085"
  "payment-service:8086"
  "notification-service:8087"
)

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[0;33m'; BOLD=$'\033[1m'; NC=$'\033[0m'
info()  { echo "${BOLD}==>${NC} $*"; }
ok()    { echo "  ${GREEN}✓${NC} $*"; }
warn()  { echo "  ${YELLOW}!${NC} $*"; }
fail()  { echo "  ${RED}✗${NC} $*"; }

mkdir -p "$LOG_DIR"

# ---------------------------------------------------------------- preflight

info "Checking prerequisites"
command -v docker >/dev/null || { fail "docker not found"; exit 1; }
docker info >/dev/null 2>&1 || {
  fail "Docker daemon is not running — start Docker Desktop first"
  exit 1
}
ok "Docker is running"

if $START_UI; then
  command -v node >/dev/null || { fail "node not found (needed for the UI)"; exit 1; }
  ok "Node $(node --version)"
fi

# A port already in use almost always means a previous run is still up. Starting a second copy
# would silently fail to bind and leave a confusing half-started stack, so refuse up front.
for entry in "${SERVICES[@]}"; do
  port="${entry##*:}"
  if lsof -ti tcp:"$port" >/dev/null 2>&1; then
    fail "Port $port is already in use (${entry%%:*}). Run ./scripts/stop-all.sh first."
    exit 1
  fi
done
if $START_UI && lsof -ti tcp:5173 >/dev/null 2>&1; then
  fail "Port 5173 is already in use (customer-web). Run ./scripts/stop-all.sh first."
  exit 1
fi

# ---------------------------------------------------------------- infrastructure

info "Starting infrastructure (Postgres x7, Redis, Kafka)"
docker compose -f "$REPO_ROOT/docker-compose.yml" up -d >/dev/null
ok "Containers requested"

# Every container declares a healthcheck, so wait on health rather than on a fixed sleep — a
# service that connects before Postgres accepts connections dies at startup.
info "Waiting for containers to report healthy"
deadline=$((SECONDS + 180))
while true; do
  unhealthy=$(docker compose -f "$REPO_ROOT/docker-compose.yml" ps --format '{{.Name}} {{.Health}}' \
              | awk '$2 != "healthy" {print $1}' || true)
  [[ -z "$unhealthy" ]] && break
  if (( SECONDS > deadline )); then
    fail "Timed out waiting for: $unhealthy"
    exit 1
  fi
  sleep 3
done
ok "All containers healthy"

# ---------------------------------------------------------------- backend services

start_service() {
  local name="$1" port="$2"
  info "Starting $name (port $port)"
  (
    cd "$SERVICES_DIR/$name"
    SPRING_PROFILES_ACTIVE=local nohup ./mvnw spring-boot:run \
      > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
  )
  ok "launched (pid $(cat "$LOG_DIR/$name.pid")), logging to logs/$name.log"
}

for entry in "${SERVICES[@]}"; do
  start_service "${entry%%:*}" "${entry##*:}"
done

# ---------------------------------------------------------------- UI

if $START_UI; then
  info "Starting customer-web (port 5173)"
  if [[ ! -d "$UI_DIR/node_modules" ]]; then
    warn "node_modules missing — running npm install (first run only)"
    (cd "$UI_DIR" && npm install >"$LOG_DIR/customer-web-install.log" 2>&1)
  fi
  (
    cd "$UI_DIR"
    nohup npm run dev > "$LOG_DIR/customer-web.log" 2>&1 &
    echo $! > "$LOG_DIR/customer-web.pid"
  )
  ok "launched (pid $(cat "$LOG_DIR/customer-web.pid")), logging to logs/customer-web.log"
fi

# ---------------------------------------------------------------- readiness

# Spring Boot reports UP on /actuator/health only once Flyway, JPA, Kafka and Redis are all
# wired, so this is a real readiness signal rather than "the port is open".
info "Waiting for services to become healthy (first run compiles; this can take a few minutes)"
deadline=$((SECONDS + 420))
declare -a pending=()
for entry in "${SERVICES[@]}"; do pending+=("$entry"); done

while (( ${#pending[@]} > 0 )); do
  remaining=()
  for entry in "${pending[@]}"; do
    name="${entry%%:*}"; port="${entry##*:}"
    if curl -fsS "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      ok "$name is UP  →  http://localhost:$port"
    elif ! kill -0 "$(cat "$LOG_DIR/$name.pid" 2>/dev/null)" 2>/dev/null; then
      fail "$name died during startup — see logs/$name.log"
    else
      remaining+=("$entry")
    fi
  done
  pending=("${remaining[@]+"${remaining[@]}"}")
  (( ${#pending[@]} == 0 )) && break
  if (( SECONDS > deadline )); then
    for entry in "${pending[@]}"; do fail "${entry%%:*} never became healthy — see logs/${entry%%:*}.log"; done
    exit 1
  fi
  sleep 5
done

if $START_UI; then
  deadline=$((SECONDS + 180))
  until curl -fsS -o /dev/null http://localhost:5173 2>/dev/null; do
    if (( SECONDS > deadline )); then fail "customer-web never responded — see logs/customer-web.log"; exit 1; fi
    sleep 3
  done
  ok "customer-web is UP  →  http://localhost:5173"
fi

# ---------------------------------------------------------------- summary

cat <<EOF

${BOLD}RoadScanner is running${NC}

  UI          http://localhost:5173
  auth        http://localhost:8081     swagger: /swagger-ui.html
  search      http://localhost:8082     swagger: /swagger-ui.html
  provider    http://localhost:8083     swagger: /swagger-ui.html
  inventory   http://localhost:8084     swagger: /swagger-ui.html
  booking     http://localhost:8085     swagger: /swagger-ui.html
  payment     http://localhost:8086     swagger: /swagger-ui.html
  notify      http://localhost:8087     swagger: /swagger-ui.html

  Logs        tail -f logs/<service>.log
  Stop        ./scripts/stop-all.sh

EOF
