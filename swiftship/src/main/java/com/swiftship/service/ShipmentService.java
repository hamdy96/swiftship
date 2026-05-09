package com.swiftship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftship.dto.ShipmentResponse;
import com.swiftship.dto.common.enums.ShipmentStatus;
import com.swiftship.dto.create.CreateShipmentRequest;
import com.swiftship.dto.history.StatusHistoryResponse;
import com.swiftship.dto.update.UpdateShipmentStatusRequest;
import com.swiftship.event.ShipmentStatusChangedEvent;
import com.swiftship.exception.CarrierNotFoundException;
import com.swiftship.exception.InvalidStatusTransitionException;
import com.swiftship.exception.ShipmentNotFoundException;
import com.swiftship.mapper.ShipmentMapper;
import com.swiftship.model.*;
import com.swiftship.repository.*;
import com.swiftship.utils.CommonUtils;
import com.swiftship.utils.ValidateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShipmentService {

  private final CommonUtils commonUtils;
  private final ObjectMapper objectMapper;
  private final ValidateUtils validateUtils;
  private final ShipmentMapper shipmentMapper;
  private final CarrierRepository carrierRepository;
  private final ShipmentRepository shipmentRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ShipmentStatusHistoryRepository historyRepository;

  @Transactional
  public ShipmentResponse createShipment(CreateShipmentRequest request) {
    // Step 1 — look up carrier; throw 404 if missing
    Carrier carrier = carrierRepository.findById(request.getCarrierId())
        .orElseThrow(() -> new CarrierNotFoundException(request.getCarrierId().toString()));

    // business rule: carrier must not exceed 20 active shipments
    validateUtils.validateCarrierCapacity(carrier.getId());

    // Step 2 — build and persist the shipment
    Shipment shipment =
        Shipment.builder()
            .trackingNumber(commonUtils.generateTrackingNumber())
            .customerName(request.getCustomerName())
            .customerEmail(request.getCustomerEmail())
            .originAddress(request.getOriginAddress())
            .destinationAddress(request.getDestinationAddress())
            .carrier(carrier)
            .weightKg(request.getWeightKg())
            .estimatedDeliveryDate(request.getEstimatedDeliveryDate())
            .status(ShipmentStatus.CREATED)
            .build();

    shipment = shipmentRepository.save(shipment);

    // Step 3 — audit trail: record the initial CREATED transition
    recordHistory(shipment, null, ShipmentStatus.CREATED, "Shipment created", "SYSTEM");

    // Step 4 — outbox: write event row in the same transaction
    saveOutboxEvent(shipment, null, ShipmentStatus.CREATED, "Shipment created");

    log.info("Created shipment {} assigned to carrier {}", shipment.getTrackingNumber(), carrier.getName());
    return shipmentMapper.mapShipmentResponse(shipment);
  }

  public ShipmentResponse getByTrackingNumber(String trackingNumber) {
    return shipmentRepository.findByTrackingNumber(trackingNumber)
        .map(shipmentMapper::mapShipmentResponse)
        .orElseThrow(() -> new ShipmentNotFoundException(trackingNumber));
  }

  public ShipmentResponse getById(UUID id) {
    return shipmentRepository.findById(id)
        .map(shipmentMapper::mapShipmentResponse)
        .orElseThrow(() -> new ShipmentNotFoundException(id.toString()));
  }

  public Page<ShipmentResponse> listShipments(ShipmentStatus status, Pageable pageable) {
    Page<Shipment> page = (status != null)
        ? shipmentRepository.findByStatus(status, pageable)
        : shipmentRepository.findAll(pageable);
    return page.map(shipmentMapper::mapShipmentResponse);
  }

  public Page<ShipmentResponse> listByCarrier(UUID carrierId, Pageable pageable) {
    return shipmentRepository.findByCarrierId(carrierId, pageable)
        .map(shipmentMapper::mapShipmentResponse);
  }

  public List<ShipmentResponse> listDelayed() {
    return shipmentRepository.findDelayedShipments().stream()
        .map(shipmentMapper::mapShipmentResponse)
        .toList();
  }

  public List<StatusHistoryResponse> getHistory(UUID shipmentId) {
    // Verify the shipment exists before querying history
    shipmentRepository.findById(shipmentId)
        .orElseThrow(() -> new ShipmentNotFoundException(shipmentId.toString()));

    return historyRepository.findByShipmentIdOrderByChangedAtAsc(shipmentId)
        .stream()
        .map(shipmentMapper::mapHistoryResponse)
        .toList();
  }

  @Transactional
  public ShipmentResponse updateStatus(UUID shipmentId, UpdateShipmentStatusRequest request) {
    Shipment shipment = shipmentRepository.findById(shipmentId)
        .orElseThrow(() -> new ShipmentNotFoundException(shipmentId.toString()));

    ShipmentStatus oldStatus = shipment.getStatus();
    ShipmentStatus newStatus = request.getStatus();

    // Rule 1 — FSM check: is this transition allowed by the lifecycle?
    if (!oldStatus.canTransitionTo(newStatus)) {
      throw new InvalidStatusTransitionException(oldStatus, newStatus);
    }

    // Rule 2 — RETURNED is only allowed within the 14-day return window
    if (newStatus == ShipmentStatus.RETURNED) {
      validateUtils.validateReturnWindow(shipment);
    }

    // Apply the new status
    shipment.setStatus(newStatus);

    // Record delivery timestamp when the shipment is first marked DELIVERED
    if (newStatus == ShipmentStatus.DELIVERED) {
      shipment.setDeliveredAt(Instant.now());
    }

    shipment = shipmentRepository.save(shipment);

    // Write audit row and outbox event atomically with the status update
    recordHistory(shipment, oldStatus, newStatus, request.getNotes(), request.getChangedBy());
    saveOutboxEvent(shipment, oldStatus, newStatus, request.getNotes());

    log.info("Shipment {} transitioned {} → {}", shipment.getTrackingNumber(), oldStatus, newStatus);
    return shipmentMapper.mapShipmentResponse(shipment);
  }

  @Transactional
  public ShipmentResponse assignCarrier(UUID shipmentId, UUID carrierId) {
    Shipment shipment = shipmentRepository.findById(shipmentId)
        .orElseThrow(() -> new ShipmentNotFoundException(shipmentId.toString()));

    Carrier carrier = carrierRepository.findById(carrierId)
        .orElseThrow(() -> new CarrierNotFoundException(carrierId.toString()));

    // Capacity check against the new carrier (not the old one)
    validateUtils.validateCarrierCapacity(carrierId);

    shipment.setCarrier(carrier);
    shipment = shipmentRepository.save(shipment);

    log.info("Assigned carrier {} to shipment {}", carrier.getName(), shipment.getTrackingNumber());
    return shipmentMapper.mapShipmentResponse(shipment);
  }

  private void recordHistory(
      Shipment shipment,
      ShipmentStatus oldStatus,
      ShipmentStatus newStatus,
      String notes, String changedBy
  ) {
    ShipmentStatusHistory history = ShipmentStatusHistory.builder()
        .shipment(shipment)
        .fromStatus(oldStatus)
        .toStatus(newStatus)
        .notes(notes)
        .changedBy(changedBy)
        .build();
    historyRepository.save(history);
  }

  @SuppressWarnings("unchecked")
  private void saveOutboxEvent(
      Shipment shipment,
      ShipmentStatus oldStatus,
      ShipmentStatus newStatus,
      String notes
  ) {
    ShipmentStatusChangedEvent event =
        ShipmentStatusChangedEvent.builder()
            .eventId(UUID.randomUUID())
            .shipmentId(shipment.getId())
            .trackingNumber(shipment.getTrackingNumber())
            .previousStatus(oldStatus)
            .newStatus(newStatus)
            .customerEmail(shipment.getCustomerEmail())
            .carrierName(shipment.getCarrier() != null ? shipment.getCarrier().getName() : null)
            .notes(notes)
            .occurredAt(Instant.now())
            .build();

    // Serialise the event newStatus a Map so it can be stored as JSONB
    // Using ObjectMapper ensures the same JSON shape that Kafka consumers expect
    Map<String, Object> payload = objectMapper.convertValue(event, Map.class);

    OutboxEvent outboxEvent = OutboxEvent.builder()
        .aggregateId(shipment.getId())          // link back newStatus the shipment
        .aggregateType("SHIPMENT")
        .eventType("SHIPMENT_STATUS_CHANGED")   // consumer-readable event name
        .payload(payload)
        .published(false)                       // OutboxEventPublisher will flip this
        .build();

    outboxEventRepository.save(outboxEvent);

    log.debug("Outbox event saved for shipment {} transition {} → {}",
        shipment.getTrackingNumber(), oldStatus, newStatus);
  }
}
