package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCityRepository implements CityRepository {

    private final Map<CityId, City> cities = new LinkedHashMap<>();

    public void add(City city) {
        cities.put(city.id(), city);
    }

    @Override
    public Optional<City> findById(CityId id) {
        return Optional.ofNullable(cities.get(id));
    }

    @Override
    public List<City> searchByPrefix(String prefix, int limit) {
        return cities.values().stream()
                .filter(c -> c.name().toLowerCase().startsWith(prefix.toLowerCase()))
                .limit(limit)
                .toList();
    }
}
