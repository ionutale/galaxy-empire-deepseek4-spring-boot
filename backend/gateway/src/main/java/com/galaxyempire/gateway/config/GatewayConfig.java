package com.galaxyempire.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("auth-service", r -> r
                .path("/api/auth/**")
                .uri("lb://auth-service"))
            .route("game-service", r -> r
                .path("/api/game/**")
                .uri("lb://game-service"))
            .route("game-service-ws", r -> r
                .path("/ws/**")
                .uri("lb:ws://game-service"))
            .build();
    }
}
