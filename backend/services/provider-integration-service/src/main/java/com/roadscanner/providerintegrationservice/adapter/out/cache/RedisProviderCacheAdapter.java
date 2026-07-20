package com.roadscanner.providerintegrationservice.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.providerintegrationservice.config.ProviderProperties;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis implementation of the {@link ProviderCache} port — two independently-TTLed concerns
 * (capability metadata, seat maps) sharing one adapter, per the port's own Javadoc. Same
 * degrade-on-failure discipline as {@link RedisTokenCacheAdapter}.
 */
@Component
class RedisProviderCacheAdapter implements ProviderCache {

    private static final Logger log = LoggerFactory.getLogger(RedisProviderCacheAdapter.class);
    private static final String CAPABILITIES_KEY_PREFIX = "provider:capabilities:";
    private static final String SEAT_MAP_KEY_PREFIX = "provider:seatmap:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProviderProperties properties;

    RedisProviderCacheAdapter(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
                               ProviderProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<Set<ProviderCapability>> getCapabilities(ProviderType providerType) {
        try {
            String value = redisTemplate.opsForValue().get(capabilitiesKey(providerType));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            Set<ProviderCapability> capabilities = java.util.Arrays.stream(value.split(","))
                    .map(ProviderCapability::valueOf)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return Optional.of(capabilities);
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn("Capability cache read failed for {} — falling back to configuration lookup", providerType, e);
            return Optional.empty();
        }
    }

    @Override
    public void putCapabilities(ProviderType providerType, Set<ProviderCapability> capabilities) {
        try {
            String joined = capabilities.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
            redisTemplate.opsForValue().set(capabilitiesKey(providerType), joined, properties.capabilitiesCacheTtl());
        } catch (DataAccessException e) {
            log.warn("Capability cache write failed for {} — continuing without caching", providerType, e);
        }
    }

    @Override
    public Optional<ProviderSeatMap> getSeatMap(ProviderType providerType, String providerTripId) {
        try {
            String json = redisTemplate.opsForValue().get(seatMapKey(providerType, providerTripId));
            if (json == null) {
                return Optional.empty();
            }
            SeatMapDto dto = objectMapper.readValue(json, SeatMapDto.class);
            List<ProviderSeat> seats = dto.seats().stream()
                    .map(seat -> new ProviderSeat(new com.roadscanner.providerintegrationservice.domain.model.SeatNumber(seat.seatNumber()),
                            seat.deck(), seat.seatType(), seat.status(),
                            new com.roadscanner.providerintegrationservice.domain.model.FareAmount(seat.priceAmount(),
                                    java.util.Currency.getInstance(seat.priceCurrency()))))
                    .toList();
            return Optional.of(new ProviderSeatMap(providerTripId, providerType, seats));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Seat map cache read failed for {}/{} — falling back to a live provider call", providerType,
                    providerTripId, e);
            return Optional.empty();
        }
    }

    @Override
    public void putSeatMap(ProviderType providerType, String providerTripId, ProviderSeatMap seatMap) {
        try {
            List<SeatDto> seats = seatMap.seats().stream()
                    .map(seat -> new SeatDto(seat.seatNumber().value(), seat.deck(), seat.seatType(), seat.status(),
                            seat.price().amount(), seat.price().currency().getCurrencyCode()))
                    .toList();
            String json = objectMapper.writeValueAsString(new SeatMapDto(seats));
            redisTemplate.opsForValue().set(seatMapKey(providerType, providerTripId), json, properties.seatMapCacheTtl());
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Seat map cache write failed for {}/{} — continuing without caching", providerType,
                    providerTripId, e);
        }
    }

    private String capabilitiesKey(ProviderType providerType) {
        return CAPABILITIES_KEY_PREFIX + providerType.code();
    }

    private String seatMapKey(ProviderType providerType, String providerTripId) {
        return SEAT_MAP_KEY_PREFIX + providerType.code() + ":" + providerTripId;
    }

    private record SeatDto(String seatNumber, String deck, String seatType,
                            com.roadscanner.providerintegrationservice.domain.model.SeatStatus status,
                            java.math.BigDecimal priceAmount, String priceCurrency) {
    }

    private record SeatMapDto(List<SeatDto> seats) {
    }
}
