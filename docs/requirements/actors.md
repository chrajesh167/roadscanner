# Actors

## Primary Actors (Phase 1)

### Traveler (Customer)

Searches, books, pays for, manages, and cancels trips; reviews completed trips. The primary end-user of `frontend/apps/customer-web`.

### Bus Operator

A business partner running one or more bus fleets on the platform. Manages fleet, routes, schedules, fares, and inventory; views their own bookings and settlements. Uses `frontend/apps/operator-portal`. RoadScanner is a multi-operator marketplace — no operator is treated as a platform-internal special case.

### Platform Admin

Internal RoadScanner staff. Approves/suspends operator accounts, monitors platform-wide health and analytics. Uses `frontend/apps/admin-console`.

### Support Agent

A narrower-permission variant of the Admin role, focused on resolving individual customer issues (booking/payment lookups, refund overrides) rather than platform-wide operations. Called out separately from Admin because the interaction pattern — drilling into one customer's case — is distinct, even if the same console and underlying account type serves both.

## Secondary / System Actors

### Payment Gateway (external)

Third-party service that actually processes card/UPI/wallet payments. RoadScanner never stores raw payment credentials; `payment-service` holds only references and status.

### Notification Provider (external)

Third-party email/SMS/push delivery provider invoked by `notification-service`.

### Scheduler / System Jobs (internal, non-human)

Automated actor responsible for time-based logic: releasing expired seat holds, sending trip reminders, closing out bookings after departure. Called out explicitly because it initiates actions with no human in the loop, which matters for the event flows in `docs/requirements/user-journeys.md`.

## Future Actors (Phase 2+, not implemented in Phase 1)

- **Airline / Hotel / Train / Cab Operator** — the same conceptual role as Bus Operator, generalized once those verticals are added.
- **Corporate / Travel-Agent Account** — a bulk-booking actor, deferred past Phase 1's consumer-only launch.
