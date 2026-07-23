package com.roadscanner.bookingservice.config;

import com.roadscanner.bookingservice.domain.port.in.CompleteBooking;
import com.roadscanner.bookingservice.domain.port.in.SweepStaleHolds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The two scheduled jobs this service runs — thin trigger beans; all logic lives in the
 * application-layer services (framework-free, independently testable), matching
 * {@code inventory-service}'s {@code CatalogSyncScheduler} pattern. */
@Component
public class BookingSchedulers {

    private static final Logger log = LoggerFactory.getLogger(BookingSchedulers.class);

    private final CompleteBooking completeBooking;
    private final SweepStaleHolds sweepStaleHolds;

    public BookingSchedulers(CompleteBooking completeBooking, SweepStaleHolds sweepStaleHolds) {
        this.completeBooking = completeBooking;
        this.sweepStaleHolds = sweepStaleHolds;
    }

    @Scheduled(cron = "${roadscanner.booking.scheduling.complete-booking-cron}")
    public void completeDepartedBookings() {
        try {
            CompleteBooking.Result result = completeBooking.completeDepartedTrips();
            if (result.completedCount() > 0) {
                log.info("Completed {} booking(s) whose trip has departed", result.completedCount());
            }
        } catch (RuntimeException e) {
            log.error("Complete Booking sweep failed unexpectedly", e);
        }
    }

    @Scheduled(cron = "${roadscanner.booking.scheduling.sweep-stale-holds-cron}")
    public void sweepStaleHolds() {
        try {
            SweepStaleHolds.Result result = sweepStaleHolds.sweep();
            if (result.expiredHoldsRemoved() > 0 || result.bookingsCancelled() > 0) {
                log.info("Stale-hold sweep removed {} hold(s), cancelled {} booking(s)",
                        result.expiredHoldsRemoved(), result.bookingsCancelled());
            }
        } catch (RuntimeException e) {
            log.error("Sweep Stale Holds failed unexpectedly", e);
        }
    }
}
