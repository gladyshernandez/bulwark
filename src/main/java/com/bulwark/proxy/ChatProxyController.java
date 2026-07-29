package com.bulwark.proxy;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI-compatible entry point. Any OpenAI client can point its base URL at
 * Bulwark and call {@code POST /v1/chat/completions} as usual.
 *
 * Day 1: forwards the request unchanged and relays the upstream response.
 *
 * Day 2 hook: this is where Layer 1 screening runs. Before forwarding, pass the
 * request through the detection stack; if it flags an injection, return a refusal
 * (block mode) or annotate-and-forward (flag-only mode) instead of calling upstream.
 */
@RestController
public class ChatProxyController {

    private final UpstreamClient upstream;

    public ChatProxyController(UpstreamClient upstream) {
        this.upstream = upstream;
    }

    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chatCompletions(@RequestBody String body) {
        // TODO (Day 2): screen `body` here before forwarding.
        ResponseEntity<String> response = upstream.forwardChatCompletion(body);
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
