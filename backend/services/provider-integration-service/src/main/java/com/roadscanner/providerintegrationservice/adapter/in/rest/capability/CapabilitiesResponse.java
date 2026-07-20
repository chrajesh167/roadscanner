package com.roadscanner.providerintegrationservice.adapter.in.rest.capability;

import com.roadscanner.providerintegrationservice.domain.port.in.GetProviderCapabilities;

import java.util.Set;
import java.util.stream.Collectors;

public record CapabilitiesResponse(String providerType, Set<String> capabilities) {

    public static CapabilitiesResponse from(GetProviderCapabilities.Result result) {
        return new CapabilitiesResponse(result.providerType().code(),
                result.capabilities().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()));
    }
}
