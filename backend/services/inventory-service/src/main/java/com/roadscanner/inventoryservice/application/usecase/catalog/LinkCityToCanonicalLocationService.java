package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.CityNotFoundException;
import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.port.in.LinkCityToCanonicalLocation;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link LinkCityToCanonicalLocation}.
 *
 * <p>Deliberately thin: the rule that matters — refusing to repoint an already-linked city — lives
 * on {@link City} itself, where it applies however the city is loaded, rather than here where only
 * this one caller would honour it.
 *
 * <p>The link is not validated against search-service. Confirming that the id names a real location
 * would couple catalog geography to a live call from an administrative path that must keep working
 * while search-service is down, and it would only move the question: a syntactically valid id for
 * the *wrong* city passes either way. Sync already fails closed on an id no provider can resolve.
 */
public class LinkCityToCanonicalLocationService implements LinkCityToCanonicalLocation {

    private static final Logger log = LoggerFactory.getLogger(LinkCityToCanonicalLocationService.class);

    private final CityRepository cityRepository;

    public LinkCityToCanonicalLocationService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Override
    public Result link(Command command) {
        City city = cityRepository.findById(command.cityId())
                .orElseThrow(() -> new CityNotFoundException(command.cityId()));

        City linked = cityRepository.save(city.linkToCanonicalLocation(command.canonicalLocationId()));
        log.info("City {} ({}) linked to canonical location {}", linked.id().value(), linked.name(),
                command.canonicalLocationId());
        return new Result(linked);
    }
}
