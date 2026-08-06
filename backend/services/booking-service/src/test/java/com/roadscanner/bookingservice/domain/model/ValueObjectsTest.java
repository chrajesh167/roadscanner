package com.roadscanner.bookingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    void providerTypeNormalizesToUppercase() {
        assertThat(new ProviderType("mock")).isEqualTo(new ProviderType("MOCK"));
        assertThat(new ProviderType(" flixbus ").code()).isEqualTo("FLIXBUS");
    }

    @Test
    void providerTypeRejectsBlank() {
        assertThatThrownBy(() -> new ProviderType(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passengerRequiresBothNameParts() {
        // The provider prints them in distinct places on a document that must match an ID, and
        // rejects a blank family name outright — so neither half is optional here either.
        assertThatThrownBy(() -> new Passenger(" ", "Menon", LocalDate.of(1994, 3, 17), "female", "L1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Passenger("Asha", " ", LocalDate.of(1994, 3, 17), "female", "L1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passengerRequiresABirthDate() {
        // An age cannot stand in for it: the provider wants the date itself.
        assertThatThrownBy(() -> new Passenger("Asha", "Menon", null, "female", "L1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void passengerRejectsBlankGenderOrSeat() {
        assertThatThrownBy(() -> new Passenger("Asha", "Menon", LocalDate.of(1994, 3, 17), " ", "L1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Passenger("Asha", "Menon", LocalDate.of(1994, 3, 17), "female", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passengerComposesAFullNameButNeverSplitsOne() {
        assertThat(new Passenger("Asha", "Menon", LocalDate.of(1994, 3, 17), "female", "L1").fullName())
                .isEqualTo("Asha Menon");
    }

    @Test
    void contactRequiresSomewhereToSendTheTicket() {
        assertThatThrownBy(() -> new Contact(" ", "asha@example.com", Contact.CommunicationPreference.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contact("+919876543210", " ", Contact.CommunicationPreference.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void communicationPreferenceDefaultsToEmailAndRejectsAnythingElse() {
        assertThat(Contact.CommunicationPreference.parse(null)).isEqualTo(Contact.CommunicationPreference.EMAIL);
        assertThat(Contact.CommunicationPreference.parse("sms")).isEqualTo(Contact.CommunicationPreference.SMS);
        assertThatThrownBy(() -> Contact.CommunicationPreference.parse("pigeon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fareRejectsNegativeAmount() {
        assertThatThrownBy(() -> new Fare(BigDecimal.valueOf(-1), Currency.getInstance("INR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requesterContextIsPrivilegedOnlyForAdminAndSupport() {
        java.util.UUID id = java.util.UUID.randomUUID();
        assertThat(new RequesterContext(id, Role.ADMIN).isPrivileged()).isTrue();
        assertThat(new RequesterContext(id, Role.SUPPORT).isPrivileged()).isTrue();
        assertThat(new RequesterContext(id, Role.TRAVELER).isPrivileged()).isFalse();
        assertThat(new RequesterContext(id, Role.OPERATOR).isPrivileged()).isFalse();
    }
}
