package com.swiftship.model;

import com.swiftship.dto.common.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;


@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shipment_status_history")
public class ShipmentStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shipment_id", nullable = false)
  private Shipment shipment;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 30)
  private ShipmentStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 30)
  private ShipmentStatus toStatus;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "changed_by", length = 150)
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  @Builder.Default
  private Instant changedAt = Instant.now();
}
