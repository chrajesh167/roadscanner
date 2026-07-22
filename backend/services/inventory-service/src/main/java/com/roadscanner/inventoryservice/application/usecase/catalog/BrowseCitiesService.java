package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.port.in.BrowseCities;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;

/** Implements {@link BrowseCities}. */
public class BrowseCitiesService implements BrowseCities {

    private final CityRepository cityRepository;

    public BrowseCitiesService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Override
    public Result browse(Command command) {
        return new Result(cityRepository.searchByPrefix(command.prefix(), command.limit()));
    }
}
