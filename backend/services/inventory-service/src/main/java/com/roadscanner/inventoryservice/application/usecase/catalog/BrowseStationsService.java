package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.port.in.BrowseStations;
import com.roadscanner.inventoryservice.domain.port.out.StationRepository;

/** Implements {@link BrowseStations}. */
public class BrowseStationsService implements BrowseStations {

    private final StationRepository stationRepository;

    public BrowseStationsService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    public Result browse(Command command) {
        return new Result(stationRepository.search(command.prefix(), command.cityId(), command.limit()));
    }
}
