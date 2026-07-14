# Functional Requirements — Phase 1 (Bus Booking)

Scope: everything below is Bus Booking only. Domains are named generically (e.g., "Inventory" and "Booking" rather than "Bus Seat" and "Bus Ticket") because the same requirement pattern is expected to generalize to future verticals — see `docs/architecture/high-level-design.md` §12. That generalization is *not* a Phase 1 deliverable.

## 1. Traveler Account & Identity

- **FR-1.1** A traveler can register and log in (email/phone + password, or OTP).
- **FR-1.2** A traveler can manage their profile: name, contact details, saved passengers (for booking on behalf of others).
- **FR-1.3** A traveler can view their booking history.
- **FR-1.4** A traveler can reset a forgotten password / recover account access.

## 2. Search

- **FR-2.1** A traveler can search bus trips by origin, destination, and travel date.
- **FR-2.2** Search results show operator, departure/arrival time, duration, bus type, fare, and live seat availability.
- **FR-2.3** A traveler can filter and sort results (price, departure time, duration, operator rating, bus type).
- **FR-2.4** A traveler can view the seat layout and seat-level availability for a selected trip.

## 3. Booking

- **FR-3.1** A traveler can select seat(s) and enter passenger details for a trip.
- **FR-3.2** The system places a temporary hold on selected seats during checkout to prevent double-booking.
- **FR-3.3** A booking is confirmed only after successful payment.
- **FR-3.4** A seat hold is automatically released if checkout is abandoned or expires before payment.
- **FR-3.5** A traveler can cancel a booking, subject to the operator's cancellation policy for that trip.
- **FR-3.6** A traveler can view and download their e-ticket for a confirmed booking.

## 4. Payment

- **FR-4.1** A traveler can pay for a booking through a supported payment method (card, UPI, wallet — final method list is market-dependent).
- **FR-4.2** A traveler receives a refund automatically when a cancellation is refund-eligible.
- **FR-4.3** A failed or partial payment must never leave a booking in an inconsistent state (charged without a confirmed booking, or confirmed without payment).

## 5. Operator Management

- **FR-5.1** A bus operator can apply for onboarding with company profile and verification details.
- **FR-5.2** An approved operator can manage their fleet: buses, seat layouts, amenities.
- **FR-5.3** An operator can manage routes and trip schedules.
- **FR-5.4** An operator can set fares and a cancellation policy per route/trip.
- **FR-5.5** An operator can view bookings against their trips and manage remaining seat inventory.
- **FR-5.6** An operator can view a settlement/payout summary for completed trips.

## 6. Notifications

- **FR-6.1** A traveler receives a booking confirmation (email/SMS) on successful booking.
- **FR-6.2** A traveler receives a cancellation/refund confirmation.
- **FR-6.3** A traveler receives trip reminders ahead of departure (e.g., T-24h, T-2h).
- **FR-6.4** An operator receives alerts for new bookings and cancellations on their trips.

## 7. Reviews & Ratings

- **FR-7.1** A traveler can rate and review a trip after it is completed.
- **FR-7.2** A review can only be submitted against a verified, completed booking — no unverified reviews.
- **FR-7.3** An operator's/trip's average rating is visible alongside search results.

## 8. Admin / Platform Operations

- **FR-8.1** A platform admin can approve or suspend operator accounts.
- **FR-8.2** A platform admin can view platform-wide booking and revenue analytics.
- **FR-8.3** A support agent can look up a traveler's booking, payment, and notification history to resolve a support case.
