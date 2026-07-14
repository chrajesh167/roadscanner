package com.roadscanner.authservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignedByTest {

    @Test
    void adminFactoryEncodesTheAdminUserId() {
        UserId adminId = new UserId(UUID.randomUUID());
        assertThat(AssignedBy.admin(adminId).value()).isEqualTo("admin:" + adminId);
    }

    @Test
    void serviceFactoryEncodesTheServiceName() {
        assertThat(AssignedBy.service("operator-service").value()).isEqualTo("service:operator-service");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new AssignedBy(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
