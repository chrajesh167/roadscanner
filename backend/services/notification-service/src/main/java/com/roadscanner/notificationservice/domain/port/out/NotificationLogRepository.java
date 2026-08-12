package com.roadscanner.notificationservice.domain.port.out;

import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the notification log. */
public interface NotificationLogRepository {

    /**
     * Persists a {@code PENDING} record, unless this (event, channel) pair is already claimed.
     *
     * <p>Returns empty when it is. The check is the database's unique constraint rather than a
     * read-then-write, because two consumer instances handling the same redelivered event would
     * both pass a prior existence check and both send.
     */
    Optional<NotificationRecord> claim(NotificationRecord pending);

    NotificationRecord save(NotificationRecord record);

    Optional<NotificationRecord> findByEventAndChannel(UUID eventId, NotificationChannel channel);
}
