package com.roadscanner.notificationservice.testsupport;

import com.roadscanner.notificationservice.domain.model.NotificationChannel;
import com.roadscanner.notificationservice.domain.model.NotificationRecord;
import com.roadscanner.notificationservice.domain.port.out.NotificationLogRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enforces the same uniqueness the database does, because the idempotency guarantee is what these
 * tests are about — a fake that accepted every claim would let a duplicate-delivery bug pass.
 */
public final class InMemoryNotificationLogRepository implements NotificationLogRepository {

    private record Key(UUID eventId, NotificationChannel channel) {
    }

    private final Map<Key, NotificationRecord> byEventAndChannel = new LinkedHashMap<>();
    private final Map<UUID, NotificationRecord> byId = new LinkedHashMap<>();

    @Override
    public Optional<NotificationRecord> claim(NotificationRecord pending) {
        Key key = new Key(pending.eventId(), pending.recipient().channel());
        if (byEventAndChannel.containsKey(key)) {
            return Optional.empty();
        }
        byEventAndChannel.put(key, pending);
        byId.put(pending.id(), pending);
        return Optional.of(pending);
    }

    @Override
    public NotificationRecord save(NotificationRecord record) {
        byId.put(record.id(), record);
        byEventAndChannel.put(new Key(record.eventId(), record.recipient().channel()), record);
        return record;
    }

    @Override
    public Optional<NotificationRecord> findByEventAndChannel(UUID eventId, NotificationChannel channel) {
        return Optional.ofNullable(byEventAndChannel.get(new Key(eventId, channel)));
    }

    public List<NotificationRecord> all() {
        return new ArrayList<>(byId.values());
    }
}
