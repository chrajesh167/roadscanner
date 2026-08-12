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

    /**
     * Updates the managed row in place rather than persisting a detached entity built from the
     * domain object. Cities carry seeded, administratively-owned fields (name, state, country) that
     * this service must never author; rebuilding an entity here would write them back from whatever
     * the domain object happened to hold and quietly turn a link into a full overwrite.
     */
    @Override
    public City save(City city) {
        CityJpaEntity entity = springDataRepository.findById(city.id().value())
                .orElseThrow(() -> new IllegalStateException(
                        "City " + city.id().value() + " does not exist; cities are seeded, never created here"));
        entity.linkCanonicalLocation(city.locationId().orElse(null));
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
