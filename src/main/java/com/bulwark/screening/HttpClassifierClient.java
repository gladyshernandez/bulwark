package com.bulwark.screening;

import com.bulwark.config.Layer2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Calls the Python DeBERTa sidecar over HTTP: {@code POST /classify} with {@code {"text": ...}},
 * expecting {@code {"score": <injection probability>, "label": ...}} back.
 *
 * <p>Any failure - unreachable host, timeout, bad status, unparseable body - returns
 * {@link OptionalDouble#empty()} rather than throwing, so a sidecar outage degrades Layer 2
 * to a no-op instead of failing the proxied request.
 */
@Component
public class HttpClassifierClient implements ClassifierClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClassifierClient.class);

    private final boolean enabled;
    private final RestClient restClient;
    private volatile boolean warnedUnavailable = false;

    public HttpClassifierClient(Layer2Properties props) {
        this.enabled = props.isEnabled();
        this.restClient = enabled ? buildClient(props) : null;
    }

    private static RestClient buildClient(Layer2Properties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeoutMillis());
        factory.setReadTimeout(props.timeoutMillis());
        return RestClient.builder()
                .baseUrl(props.url())
                .requestFactory(factory)
                .build();
    }

    @Override
    public OptionalDouble injectionScore(String text) {
        if (!enabled) {
            return OptionalDouble.empty();
        }
        try {
            Map<?, ?> body = restClient.post()
                    .uri("/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text == null ? "" : text))
                    .retrieve()
                    .body(Map.class);
            Object score = body == null ? null : body.get("score");
            if (score instanceof Number n) {
                return OptionalDouble.of(n.doubleValue());
            }
            return OptionalDouble.empty();
        } catch (RuntimeException e) {
            // Fail open: an unavailable classifier must never fail the request.
            if (!warnedUnavailable) {
                log.warn("Layer 2 classifier unavailable; failing open: {}", e.getMessage());
                warnedUnavailable = true;
            }
            return OptionalDouble.empty();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
