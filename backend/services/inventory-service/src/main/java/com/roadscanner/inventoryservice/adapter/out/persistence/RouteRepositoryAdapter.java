package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.port.out.RouteRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class RouteRepositoryAdapter implements RouteRepository {

    private final RouteSpringDataRepository springDataRepository;
    private final RouteMapper mapper = new RouteMapper();

    RouteRepositoryAdapter(RouteSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Route> findById(RouteId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Route> findByCities(CityId originCityId, CityId destinationCityId) {
        return springDataRepository.findByOriginCityIdAndDestinationCityId(originCityId.value(), destinationCityId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<Route> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
