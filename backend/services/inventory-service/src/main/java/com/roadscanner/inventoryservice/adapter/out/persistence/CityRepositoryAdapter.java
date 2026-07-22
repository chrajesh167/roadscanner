package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.out.CityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class CityRepositoryAdapter implements CityRepository {

    private final CitySpringDataRepository springDataRepository;
    private final CityMapper mapper = new CityMapper();

    CityRepositoryAdapter(CitySpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<City> findById(CityId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<City> searchByPrefix(String prefix, int limit) {
        return springDataRepository.findByNameStartingWithIgnoreCase(prefix, PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
