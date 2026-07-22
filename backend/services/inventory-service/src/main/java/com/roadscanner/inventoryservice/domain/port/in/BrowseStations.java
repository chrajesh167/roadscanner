package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Station;

import java.util.List;
import java.util.Objects;

public interface BrowseStations {

    Result browse(Command command);

    record Command(String prefix, CityId cityId, int limit) {
        public Command {
            if (prefix == null) {
                prefix = "";
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
        }
    }

    record Result(List<Station> stations) {
        public Result {
            Objects.requireNonNull(stations, "stations must not be null");
            stations = List.copyOf(stations);
        }
    }
}
