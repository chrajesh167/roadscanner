#!/usr/bin/env sh
#
# Links catalog cities to their canonical location ids — the one administrative step a freshly
# deployed environment needs before any provider catalog can sync.
#
# Why this exists: inventory-service seeds cities and routes via Flyway, but NOT the canonical
# location link. CityRepository's own contract explains that it "cannot be seeded because canonical
# ids are minted per environment". Until the link exists, SynchronizeProviderCatalogService skips
# every route ("has a city with no canonical location recorded") and no trip is ever imported — a
# stack that looks healthy and has an empty catalog.
#
# Both ids are DISCOVERED at run time, never hardcoded: the catalog city id from inventory-service,
# the canonical id from search-service, matched on city name.
#
# Runs INSIDE the deployment network, because the endpoint it calls is /internal/** — deliberately
# unreachable through nginx (docker/nginx/nginx.conf). Invoke it via the compose profile:
#
#   docker compose -f docker-compose.prod.yml --env-file <env> --profile bootstrap run --rm bootstrap
#
# Idempotent: the endpoint records a link rather than appending one, so re-running is a no-op and
# an already-linked city is left as it is.
set -eu

INVENTORY_URL="${INVENTORY_URL:-http://inventory-service:8084}"
SEARCH_URL="${SEARCH_URL:-http://search-service:8082}"
# Space-separated. Defaults to the pair the demo flow books; every seeded city may be listed.
BOOTSTRAP_CITIES="${BOOTSTRAP_CITIES:-Hyderabad Bengaluru}"
# Catalog sync runs on a fixed delay (roadscanner.inventory.sync.schedule-interval, 300s by
# default), so a freshly linked environment needs one cycle before trips appear.
SYNC_TIMEOUT="${SYNC_TIMEOUT:-720}"
VERIFY_ORIGIN="${VERIFY_ORIGIN:-Hyderabad}"
VERIFY_DESTINATION="${VERIFY_DESTINATION:-Bengaluru}"

log()  { printf '%s\n' "$*"; }
fail() { printf '\nBOOTSTRAP FAILED: %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is required in the bootstrap image"; }
need curl
need jq

wait_for() {
    name=$1 url=$2 deadline=$(( $(date +%s) + 120 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if curl -fsS -o /dev/null --max-time 3 "$url" 2>/dev/null; then
            log "  $name is answering"
            return 0
        fi
        sleep 3
    done
    fail "$name did not become reachable at $url within 120s"
}

log "Waiting for dependencies"
wait_for inventory-service "$INVENTORY_URL/actuator/health"
wait_for search-service    "$SEARCH_URL/actuator/health"

log ""
log "Linking canonical locations"
linked=0
for city in $BOOTSTRAP_CITIES; do
    # Catalog city id, by exact name. `q` is a prefix search, so the exact match is selected rather
    # than the first row — "Bengaluru" must not silently link whatever sorts first.
    city_id=$(curl -fsS --get "$INVENTORY_URL/api/v1/inventory/cities" \
                --data-urlencode "q=$city" --data-urlencode "limit=50" \
              | jq -r --arg n "$city" '(.cities // [])[] | select(.name == $n) | .id' | head -1)
    [ -n "$city_id" ] || fail "no catalog city named '$city' (is the Flyway seed present?)"

    # Canonical location id, from the service that mints them.
    location_id=$(curl -fsS --get "$SEARCH_URL/api/v1/locations" \
                    --data-urlencode "q=$city" --data-urlencode "limit=25" \
                  | jq -r --arg n "$city" '(.suggestions // [])[] | select(.city == $n or .displayName == $n) | .id' | head -1)
    [ -n "$location_id" ] || fail "no canonical location for '$city' in search-service"

    code=$(curl -sS -o /tmp/link.out -w '%{http_code}' -X PUT \
             "$INVENTORY_URL/internal/api/v1/inventory/cities/$city_id/canonical-location" \
             -H 'Content-Type: application/json' \
             -d "{\"canonicalLocationId\":\"$location_id\"}")
    case "$code" in
        2*) log "  $city: catalog=$city_id -> canonical=$location_id" ; linked=$((linked + 1)) ;;
        *)  fail "linking '$city' returned HTTP $code: $(cat /tmp/link.out)" ;;
    esac
done
[ "$linked" -gt 0 ] || fail "no cities were linked"

log ""
log "Waiting for catalog sync to import trips (up to ${SYNC_TIMEOUT}s)"
today=$(TZ=Asia/Kolkata date +%F)
deadline=$(( $(date +%s) + SYNC_TIMEOUT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
    count=$(curl -fsS --get "$SEARCH_URL/api/v1/search/trips" \
              --data-urlencode "origin=$VERIFY_ORIGIN" \
              --data-urlencode "destination=$VERIFY_DESTINATION" \
              --data-urlencode "date=$today" 2>/dev/null \
            | jq -r '(.content // []) | map(select(.bookable == true)) | length' 2>/dev/null || echo 0)
    if [ "${count:-0}" -gt 0 ]; then
        log ""
        log "Bootstrap complete: $count bookable $VERIFY_ORIGIN -> $VERIFY_DESTINATION trip(s) for $today"
        exit 0
    fi
    sleep 15
done

fail "cities were linked, but no bookable $VERIFY_ORIGIN -> $VERIFY_DESTINATION trip appeared for $today
       within ${SYNC_TIMEOUT}s. Check inventory-service logs for 'Catalog sync for' lines; a sync
       that reports tripsReconciled=0 after linking means the provider returned nothing."
