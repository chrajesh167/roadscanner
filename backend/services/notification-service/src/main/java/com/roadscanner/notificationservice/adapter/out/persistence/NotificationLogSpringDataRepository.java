package com.roadscanner.notificationservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface NotificationLogSpringDataRepository extends JpaRepository<NotificationLogJpaEntity, UUID> {

    Optional<NotificationLogJpaEntity> findByEventIdAndChannel(UUID eventId, String channel);
}
