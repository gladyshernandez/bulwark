package com.bulwark.proxy;

import com.bulwark.config.UpstreamProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * Thin client that forwards a raw chat-completion request body to the upstream
 * provider and returns the upstream response verbatim (status + body).
 *
 * Pure pass-through: no request modeling of its own. Screening runs earlier,
 * so only requests that passed the detection layers reach this call.
 */
@Component
public class UpstreamClient {

    private final RestClient restClient;
    private final String apiKey;

    public UpstreamClient(UpstreamProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
        this.apiKey = props.apiKey();
    }

    /**
     * Forward a raw JSON body to {@code /v1/chat/completions} on the upstream
     * provider. The upstream status and body are passed back untouched — including
     * error responses, which are NOT thrown (the no-op onStatus handler swallows
     * the default error behaviour so we can relay them to the caller).
     */
    public ResponseEntity<String> forwardChatCompletion(String body) {
        return restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    // Relay upstream errors as-is instead of throwing.
                })
                .toEntity(String.class);
    }

    /**
     * Forward a {@code stream: true} request and relay the upstream SSE response
     * chunk-by-chunk, flushing each chunk so the caller sees tokens as they arrive
     * instead of after the whole response is buffered. The upstream bytes are passed
     * through verbatim; the response is not screened.
     *
     * <p>The upstream call runs inside the returned body, when the servlet writes the
     * response, so the connection stays open for the life of the stream.
     */
    public StreamingResponseBody streamChatCompletion(String body) {
        return outputStream -> restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    try (InputStream in = response.getBody()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                            outputStream.flush();
                        }
                    }
                    return null;
                });
    }
}
