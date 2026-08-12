package com.roadscanner.inventoryservice.domain.exception;

import com.roadscanner.inventoryservice.domain.model.CityId;

public class CityNotFoundException extends InventoryServiceException {

    private final CityId cityId;

    public CityNotFoundException(CityId cityId) {
        super("No such city: " + cityId);
        this.cityId = cityId;
    }

    public CityId cityId() {
        return cityId;
    }
}
