package com.roadscanner.notificationservice.adapter.out.persistence;

import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.model.NotificationStatus;
import com.roadscanner.notificationservice.domain.model.NotificationType;
import com.roadscanner.notificationservice.domain.model.Recipient;
import com.roadscanner.notificationservice.domain.port.out.NotificationLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class NotificationLogRepositoryAdapter implements NotificationLogRepository {

    private final NotificationLogSpringDataRepository springDataRepository;

    NotificationLogRepositoryAdapter(NotificationLogSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Claims the (event, channel) pair by inserting, and treats the unique-constraint violation as
     * the answer rather than as an error.
     *
     * <p>Deliberately not "select then insert": two instances handling the same redelivered event
     * would both find nothing and both proceed to send. Letting the database arbitrate is the only
     * version of this that is correct with more than one consumer running.
     */
    @Override
    public Optional<NotificationRecord> claim(NotificationRecord pending) {
        try {
            return Optional.of(toDomain(springDataRepository.saveAndFlush(toEntity(pending))));
        } catch (DataIntegrityViolationException e) {
            // Someone else already holds this pair. Expected under redelivery, not a fault.
            return Optional.empty();
        }
    }

    @Override
    public NotificationRecord save(NotificationRecord record) {
        NotificationLogJpaEntity entity = springDataRepository.findById(record.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Notification " + record.id() + " was claimed but no longer exists"));
        entity.apply(record.status().name(), record.failureReason().orElse(null), record.sentAt().orElse(null));
        return toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<NotificationRecord> findByEventAndChannel(UUID eventId, NotificationChannel channel) {
        return springDataRepository.findByEventIdAndChannel(eventId, channel.name()).map(this::toDomain);
    }

    private NotificationLogJpaEntity toEntity(NotificationRecord record) {
        return new NotificationLogJpaEntity(record.id(), record.eventId(), record.bookingId(),
                record.type().name(), record.recipient().channel().name(), record.recipient().value(),
                record.status().name(), record.failureReason().orElse(null), record.createdAt(),
                record.sentAt().orElse(null));
    }

    private NotificationRecord toDomain(NotificationLogJpaEntity entity) {
        return NotificationRecord.reconstitute(entity.getId(), entity.getEventId(), entity.getBookingId(),
                NotificationType.valueOf(entity.getEventType()),
                new Recipient(NotificationChannel.valueOf(entity.getChannel()), entity.getRecipient()),
                NotificationStatus.valueOf(entity.getStatus()), entity.getFailureReason(),
                entity.getCreatedAt(), entity.getSentAt());
    }
}
