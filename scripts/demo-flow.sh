#!/usr/bin/env bash
#
# RoadScanner demo / end-to-end flow, driven entirely over the running HTTP APIs.
#
# What it does: resolves today's bookable MOCK Hyderabad -> Bengaluru trip dynamically, then runs
# the full journey through it — search, seat map, hold, booking, payment capture, notification,
# cancellation — asserting the notification, cancellation and idempotency outcomes as it goes.
#
# Requires the local stack to ALREADY be running: ./scripts/start-all.sh (infrastructure plus every
# service). This script starts nothing and will fail fast if the APIs are not answering.
#
# DEMO_DATE overrides the travel date (YYYY-MM-DD); it defaults to today in Asia/Kolkata.
#
# This file must NEVER contain credentials, tokens or API keys. The booking contact address comes
# from the environment (CONTACT_EMAIL / NOTIFICATION_EMAIL_USERNAME) and no secret is ever printed.
#
# The trip is resolved at run time from search + inventory. Nothing about a trip is hardcoded:
# MOCK's catalog is generated per travel date (MockProviderDataStore builds
# MOCK-{ORIGIN}-{DEST}-{travelDate}-{suffix} on demand) and inventory's CatalogSyncScheduler only
# keeps a rolling 3-day window, so any trip UUID written into a script stops resolving within days.
# That is exactly what broke this script before: it carried a UUID for a departed trip and the
# seat-map call started returning 503.
#
# Usage:
#   ./flow.sh                 resolve today's trip and run the whole demo
#   ./flow.sh demo            same
#   ./flow.sh resolve_trip    resolve and print the trip only
#   ./flow.sh <function> ...  call one step directly (register, hold, create_booking, ...)
#
# Environment:
#   CONTACT_EMAIL   booking contact address. Defaults to NOTIFICATION_EMAIL_USERNAME so the SMTP
#                   path is exercised against a real mailbox. No credential is ever read or printed.
#   DEMO_DATE       override the travel date (YYYY-MM-DD). Defaults to today in Asia/Kolkata.
#   DEMO_BASE_URL   origin serving the customer-facing APIs. Defaults to the production ingress.
#   NOTIFICATION_DB_CONTAINER
#                   container running notification-service's Postgres, for reading notification_log.
#                   Auto-detected when unset.
set -euo pipefail

