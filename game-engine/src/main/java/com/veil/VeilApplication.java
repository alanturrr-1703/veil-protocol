package com.veil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. This is the transport shell that wraps the authoritative
 * game engine — it adds REST + WebSocket around the existing simulation without changing
 * any game logic. Run with: {@code mvn spring-boot:run}.
 */
@SpringBootApplication
public class VeilApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeilApplication.class, args);
    }
}
