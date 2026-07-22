package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "routes")
public class RouteJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "origin_city_id", nullable = false)
    private UUID originCityId;

    @Column(name = "destination_city_id", nullable = false)
    private UUID destinationCityId;

    @Column(name = "distance_km")
    private Double distanceKm;

    protected RouteJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginCityId() {
        return originCityId;
    }

    public UUID getDestinationCityId() {
        return destinationCityId;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }
}
