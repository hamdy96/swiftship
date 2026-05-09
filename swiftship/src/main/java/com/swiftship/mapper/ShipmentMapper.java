package com.swiftship.mapper;

import com.swiftship.dto.ShipmentResponse;
import com.swiftship.dto.history.StatusHistoryResponse;
import com.swiftship.model.Shipment;
import com.swiftship.model.ShipmentStatusHistory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;


@Component
public class ShipmentMapper {

  public ShipmentResponse mapShipmentResponse(Shipment shipment) {
    return ShipmentResponse.builder()
        .id(shipment.getId())
        .trackingNumber(shipment.getTrackingNumber())
        .customerName(shipment.getCustomerName())
        .customerEmail(shipment.getCustomerEmail())
        .originAddress(shipment.getOriginAddress())
        .destinationAddress(shipment.getDestinationAddress())
        // Warehouse IDs and names — null if not yet assigned
        .originWarehouseId(
            ObjectUtils.isEmpty(shipment.getOriginWarehouse()) ?
                null : shipment.getOriginWarehouse().getId()
        )
        .originWarehouseName(
            ObjectUtils.isEmpty(shipment.getOriginWarehouse()) ?
                null : shipment.getOriginWarehouse().getName()
        )
        .destinationWarehouseId(
            ObjectUtils.isEmpty(shipment.getDestinationWarehouse()) ?
                null : shipment.getDestinationWarehouse().getId()
        )
        .destinationWarehouseName(
            ObjectUtils.isEmpty(shipment.getDestinationWarehouse()) ?
                null : shipment.getDestinationWarehouse().getName()
        )
        // Carrier — null if not yet assigned
        .carrierId(
            ObjectUtils.isEmpty(shipment.getCarrier()) ?
                null : shipment.getCarrier().getId()
        )
        .carrierName(
            ObjectUtils.isEmpty(shipment.getDestinationWarehouse()) ?
                null : shipment.getCarrier().getName()
        )
        .status(shipment.getStatus())
        // Computed by entity: true if not delivered and >48h past estimated date
        .delayed(shipment.isDelayed())
        .weightKg(shipment.getWeightKg())
        .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
        .deliveredAt(shipment.getDeliveredAt())
        .createdAt(shipment.getCreatedAt())
        .updatedAt(shipment.getUpdatedAt())
        .build();
  }

  public StatusHistoryResponse mapHistoryResponse(ShipmentStatusHistory history) {
    return StatusHistoryResponse.builder()
        .id(history.getId())
        .fromStatus(history.getFromStatus())   // null for the initial CREATED row
        .toStatus(history.getToStatus())
        .notes(history.getNotes())
        .changedBy(history.getChangedBy())
        .changedAt(history.getChangedAt())
        .build();
  }
}
