package com.roadscanner.bookingservice.domain.port.in;

/** Scheduled sweep (docs/requirements/actors.md's Scheduler/System Jobs actor: "closing out
 * bookings after departure"). Transitions every {@code CONFIRMED} booking whose trip's departure
 * time has passed to {@code COMPLETED} — backs FR-7.2's review-eligibility gate. */
public interface CompleteBooking {

    Result completeDepartedTrips();

    record Result(int completedCount) {
    }
}
