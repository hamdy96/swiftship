package com.swiftship.repository;

import com.swiftship.model.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CarrierRepository extends JpaRepository<Carrier, UUID> {
}
