package com.swiftship.repository;

import com.swiftship.model.Shipment;
import com.swiftship.dto.common.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
  Optional<Shipment> findByTrackingNumber(String trackingNumber);
  Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);
  Page<Shipment> findByCarrierId(UUID carrierId, Pageable pageable);

  @Query("SELECT COUNT(s) FROM Shipment s WHERE s.carrier.id = :carrierId " +
      "AND s.status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')")
  long countActiveShipmentsByCarrier(@Param("carrierId") UUID carrierId);

  @Query(value = "SELECT * FROM shipment s " +
      "WHERE s.status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED') " +
      "AND s.estimated_delivery_date IS NOT NULL " +
      "AND s.estimated_delivery_date < DATE_SUB(CURDATE(), INTERVAL 2 DAY)",
      nativeQuery = true)
  List<Shipment> findDelayedShipments();
}
