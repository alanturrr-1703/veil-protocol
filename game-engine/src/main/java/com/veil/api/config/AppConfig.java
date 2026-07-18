package com.veil.api.config;

import com.veil.confidential.ConfidentialGateway;
import com.veil.confidential.MockConfidentialGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires cross-cutting beans. The {@link ConfidentialGateway} is the ONLY place the Midnight
 * implementation is chosen: swap {@link MockConfidentialGateway} for a real
 * {@code MidnightConfidentialGateway} here (or via a Spring profile) with no other changes.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    /** Local dev default: in-process confidential layer, no Midnight node required. */
    @Bean
    public ConfidentialGateway confidentialGateway() {
        return new MockConfidentialGateway();
    }

    /** Allow the Vite dev server to call the REST API during local development. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
