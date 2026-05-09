package com.swiftship.repository;

import com.swiftship.model.ShipmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, UUID> {
  List<ShipmentStatusHistory> findByShipmentIdOrderByChangedAtAsc(UUID shipmentId);
}
