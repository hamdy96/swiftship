package com.swiftship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftship.dto.ShipmentResponse;
import com.swiftship.dto.common.enums.ShipmentStatus;
import com.swiftship.dto.create.CreateShipmentRequest;
import com.swiftship.dto.update.UpdateShipmentStatusRequest;
import com.swiftship.exception.*;
import com.swiftship.mapper.ShipmentMapper;
import com.swiftship.model.*;
import com.swiftship.repository.*;
import com.swiftship.service.ShipmentService;
import com.swiftship.utils.CommonUtils;
import com.swiftship.utils.ValidateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@DisplayName("ShipmentService")
@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

  // ── Mocks ─────────────────────────────────────────────────────────────────

  @Mock
  CommonUtils commonUtils;
  @Mock
  ValidateUtils validateUtils;
  @Mock
  ShipmentMapper shipmentMapper;
  @Mock
  CarrierRepository carrierRepository;
  @Mock
  ShipmentRepository shipmentRepository;
  @Mock
  OutboxEventRepository outboxEventRepository;   // ← now mocked since it is used
  @Mock
  ShipmentStatusHistoryRepository historyRepository;

  @Spy
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @InjectMocks
  ShipmentService service;

  // ── Shared test fixtures ──────────────────────────────────────────────────

  private Carrier carrier;
  private Shipment shipment;

  @BeforeEach
  void setUp() {
    carrier = Carrier.builder()
        .id(UUID.randomUUID())
        .name("Ali Driver")
        .email("ali@swiftship.com")
        .phone("+966501234567")
        .active(true)
        .build();

    shipment = Shipment.builder()
        .id(UUID.randomUUID())
        .trackingNumber("SWF-TEST-000001")
        .customerName("Omar Hassan")
        .customerEmail("omar@example.com")
        .originAddress("Riyadh")
        .destinationAddress("Jeddah")
        .carrier(carrier)
        .status(ShipmentStatus.CREATED)
        .estimatedDeliveryDate(LocalDate.now().plusDays(3))
        .build();

    // Make the mapper return a minimal response so we can verify the service calls it
    lenient().when(shipmentMapper.mapShipmentResponse(any()))
        .thenAnswer(inv -> ShipmentResponse.builder()
            .status(((Shipment) inv.getArgument(0)).getStatus())
            .trackingNumber(((Shipment) inv.getArgument(0)).getTrackingNumber())
            .build());

    // Default tracking number from the utility
    lenient().when(commonUtils.generateTrackingNumber()).thenReturn("SWF-" + System.currentTimeMillis() + "-ABCDEF");
  }

  // ── createShipment ────────────────────────────────────────────────────────

  @Nested
  @DisplayName("createShipment()")
  class CreateShipmentTests {

    @Test
    @DisplayName("creates shipment successfully and saves an outbox event row")
    void creates_shipment_and_saves_outbox_event() {
      // Arrange
      when(carrierRepository.findById(carrier.getId())).thenReturn(Optional.of(carrier));
      when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      // validateCarrierCapacity is a void method — no stubbing needed (does nothing by default)

      CreateShipmentRequest req = CreateShipmentRequest.builder()
          .customerName("Omar Hassan")
          .customerEmail("omar@example.com")
          .originAddress("Riyadh")
          .destinationAddress("Jeddah")
          .carrierId(carrier.getId())
          .estimatedDeliveryDate(LocalDate.now().plusDays(3))
          .build();

      // Act
      ShipmentResponse response = service.createShipment(req);

      // Assert — shipment saved, history written, outbox event saved
      assertThat(response.getStatus()).isEqualTo(ShipmentStatus.CREATED);
      verify(shipmentRepository, times(1)).save(any());
      verify(historyRepository, times(1)).save(any());

      // Key assertion: outbox event must be saved (proves the pattern is wired)
      verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("throws CarrierCapacityExceededException when carrier is at max capacity")
    void throws_when_carrier_at_capacity() {
      // Arrange — simulate validateUtils throwing the capacity exception
      when(carrierRepository.findById(carrier.getId())).thenReturn(Optional.of(carrier));
      doThrow(new CarrierCapacityExceededException(20))
          .when(validateUtils).validateCarrierCapacity(carrier.getId());

      CreateShipmentRequest req = CreateShipmentRequest.builder()
          .customerName("Test")
          .customerEmail("test@test.com")
          .originAddress("A")
          .destinationAddress("B")
          .carrierId(carrier.getId())
          .build();

      // Act & Assert — shipment must NOT be saved if capacity is exceeded
      assertThatThrownBy(() -> service.createShipment(req))
          .isInstanceOf(CarrierCapacityExceededException.class);

      verify(shipmentRepository, never()).save(any());
      verify(outboxEventRepository, never()).save(any()); // no outbox event either
    }
  }

  // ── updateStatus ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("updateStatus()")
  class UpdateStatusTests {

    @Test
    @DisplayName("CREATED → PICKED_UP succeeds and saves outbox event")
    void created_to_picked_up_saves_outbox_event() {
      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
      when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      ShipmentResponse response = service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP, "Driver collected package", "ali@swiftship.com")
      );

      assertThat(response.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
      // Both history AND outbox event must be written on every transition
      verify(historyRepository, times(1)).save(any());
      verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("CREATED → CANCELLED succeeds")
    void created_to_cancelled_succeeds() {
      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
      when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      ShipmentResponse response = service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.CANCELLED, "Customer requested cancellation", "system")
      );

      assertThat(response.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("CREATED → DELIVERED throws InvalidStatusTransitionException (skips stages)")
    void invalid_transition_throws() {
      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));

      assertThatThrownBy(() -> service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.DELIVERED, null, null)
      ))
          .isInstanceOf(InvalidStatusTransitionException.class)
          .hasMessageContaining("CREATED")
          .hasMessageContaining("DELIVERED");

      // No outbox event should be saved for a rejected transition
      verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("IN_TRANSIT → CANCELLED throws — cancellation not allowed once in transit")
    void in_transit_cannot_be_cancelled() {
      shipment.setStatus(ShipmentStatus.IN_TRANSIT);
      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));

      assertThatThrownBy(() -> service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.CANCELLED, null, null)
      )).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("DELIVERED → RETURNED within 14 days succeeds")
    void return_within_window_succeeds() {
      shipment.setStatus(ShipmentStatus.DELIVERED);
      shipment.setDeliveredAt(Instant.now().minusSeconds(86400)); // 1 day ago — within window

      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
      when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      // validateReturnWindow does nothing (within window) — no stubbing needed

      ShipmentResponse response = service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.RETURNED, "Item damaged", "customer")
      );

      assertThat(response.getStatus()).isEqualTo(ShipmentStatus.RETURNED);
    }

    @Test
    @DisplayName("DELIVERED → RETURNED after 14 days throws ReturnWindowExpiredException")
    void return_after_window_throws() {
      shipment.setStatus(ShipmentStatus.DELIVERED);
      shipment.setDeliveredAt(Instant.now().minusSeconds(15L * 24 * 3600)); // 15 days ago

      when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
      // Simulate the validation throwing the exception
      doThrow(new ReturnWindowExpiredException(14))
          .when(validateUtils).validateReturnWindow(shipment);

      assertThatThrownBy(() -> service.updateStatus(
          shipment.getId(),
          new UpdateShipmentStatusRequest(ShipmentStatus.RETURNED, null, null)
      )).isInstanceOf(ReturnWindowExpiredException.class);

      verify(outboxEventRepository, never()).save(any()); // no outbox event saved on failure
    }
  }

  // ── isDelayed (entity-level) ───────────────────────────────────────────────

  @Nested
  @DisplayName("Shipment.isDelayed() (computed on entity)")
  class DelayedFlagTests {

    @Test
    @DisplayName("shipment is DELAYED when >48h past estimated delivery date")
    void is_delayed_when_overdue() {
      shipment.setEstimatedDeliveryDate(LocalDate.now().minusDays(3)); // 3 days in the past
      assertThat(shipment.isDelayed()).isTrue();
    }

    @Test
    @DisplayName("DELIVERED shipment is never considered delayed")
    void delivered_is_never_delayed() {
      shipment.setStatus(ShipmentStatus.DELIVERED);
      shipment.setEstimatedDeliveryDate(LocalDate.now().minusDays(5));
      assertThat(shipment.isDelayed()).isFalse();
    }

    @Test
    @DisplayName("CANCELLED shipment is never considered delayed")
    void cancelled_is_never_delayed() {
      shipment.setStatus(ShipmentStatus.CANCELLED);
      shipment.setEstimatedDeliveryDate(LocalDate.now().minusDays(5));
      assertThat(shipment.isDelayed()).isFalse();
    }

    @Test
    @DisplayName("shipment with future estimated date is not delayed")
    void future_date_is_not_delayed() {
      shipment.setEstimatedDeliveryDate(LocalDate.now().plusDays(2));
      assertThat(shipment.isDelayed()).isFalse();
    }

    @Test
    @DisplayName("shipment with no estimated date is not delayed")
    void no_estimated_date_is_not_delayed() {
      shipment.setEstimatedDeliveryDate(null);
      assertThat(shipment.isDelayed()).isFalse();
    }
  }
}
