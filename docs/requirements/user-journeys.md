# User Journeys — Phase 1

Each journey lists the actor, trigger, steps, and the services primarily involved. Service references are for architectural traceability (see `docs/architecture/high-level-design.md`) — this is not an API contract.

## 1. Search & Book a Bus Ticket

**Actor:** Traveler · **Trigger:** wants to travel from A to B on a given date.

1. Traveler searches for trips by origin, destination, date. → `search-service` (via `api-gateway`)
2. Traveler reviews, filters, and selects a trip. → `search-service`, `inventory-service` (live seat availability)
3. Traveler selects seat(s) and enters passenger details.
4. System places a temporary hold on the selected seats. → `inventory-service`
5. Traveler proceeds to payment. → `payment-service`
6. On successful payment, the booking is confirmed and the seat hold becomes a permanent reservation. → `booking-service`, `inventory-service` (event-driven; see high-level-design.md §6)
7. Traveler receives a booking confirmation. → `notification-service`
8. Traveler can view/download the e-ticket. → `booking-service`

**Failure paths:** payment fails → seat hold is released, no booking is created. Seat hold expires before payment → traveler must re-select seats.

## 2. Cancel a Booking & Get Refund

**Actor:** Traveler · **Trigger:** needs to cancel an upcoming trip.

1. Traveler opens booking history and selects a booking to cancel. → `booking-service`
2. System evaluates the cancellation policy for that trip/operator (fee, refund eligibility). → `booking-service`, `operator-service`
3. Traveler confirms cancellation.
4. Booking is marked cancelled; the seat is released back to inventory. → `booking-service`, `inventory-service`
5. A refund is initiated to the original payment method. → `payment-service`
6. Traveler and operator are notified. → `notification-service`

## 3. Operator Manages Routes & Inventory

**Actor:** Bus Operator · **Trigger:** needs to publish a new schedule or update seat availability.

1. Operator logs into `operator-portal`. → `auth-service`
2. Operator manages fleet (buses, seat layout, amenities). → `operator-service`
3. Operator creates/updates routes and trip schedules with fares. → `operator-service`, `inventory-service`
4. New/updated trips become searchable. → `search-service` (kept current via events from `inventory-service`/`operator-service`)
5. Operator monitors bookings against their trips. → `booking-service`
6. Operator views a settlement summary for completed trips. → `payment-service`, `analytics-service`

## 4. Operator Onboarding

**Actor:** Bus Operator (prospective), Platform Admin · **Trigger:** a new operator wants to sell inventory on RoadScanner.

1. Operator submits an onboarding application (company details, documents). → `operator-service`
2. Admin reviews and approves or rejects the application. → `admin-console`, `operator-service`
3. On approval, the operator account is activated with access to `operator-portal`. → `auth-service`, `operator-service`
4. Operator proceeds to Journey 3 (Manages Routes & Inventory).

## 5. Post-Trip Review

**Actor:** Traveler · **Trigger:** a booked trip has completed.

1. System detects trip completion and prompts the traveler to review it. → `notification-service` (trigger), `review-service`
2. Traveler submits a rating/review tied to their completed booking. → `review-service` (validated against `booking-service`)
3. The review becomes visible in the operator's/trip's public rating. → `review-service`, `search-service` (rating surfaced in results)

## 6. Support Agent Investigates a Booking Issue

**Actor:** Support Agent · **Trigger:** a traveler contacts support about a payment or booking issue.

1. Agent looks up the traveler's booking in `admin-console`. → `booking-service`, `payment-service`
2. Agent reviews booking/payment/notification history for the case.
3. Agent takes corrective action within policy (e.g., a manual refund override, resending a confirmation). → `payment-service`, `notification-service`
4. The action is logged for audit and downstream reporting. → `analytics-service`
