package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.City;

import java.util.List;
import java.util.Objects;

/** Prefix/text lookup over {@link City} — backs search-form autocomplete
 * (docs/services/inventory-service/use-cases.md). */
public interface BrowseCities {

    Result browse(Command command);

    record Command(String prefix, int limit) {
        public Command {
            if (prefix == null) {
                prefix = "";
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
        }
    }

    record Result(List<City> cities) {
        public Result {
            Objects.requireNonNull(cities, "cities must not be null");
            cities = List.copyOf(cities);
        }
    }
}
