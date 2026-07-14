# RoadScanner — Product Vision

## Mission

Make booking travel as reliable and effortless as the best single-vertical apps make it — starting with buses, built from day one as a platform for every mode of travel.

## Problem

Bus ticket booking today is fragmented: inventory is scattered across individual operator sites, offline agents, and third-party resellers with inconsistent availability, unclear cancellation policies, and poor real-time visibility into seat availability. Travelers can't compare operators reliably; operators can't reach travelers without ceding margin to aggregators that don't invest in the experience.

## Product Vision

RoadScanner is a cloud-native, AI-powered travel platform. **Phase 1 is Bus Booking only.** Every architectural decision from Phase 1 onward is made so that Trains, Flights, Hotels, and Cabs can be added as new verticals on the *same* platform — same identity, same payment rail, same operator/partner model — rather than as separate products bolted on later.

### Where AI fits (directional, not a Phase 1 dependency)

- Search ranking and personalization
- Demand forecasting for operators (fleet/route planning)
- Fraud and anomaly detection on bookings and payments
- Conversational customer support

None of these are required for a correct Phase 1 launch. They are why the platform is built event-driven and data-observable from day one — those signals are the raw material AI features will consume later.

## Phased Roadmap

- **Phase 1 — Bus Booking:** search, book, pay, manage/cancel bookings, operator inventory and fleet management, reviews.
- **Phase 2+ — Trains, Flights, Hotels, Cabs:** added as additional verticals. See `docs/architecture/high-level-design.md` §12 for how the architecture accommodates this without a rewrite.
- **Backlog (unscheduled):** feature ideas not yet assigned to a phase — see `docs/requirements/future-roadmap.md`.

## Product Principles

1. **One platform, many verticals.** A new travel mode is a new bounded context reusing shared platform capabilities (identity, payment, notification, review) — not a new codebase.
2. **Operator-agnostic marketplace.** RoadScanner is not a single fleet operator; it aggregates many independent bus operators and must treat none of them as a special case in the platform's core.
3. **Trust over growth-hacking.** Accurate real-time inventory, no overselling, reliable refunds, and transparent cancellation policy matter more at this stage than feature breadth.
4. **Built for scale, priced for a startup.** Cloud-native and horizontally scalable from day one, without over-engineering for load the business doesn't have yet.

## Explicitly Out of Scope for Phase 1

- Trains, Flights, Hotels, Cabs (any vertical beyond Bus)
- Loyalty/rewards programs
- Native mobile apps (web-first)
- Corporate/travel-agent bulk booking accounts
- Dynamic/surge pricing driven by AI

These are not rejected ideas — they are sequenced out of Phase 1 so the initial platform ships with a correct, defensible core.
