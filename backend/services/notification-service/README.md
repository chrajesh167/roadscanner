# notification-service

Turns booking lifecycle events into email and SMS to the traveller.

Consumes `booking-events`. Owns nothing but its own notification log, calls no other service, and
cannot change the outcome of anything it reports on — a message that fails to send leaves the
booking exactly as it was.

## What it sends, and when

| Booking event | Notification |
|---|---|
| `CONFIRMED` | `BOOKING_CONFIRMED` |
| `CANCELLED`, reason `PAYMENT_FAILED` or `PAYMENT_TIMED_OUT` | `PAYMENT_FAILED` |
| `CANCELLED`, any other reason | `BOOKING_CANCELLED` |
| `CREATED` | nothing — the traveller is still inside the flow that created it |

Only `booking-events` is consumed, not `payment-events`. A declined payment produces both a
`payment-events/FAILED` and a `booking-events/CANCELLED`, so reading both would send the customer
two messages about one incident. The cancellation event already states the cause, and — decisively —
it is the only one of the two that carries a recipient: `Contact` belongs to the `Booking`
aggregate, and `payment-service` has never held it.

Channel follows the traveller's stated `communicationPreference` exactly: `SMS` means SMS only,
anything else means email. Sending on both would ignore a choice they were explicitly asked to make.

## Running locally

```bash
# from the repo root
docker compose up -d postgres-notification kafka

cd backend/services/notification-service
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run   # :8087
```

The service starts with or without a mail server. Without one it still consumes events and records
honest failures — see below.

### Email (SMTP)

Every value comes from the environment. **Nothing here belongs in a file in this repository.**

```bash
export NOTIFICATION_EMAIL_HOST="smtp.gmail.com"
export NOTIFICATION_EMAIL_PORT="587"
export NOTIFICATION_EMAIL_USERNAME="your-email@gmail.com"
export NOTIFICATION_EMAIL_PASSWORD="YOUR_GMAIL_APP_PASSWORD"
export NOTIFICATION_EMAIL_FROM="RoadScanner <your-email@gmail.com>"
```

Put these in your shell profile (`~/.zshrc`) rather than in any file under this repository, so
there is no path by which they reach a commit.

**`NOTIFICATION_EMAIL_PASSWORD` must be a Google App Password, not your account password.** Gmail
rejects account passwords on SMTP outright. Generate one at
<https://myaccount.google.com/apppasswords> with 2-Step Verification enabled; it is 16 characters
and Google shows it once.

**`NOTIFICATION_EMAIL_FROM` should be the same address as `NOTIFICATION_EMAIL_USERNAME`.** Gmail
will not let an arbitrary address send as itself: a `from` naming a different account is either
rewritten to the authenticated one or rejected, depending on whether it is a configured alias.

Port 587 with STARTTLS is the configuration Gmail expects, and is what `application-local.yml`
sets up. Connection, read and write timeouts are bounded so a wedged mail server cannot hold a
consumer thread indefinitely.

**The recipient is never configured.** It comes from the booking's contact details — the address
the traveller entered at checkout. A configured recipient would send every traveller's confirmation
to one inbox.

### When SMTP is not configured

With `NOTIFICATION_EMAIL_HOST` unset, `LoggingEmailNotificationAdapter` is selected and every email
notification is recorded `FAILED` with `"No SMTP host configured"`. It deliberately does **not**
pretend to send: a log full of `SENT` rows for mail nobody sent is worse than an honest failure, and
the first person to trust that log would be wrong about something a customer is waiting for.

### SMS

There is no real SMS provider. `MockSmsNotificationAdapter` records `DEMO_RECORDED` — a distinct
status from `SENT`, so a stand-in with no carrier behind it can never leave a row claiming a message
was delivered. Numbers are logged masked (`***3210`) and message bodies are not logged at all.

## The notification log

```bash
docker exec roadscanner-notification-postgres \
  psql -U notificationservice -d notificationservice \
  -c "SELECT event_type, channel, status, failure_reason, created_at FROM notification_log ORDER BY created_at DESC;"
```

| Status | Meaning |
|---|---|
| `PENDING` | claimed, not yet attempted |
| `SENT` | a real provider accepted it — not proof of inbox delivery, which no sender can observe |
| `DEMO_RECORDED` | recorded by a stand-in adapter with no carrier behind it |
| `FAILED` | rejected, with `failure_reason` set |

`UNIQUE (event_id, channel)` is what makes delivery idempotent. Kafka delivers at least once, so a
redelivered `BookingConfirmed` arrives as a byte-identical message; the row is claimed *before* any
send is attempted, and the database — not a read-then-write check in Java — arbitrates. Two consumer
instances racing on the same redelivery both pass a prior existence check; only one wins an insert.

The cost of claiming first is that a process dying mid-send leaves a `PENDING` row and the customer
may get nothing. That is the right way round: a missing notification is recoverable and visible in
the log, whereas a duplicate confirmation has already reached someone.

## Failure containment

Nothing here can fail a booking. Delivery failures are caught, recorded, and swallowed; the Kafka
listener never throws. Rethrowing would mean redelivery, and redelivery means re-attempting a send
that may already have reached the customer — turning one bad SMTP response into a loop against
someone's inbox.

Retry is deliberately absent for the same reason. Every failure is either permanent (a malformed
event) or already recorded with its reason (a refused mail server).

## Health

`GET /actuator/health` on :8087. Spring's mail health indicator is **disabled**: it opens an SMTP
connection on every probe, so with no host configured it would report the whole service `DOWN` and
an orchestrator would keep restarting a service that is consuming events correctly. Delivery health
belongs in `notification_log`, with reasons.

## Testing

```bash
./mvnw test
```

No test touches a network or a mail server. `SmtpDeliveryTest` wires the real SMTP adapter into the
real application service against a mocked `JavaMailSender` — a test that needed Gmail would fail on
an aeroplane, in CI, and whenever an App Password was rotated.

## Not built yet

No notification UI, no preferences UI, no push or WhatsApp, no template management, no retry
dashboard, no SES/SNS. `TripCancelled`, `RefundCompleted` and `RefundFailed` are routed here by
`docs/architecture/event-catalog.md` but are not consumed yet.
