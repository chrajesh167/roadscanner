package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderMappings;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implements {@link SearchProviderMappings}: page the mappings, then resolve each one's canonical
 * location so a row is readable on its own.
 *
 * <p>Locations are fetched per distinct id on the page rather than per row. A page of twenty
 * mappings across five places is five reads, not twenty — and because the search filter already
 * spans the location's display name and city, the repository has joined to it anyway.
 *
 * <p>A mapping whose location has vanished is dropped rather than rendered with a placeholder.
 * The foreign key makes that impossible today, and inventing an "unknown location" row would put
 * something on screen that an administrator cannot act on. If it ever happens, the mapping is
 * simply absent from the list — a state the delete path can still resolve by id.
 */
public class SearchProviderMappingsService implements SearchProviderMappings {

    private final ProviderLocationMappingRepository mappingRepository;
    private final LocationRepository locationRepository;

    public SearchProviderMappingsService(ProviderLocationMappingRepository mappingRepository,
                                         LocationRepository locationRepository) {
        this.mappingRepository = mappingRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    public Result search(Query query) {
        ProviderLocationMappingRepository.Page page = mappingRepository.search(
                new ProviderLocationMappingRepository.Criteria(query.provider(), query.verified(),
                        query.searchTerm()),
                query.page(), query.size());

        Map<LocationId, Location> locations = resolveLocations(page.content());

        List<MappedLocation> rows = new ArrayList<>(page.content().size());
        for (ProviderLocationMapping mapping : page.content()) {
            Location location = locations.get(mapping.locationId());
            if (location != null) {
                rows.add(new MappedLocation(mapping, location));
            }
        }

        return new Result(rows, page.totalElements(), page.page(), page.size(), page.totalPages());
    }

    @Override
    public UnmappedResult findUnmappedLocations(UnmappedQuery query) {
        return new UnmappedResult(locationRepository.findActiveWithoutMappingForProvider(
                query.provider(), query.searchTerm(), query.limit()));
    }

    private Map<LocationId, Location> resolveLocations(List<ProviderLocationMapping> mappings) {
        return mappings.stream()
                .map(ProviderLocationMapping::locationId)
                .distinct()
                .map(locationRepository::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Location::id, Function.identity()));
    }
}
