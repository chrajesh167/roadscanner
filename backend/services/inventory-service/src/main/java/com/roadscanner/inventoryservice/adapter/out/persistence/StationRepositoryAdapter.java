package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Station;
import com.roadscanner.inventoryservice.domain.model.StationId;
import com.roadscanner.inventoryservice.domain.port.out.StationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class StationRepositoryAdapter implements StationRepository {

    private final StationSpringDataRepository springDataRepository;
    private final StationMapper mapper = new StationMapper();

    StationRepositoryAdapter(StationSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Station> findById(StationId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Station> search(String prefix, CityId cityId, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<StationJpaEntity> entities = cityId == null
                ? springDataRepository.findByNameStartingWithIgnoreCase(prefix, page)
                : springDataRepository.findByNameStartingWithIgnoreCaseAndCityId(prefix, cityId.value(), page);
        return entities.stream().map(mapper::toDomain).toList();
    }
}
