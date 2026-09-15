package com.bulwark.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAuthFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    void blankTokenLeavesTheEndpointOpen() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");

        new MetricsAuthFilter("").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void matchingBearerTokenPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer s3cret");

        new MetricsAuthFilter("s3cret").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");

        new MetricsAuthFilter("s3cret").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer wrong");

        new MetricsAuthFilter("s3cret").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
