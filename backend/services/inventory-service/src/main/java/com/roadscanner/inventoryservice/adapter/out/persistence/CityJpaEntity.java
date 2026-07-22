package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Persistence shape for {@code City} — zero compile-time dependency on {@code domain.model},
 * matching every other JPA entity in this codebase's family. Administratively managed (Flyway
 * seed data today), never written by application code — see {@code CityRepository}'s Javadoc. */
@Entity
@Table(name = "cities")
public class CityJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "country", nullable = false)
    private String country;

    protected CityJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }
}
