package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMetadataTest {

    @Test
    void unknownHasNoLabel() {
        assertThat(DeviceMetadata.unknown().label()).isEmpty();
    }

    @Test
    void ofWithBlankLabelIsUnknown() {
        assertThat(DeviceMetadata.of("  ").label()).isEmpty();
    }

    @Test
    void ofWithLabelIsPresent() {
        assertThat(DeviceMetadata.of("Chrome on macOS").label()).contains("Chrome on macOS");
    }
}
