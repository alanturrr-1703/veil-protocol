package com.veil.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.confidential.ConfidentialGateway;
import com.veil.confidential.MidnightConfidentialGateway;
import com.veil.confidential.MockConfidentialGateway;
import com.veil.leaderboard.LeaderboardListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires cross-cutting beans. The {@link ConfidentialGateway} is the ONLY place the Midnight
 * implementation is chosen — selected by Spring profile, with no other changes to game logic:
 *
 * <ul>
 *   <li>default (no profile / {@code local}) — {@link MockConfidentialGateway}, in-process,
 *       so the whole stack runs with no Midnight node.</li>
 *   <li>{@code midnight} — {@link MidnightConfidentialGateway}, which calls the relayer
 *       sidecar that submits real zk-transactions. Enable with
 *       {@code mvn spring-boot:run -Dspring-boot.run.profiles=midnight}.</li>
 * </ul>
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    /** Local dev default: in-process confidential layer, no Midnight node required. */
    @Bean
    @Profile("!midnight")
    public ConfidentialGateway mockConfidentialGateway() {
        return new MockConfidentialGateway();
    }

    /** Real confidential referee: routes to the Midnight relayer sidecar over HTTP. */
    @Bean
    @Profile("midnight")
    public ConfidentialGateway midnightConfidentialGateway(
            @Value("${veil.midnight.relayer-url:http://localhost:6301}") String relayerUrl,
            ObjectMapper mapper) {
        return new MidnightConfidentialGateway(relayerUrl, mapper);
    }

    /**
     * One shared analytics Observer for the whole server, so standings accumulate across
     * every match. Registered on each new engine's EventBus by {@code GameService}.
     */
    @Bean
    public LeaderboardListener leaderboardListener() {
        return new LeaderboardListener();
    }

    /** Allow the Vite dev server to call the REST API during local development. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