# Every customer-facing API is reached through ONE origin, because that is how the platform is
# actually served: nginx path-routes /api/v1/auth, /api/v1/search, /api/v1/inventory,
# /api/v1/bookings and /api/v1/payments to their services (docker/nginx/nginx.conf). The browser
# never sees a service port, so neither does this script.
#
# The per-service overrides below exist only for a stack run without the ingress — e.g. services
# started natively by ./scripts/start-all.sh, where each listens on its own port:
#
#   AUTH_BASE_URL=http://localhost:8081 SEARCH_BASE_URL=http://localhost:8082 \
#   INVENTORY_BASE_URL=http://localhost:8084 BOOKING_BASE_URL=http://localhost:8085 \
#   PAYMENT_BASE_URL=http://localhost:8086 ./scripts/demo-flow.sh
DEMO_BASE_URL=${DEMO_BASE_URL:-http://localhost:80}

AUTH=${AUTH_BASE_URL:-$DEMO_BASE_URL}
SEARCH=${SEARCH_BASE_URL:-$DEMO_BASE_URL}
INVENTORY=${INVENTORY_BASE_URL:-$DEMO_BASE_URL}
BOOKING=${BOOKING_BASE_URL:-$DEMO_BASE_URL}
PAYMENT=${PAYMENT_BASE_URL:-$DEMO_BASE_URL}

ORIGIN=${ORIGIN:-Hyderabad}
DESTINATION=${DESTINATION:-Bengaluru}
PROVIDER=${PROVIDER:-MOCK}

# The business date the demo runs for. India is the operating market, so "today" is today in IST,
# not in the machine's local zone and not in UTC — at 01:00 IST those are three different dates.
export TZ=Asia/Kolkata
DEMO_DATE=${DEMO_DATE:-$(date +%F)}

# Local docker-compose stub gateway secret from application-local.yml. Not a credential: it signs
# webhooks for the in-process stub gateway and has no meaning outside this machine. Overridable via
# RAZORPAY_WEBHOOK_SECRET so no environment is forced to use the checked-in local value.
RAZORPAY_SECRET="${RAZORPAY_WEBHOOK_SECRET:-local-razorpay-secret}"

# Scratch output (last search response, last registration) for debugging a failed run. Kept out of
# the working tree deliberately — this script lives in the repository now, and writing response
# dumps next to it would leave untracked files behind on every run.
STATE_DIR="${DEMO_STATE_DIR:-${TMPDIR:-/tmp}/roadscanner-demo}"
mkdir -p "$STATE_DIR"

RESOLVED_TRIP_ID=""
RESOLVED_PROVIDER_TRIP_ID=""

info() { printf '  %s\n' "$*"; }
die()  { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- trip resolution

# Resolves today's bookable MOCK trip from the live APIs. Sets RESOLVED_TRIP_ID and
# RESOLVED_PROVIDER_TRIP_ID, or exits non-zero — there is deliberately no fallback to an earlier
# date, because a demo that silently books a departed trip proves nothing.
resolve_trip() {
    local search_json
    search_json=$(curl -sS --get "$SEARCH/api/v1/search/trips" \
        --data-urlencode "origin=$ORIGIN" \
        --data-urlencode "destination=$DESTINATION" \
        --data-urlencode "date=$DEMO_DATE") || die "search-service call failed (is the stack up?)"
    printf '%s' "$search_json" > "$STATE_DIR/search.json"

    # departureTime is an instant; its UTC date is the travel date the platform keys on — the MOCK
    # provider builds its trip id from the same value and search filters `date` against it. Reading
    # this back in IST would shift a 20:00Z departure to the following day and reject every trip.
    local candidates
    candidates=$(jq -r --arg d "$DEMO_DATE" '
        [ .content[]?
          | select(.bookable == true)
          | select((.availableSeats // 0) > 0)
          | select((.departureTime // "") | startswith($d)) ]
        | .[].tripId' <<<"$search_json")

    local trip_id provider_type provider_trip_id mapping
    for trip_id in $candidates; do
        # The search projection carries no provider, so the provider is confirmed from the mapping
        # inventory owns. This is also where providerTripId comes from — never derived from the
        # route and date, always read from the service that assigned it.
        mapping=$(curl -sS "$INVENTORY/api/v1/inventory/trips/$trip_id/provider-mapping") || continue
        provider_type=$(jq -r '.providerType // empty' <<<"$mapping")
        provider_trip_id=$(jq -r '.providerTripId // empty' <<<"$mapping")
        if [ "$provider_type" = "$PROVIDER" ] && [ -n "$provider_trip_id" ]; then
            RESOLVED_TRIP_ID="$trip_id"
            RESOLVED_PROVIDER_TRIP_ID="$provider_trip_id"
            printf 'Resolved trip\n'
            info "Demo date        : $DEMO_DATE (Asia/Kolkata)"
            info "Origin           : $ORIGIN"
            info "Destination      : $DESTINATION"
            info "Provider         : $provider_type"
            info "Provider trip ID : $RESOLVED_PROVIDER_TRIP_ID"
            info "Catalog trip ID  : $RESOLVED_TRIP_ID"
            return 0
        fi
    done

    printf '\nNo bookable %s trip for %s %s -> %s\n' "$PROVIDER" "$DEMO_DATE" "$ORIGIN" "$DESTINATION" >&2
    printf 'Search returned %s trip(s):\n' "$(jq -r '(.content // []) | length' <<<"$search_json")" >&2
    jq -r '(.content // [])[] | "  \(.tripId)  dep=\(.departureTime)  bookable=\(.bookable)  seats=\(.availableSeats)"' \
        <<<"$search_json" >&2 || true
    printf 'Full response: %s\n' "$STATE_DIR/search.json" >&2
    die "cannot run the demo without a current trip (no fallback to an earlier date by design)"
}

# ---------------------------------------------------------------- steps

register() {
    local ident body
    ident="smtpverify$(date +%s)@roadscanner.test"
    body=$(curl -sS -X POST "$AUTH/api/v1/auth/register" -H 'Content-Type: application/json' \
        -d "{\"identifier\":\"$ident\",\"password\":\"${DEMO_TRAVELER_PASSWORD:-Str0ng!Passw0rd}\",\"deviceLabel\":\"smtp-verify\"}")
    printf '%s' "$body" > "$STATE_DIR/register.json"
    printf '%s' "$body"
}

seat_view() {
    local token=$1 trip=${2:-$RESOLVED_TRIP_ID}
    curl -sS "$BOOKING/api/v1/bookings/trips/$trip/seats" -H "Authorization: Bearer $token"
}

hold() {
    local token=$1 seat=$2 trip=${3:-$RESOLVED_TRIP_ID}
    curl -sS -X POST "$BOOKING/api/v1/bookings/holds" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        -d "{\"tripId\":\"$trip\",\"passengers\":[{\"firstName\":\"Asha\",\"lastName\":\"Menon\",\"birthDate\":\"1994-03-17\",\"gender\":\"female\",\"seatNumber\":\"$seat\"}]}"
}

create_booking() {
    local token=$1 holdId=$2
    local contact=${CONTACT_EMAIL:-${NOTIFICATION_EMAIL_USERNAME:-}}
    [ -n "$contact" ] || die "set CONTACT_EMAIL (or NOTIFICATION_EMAIL_USERNAME) to the booking contact address"
    curl -sS -X POST "$BOOKING/api/v1/bookings" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' \
        -d "{\"seatHoldId\":\"$holdId\",\"contact\":{\"phone\":\"+919876543210\",\"email\":\"$contact\",\"communicationPreference\":\"email\"}}"
}

initiate_payment() {
    local token=$1 bookingId=$2 amount=$3 currency=$4
    curl -sS -X POST "$PAYMENT/api/v1/payments" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' -H "Idempotency-Key: idem-$bookingId" \
        -d "{\"bookingReference\":\"$bookingId\",\"amount\":$amount,\"currency\":\"$currency\",\"method\":\"CARD\"}"
}

sign() { printf '%s' "$1" | openssl dgst -sha256 -hmac "$RAZORPAY_SECRET" -hex | awk '{print $NF}'; }

webhook() {
    local payload=$1
    curl -sS -X POST "$PAYMENT/webhooks/RAZORPAY" -H 'Content-Type: application/json' \
        -H "X-Webhook-Signature: $(sign "$payload")" -d "$payload"
}

cancel_booking() {
    local token=$1 bookingId=$2
    curl -sS -X POST "$BOOKING/api/v1/bookings/$bookingId/cancel" -H "Authorization: Bearer $token"
}

# notification_log is read straight from Postgres because no API exposes it — it is the service's
# own audit record, not customer-facing surface. The container differs per stack (compose project
# prefix vs the local infrastructure file), so it is resolved rather than assumed.
notification_db_container() {
    if [ -n "${NOTIFICATION_DB_CONTAINER:-}" ]; then
        printf '%s' "$NOTIFICATION_DB_CONTAINER"
        return
    fi
    for candidate in $(docker ps --format '{{.Names}}' 2>/dev/null | grep -E 'notification.*postgres|postgres.*notification' || true); do
        printf '%s' "$candidate"
        return
    done
    die "cannot find the notification Postgres container — set NOTIFICATION_DB_CONTAINER"
}

notification_rows() {
    docker exec "$(notification_db_container)" psql -U notificationservice -d notificationservice -t -A -F'|' \
        -c "select event_type, channel, status, coalesce(failure_reason,'') from notification_log where booking_id='$1' order by created_at;"
}

# ---------------------------------------------------------------- full demo

demo() {
    resolve_trip

    printf '\nRunning flow\n'
    local token seat hold_id booking_id payment_id fare
    token=$(register | jq -r .accessToken)
    [ -n "$token" ] && [ "$token" != "null" ] || die "registration failed"

    seat=$(seat_view "$token" | jq -r '[.seats[]? | select(.status=="AVAILABLE")][0].seatNumber')
    [ -n "$seat" ] && [ "$seat" != "null" ] || die "no AVAILABLE seat on trip $RESOLVED_TRIP_ID"
    fare=$(seat_view "$token" | jq -r '[.seats[]? | select(.seatNumber==$s)][0].priceAmount' --arg s "$seat")
    info "seat             : $seat @ $fare"

    hold_id=$(hold "$token" "$seat" | jq -r .seatHoldId)
    [ -n "$hold_id" ] && [ "$hold_id" != "null" ] || die "seat hold failed"
    info "seatHoldId       : $hold_id"

    booking_id=$(create_booking "$token" "$hold_id" | jq -r .bookingId)
    [ -n "$booking_id" ] && [ "$booking_id" != "null" ] || die "booking creation failed"
    info "bookingId        : $booking_id"

    payment_id=$(initiate_payment "$token" "$booking_id" "$fare" INR | jq -r .paymentId)
    [ -n "$payment_id" ] && [ "$payment_id" != "null" ] || die "payment initiation failed"
    info "paymentId        : $payment_id"

    webhook "{\"gatewayEventId\":\"evt-cap-$payment_id\",\"type\":\"PAYMENT_CAPTURED\",\"gatewayPaymentId\":\"razorpay-pay-$payment_id\"}" >/dev/null
    info "payment capture  : sent"

    sleep 10
    printf '\nConfirmation\n'
    info "booking status   : $(curl -sS "$BOOKING/api/v1/bookings/$booking_id" -H "Authorization: Bearer $token" | jq -r .status)"
    info "notification_log : $(notification_rows "$booking_id" | tr '\n' ' ')"

    printf '\nCancellation\n'
    info "cancel           : $(cancel_booking "$token" "$booking_id" | jq -r .status)"
    sleep 10
    info "notification_log : $(notification_rows "$booking_id" | tr '\n' ' ')"

    printf '\nIdempotency (repeat cancel)\n'
    cancel_booking "$token" "$booking_id" >/dev/null
    sleep 8
    local rows
    rows=$(notification_rows "$booking_id" | grep -c .)
    info "notification rows: $rows (expected 2 — one confirmation, one cancellation)"
    [ "$rows" -eq 2 ] || die "expected exactly 2 notification rows, found $rows"

    printf '\nDone. bookingId=%s\n' "$booking_id"
}

if [ $# -eq 0 ]; then
    demo
else
    "$@"
fi
