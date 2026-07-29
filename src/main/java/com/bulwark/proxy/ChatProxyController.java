package com.bulwark.proxy;

import com.bulwark.screening.RefusalResponses;
import com.bulwark.screening.ScreeningResult;
import com.bulwark.screening.ScreeningService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAI-compatible entry point. Any OpenAI client can point its base URL at
 * Bulwark and call {@code POST /v1/chat/completions} as usual.
 *
 * <p>The request is screened by the detection stack BEFORE it is forwarded. In
 * BLOCK mode a detected injection short-circuits into an OpenAI-shaped refusal and
 * the prompt never reaches upstream; in FLAG mode (and when clean) the request is
 * forwarded unchanged - the decision is logged either way.
 */
@RestController
public class ChatProxyController {

    private final UpstreamClient upstream;
    private final ScreeningService screening;
    private final RefusalResponses refusals;

    public ChatProxyController(UpstreamClient upstream,
                               ScreeningService screening,
                               RefusalResponses refusals) {
        this.upstream = upstream;
        this.screening = screening;
        this.refusals = refusals;
    }

    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chatCompletions(@RequestBody String body) {
        ScreeningResult result = screening.screen(body);
        if (result.isBlocked()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(refusals.forDecision(result.model(), result.decision()));
        }

        ResponseEntity<String> response = upstream.forwardChatCompletion(body);
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
