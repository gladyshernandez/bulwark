package com.bulwark.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards {@code /actuator/prometheus} with a bearer token: blank leaves it open (local dev); when set,
 * a request needs a matching {@code Authorization: Bearer <token>} or gets a 401. The compare is
 * constant-time
 */
public class MetricsAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final String token;

    public MetricsAuthFilter(String token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (token == null || token.isBlank()) {
            // Guard disabled: the endpoint is open, as it is in local dev.
            chain.doFilter(request, response);
            return;
        }
        String presented = bearerToken(request.getHeader("Authorization"));
        if (presented != null && MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /** The token from an {@code Authorization: Bearer <token>} header, or null if it isn't one. */
    private static String bearerToken(String header) {
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return header.substring(BEARER.length()).trim();
        }
        return null;
    }
}
