package com.bulwark;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal liveness endpoint. Railway's healthcheck points here.
 * Richer metrics and health live under the Actuator endpoints.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
