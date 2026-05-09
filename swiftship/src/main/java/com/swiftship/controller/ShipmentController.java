package com.swiftship.controller;

import com.swiftship.dto.ShipmentResponse;
import com.swiftship.dto.create.CreateShipmentRequest;
import com.swiftship.dto.history.StatusHistoryResponse;
import com.swiftship.dto.update.UpdateShipmentStatusRequest;
import com.swiftship.dto.common.enums.ShipmentStatus;
import com.swiftship.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Shipments",
    description = "Shipment lifecycle management API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/shipments")
public class ShipmentController {
  private final ShipmentService shipmentService;

  /**
   * Creates a new shipment and assigns it to a carrier.
   * Returns 201 Created with the full shipment object including the generated tracking number.
   * Returns 409 if the carrier already holds 20 active shipments.
   */
  @Operation(
      summary = "Create a new shipment",
      description = "Creates a shipment in CREATED status and assigns it to a carrier. " +
          "Carrier must not exceed 20 active shipments. " +
          "A SHIPMENT_STATUS_CHANGED event is published to Kafka via the outbox."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Shipment created successfully"),
      @ApiResponse(responseCode = "400", description = "Validation error — check the 'details' field"),
      @ApiResponse(responseCode = "409", description = "Carrier has reached max active shipment capacity")
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ShipmentResponse createShipment(@Valid @RequestBody CreateShipmentRequest request) {
    return shipmentService.createShipment(request);
  }

  /**
   * Returns a single shipment by its internal UUID.
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get shipment by internal ID")
  @ApiResponse(responseCode = "200", description = "Shipment found")
  @ApiResponse(responseCode = "404", description = "Shipment not found")
  public ShipmentResponse getById(@PathVariable UUID id) {
    return shipmentService.getById(id);
  }

  /**
   * Returns a shipment by its tracking number.
   * The response includes the 'delayed' boolean flag (true if >48h past estimated delivery).
   */
  @GetMapping("/track/{trackingNumber}")
  @Operation(
      summary = "Track a shipment by tracking number",
      description = "Public endpoint. Returns current status and the computed 'delayed' flag."
  )
  public ShipmentResponse trackByNumber(
      @Parameter(description = "Tracking number e.g. SWF-1714978432000-A3B2C1")
      @PathVariable String trackingNumber
  ) {
    return shipmentService.getByTrackingNumber(trackingNumber);
  }

  /**
   * Returns a paginated list of shipments, optionally filtered by status.
   */
  @GetMapping
  @Operation(
      summary = "List all shipments (paginated)",
      description = "Optionally filter by ?status=IN_TRANSIT. Supports ?page, ?size, ?sort."
  )
  public Page<ShipmentResponse> listShipments(
      @RequestParam(required = false) ShipmentStatus status,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
  ) {
    return shipmentService.listShipments(status, pageable);
  }

  /**
   * Returns all shipments that are currently DELAYED (>48h past estimated delivery date
   * and not yet in a terminal state).
   */
  @GetMapping("/delayed")
  @Operation(
      summary = "List DELAYED shipments",
      description = "Returns active shipments whose estimatedDeliveryDate is >48h in the past."
  )
  public List<ShipmentResponse> listDelayed() {
    return shipmentService.listDelayed();
  }

  /**
   * Returns all shipments assigned to a specific carrier, paginated.
   */
  @GetMapping("/carrier/{carrierId}")
  @Operation(summary = "List shipments by carrier")
  public Page<ShipmentResponse> listByCarrier(
      @PathVariable UUID carrierId,
      @PageableDefault(size = 20) Pageable pageable
  ) {
    return shipmentService.listByCarrier(carrierId, pageable);
  }

  /**
   * Returns the complete, chronological status transition history for a shipment.
   */
  @GetMapping("/{id}/history")
  @Operation(summary = "Get status transition history for a shipment")
  public List<StatusHistoryResponse> getHistory(@PathVariable UUID id) {
    return shipmentService.getHistory(id);
  }

  /**
   * Advances a shipment to a new lifecycle status.
   * The FSM rules (ShipmentStatus.canTransitionTo) are enforced by the service.
   * Returns 422 if the transition is not allowed or the return window has expired.
   * On success, a SHIPMENT_STATUS_CHANGED event is durably saved to the outbox
   * and published to Kafka by OutboxEventPublisher within ~5 seconds.
   */
  @PatchMapping("/{id}/status")
  @Operation(
      summary = "Transition shipment status",
      description = "Moves a shipment to the next valid status per the lifecycle FSM. " +
          "RETURNED is only allowed within 14 days of delivery."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Status updated"),
      @ApiResponse(responseCode = "422", description = "Invalid transition or return window expired")
  })
  public ShipmentResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateShipmentStatusRequest request
  ) {
    return shipmentService.updateStatus(id, request);
  }

  /**
   * Assigns or re-assigns a carrier to an existing shipment.
   * The new carrier's active shipment count is validated before the change.
   */
  @PatchMapping("/{id}/carrier")
  @Operation(summary = "Assign or re-assign a carrier to a shipment")
  @ApiResponse(responseCode = "200", description = "Carrier assigned")
  @ApiResponse(responseCode = "409", description = "Carrier is at maximum capacity")
  public ShipmentResponse assignCarrier(
      @PathVariable UUID id,
      @RequestParam UUID carrierId
  ) {
    return shipmentService.assignCarrier(id, carrierId);
  }
}