package com.swiftship;

import com.swiftship.dto.common.enums.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Finite-State Machine (FSM) defined in ShipmentStatus.
 */
@DisplayName("ShipmentStatus FSM")
class ShipmentStatusFsmTest {

    /**
     * Tests every transition that SHOULD be allowed according to the lifecycle:
     *
     *   CREATED      → PICKED_UP, CANCELLED
     *   PICKED_UP    → IN_TRANSIT, CANCELLED
     *   IN_TRANSIT   → OUT_FOR_DELIVERY
     *   OUT_FOR_DELIVERY → DELIVERED
     *   DELIVERED    → RETURNED
     */
    @ParameterizedTest(name = "{0} → {1} = ALLOWED")
    @CsvSource({
        "CREATED,          PICKED_UP",
        "CREATED,          CANCELLED",
        "PICKED_UP,        IN_TRANSIT",
        "PICKED_UP,        CANCELLED",
        "IN_TRANSIT,       OUT_FOR_DELIVERY",
        "OUT_FOR_DELIVERY, DELIVERED",
        "DELIVERED,        RETURNED"
    })
    @DisplayName("Valid transitions are permitted")
    void valid_transitions_are_permitted(ShipmentStatus oldStatus, ShipmentStatus newStatus) {
        assertThat(oldStatus.canTransitionTo(newStatus))
                .as("Expected %s → %s to be ALLOWED", oldStatus, newStatus)
                .isTrue();
    }

    /**
     * Tests every transition that SHOULD be rejected — the FSM must block these
     * to prevent the lifecycle from being short-circuited or reversed.
     */
    @ParameterizedTest(name = "{0} → {1} = REJECTED")
    @CsvSource({
        // Cannot skip stages
        "CREATED,          DELIVERED",
        "CREATED,          IN_TRANSIT",
        "CREATED,          RETURNED",
        "PICKED_UP,        DELIVERED",
        // Cannot cancel once in transit
        "IN_TRANSIT,       CANCELLED",
        // Cannot go backwards
        "IN_TRANSIT,       CREATED",
        "DELIVERED,        CREATED",
        "DELIVERED,        CANCELLED",
        // Cannot leave terminal states
        "CANCELLED,        CREATED",
        "RETURNED,         DELIVERED"
    })
    @DisplayName("Invalid transitions are rejected")
    void invalid_transitions_are_rejected(ShipmentStatus oldStatus, ShipmentStatus newStatus) {
        assertThat(oldStatus.canTransitionTo(newStatus))
                .as("Expected %s → %s to be REJECTED", oldStatus, newStatus)
                .isFalse();
    }

    /**
     * CANCELLED and RETURNED are terminal states — once a shipment reaches them,
     * no further transitions are allowed at all.
     */
    @ParameterizedTest(name = "{0} has no allowed outgoing transitions (terminal)")
    @MethodSource("terminalStatuses")
    @DisplayName("Terminal statuses have no allowed transitions")
    void terminal_statuses_have_no_transitions(ShipmentStatus terminal) {
        assertThat(terminal.allowedTransitions())
                .as("Expected %s to be a terminal state with no transitions", terminal)
                .isEmpty();
    }

    static Stream<ShipmentStatus> terminalStatuses() {
        return Stream.of(ShipmentStatus.CANCELLED, ShipmentStatus.RETURNED);
    }
}
