package com.swiftship.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI swiftShipOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("SwiftShip — Shipment Tracking API")
            .version("1.0.0")
            .description("""
                REST API for managing shipment lifecycle in the SwiftShip logistics platform.
                
                **Lifecycle:**
                `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`
                - `CANCELLED` is allowed from CREATED or PICKED_UP only
                - `RETURNED` is allowed from DELIVERED within 14 days only
                - `DELAYED` is a computed flag (not a lifecycle status) —
                  true when the shipment is not delivered and >48h past estimated date
                
                **Event-Driven:** Every status change publishes a `SHIPMENT_STATUS_CHANGED`
                event to Kafka via the Transactional Outbox pattern, ensuring
                no events are lost even if Kafka is temporarily unavailable.
                """))
        .servers(List.of(
            new Server().url("http://localhost:8080/api/v1").description("Local Development"),
            new Server().url("https://api.swiftship.com/api/v1").description("Production")
        ));
  }
}
