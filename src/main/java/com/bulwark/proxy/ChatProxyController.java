package com.bulwark.proxy;

import com.bulwark.screening.RefusalResponses;
import com.bulwark.screening.ScreeningResult;
import com.bulwark.screening.ScreeningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/**
 * OpenAI-compatible entry point: point any OpenAI client at Bulwark and call
 * {@code POST /v1/chat/completions} as usual.
 *
 * <p>Every request is screened before it is forwarded. BLOCK mode turns a detected
 * injection into an OpenAI-shaped refusal and never reaches upstream; FLAG mode (and a
 * clean request) forwards unchanged. Either way the decision is logged, and the response
 * body is never screened.
 *
 * <p>A {@code stream: true} request is screened the same way. If it passes, the upstream
 * SSE response is relayed chunk-by-chunk; if blocked, the refusal is sent as a single SSE
 * event so the client still reads a well-formed stream. Both paths return a
 * {@link StreamingResponseBody} - the buffered one just writes its full body at once.
 *
 * <p>{@code POST /v1/screen} returns the decision alone, without forwarding, so the
 * evaluation harness can score prompts without paying for upstream calls.
 */
@RestController
public class ChatProxyController {

    private final UpstreamClient upstream;
    private final ScreeningService screening;
    private final RefusalResponses refusals;
    private final ObjectMapper mapper;

    public ChatProxyController(UpstreamClient upstream,
                               ScreeningService screening,
                               RefusalResponses refusals,
                               ObjectMapper mapper) {
        this.upstream = upstream;
        this.screening = screening;
        this.refusals = refusals;
        this.mapper = mapper;
    }

    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<StreamingResponseBody> chatCompletions(@RequestBody String body) {
        ScreeningResult result = screening.screen(body);
        boolean stream = streamRequested(body);

        if (result.isBlocked()) {
            String refusal = refusals.forDecision(result.model(), result.decision());
            // A streaming client is reading an event-stream, so hand the refusal back as one
            // SSE event rather than a bare JSON body it wouldn't parse.
            return stream
                    ? sseEvent(refusal)
                    : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(writeAll(refusal));
        }

        if (stream) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(upstream.streamChatCompletion(body));
        }

        ResponseEntity<String> response = upstream.forwardChatCompletion(body);
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(writeAll(response.getBody()));
    }

    /**
     * Screen a request and return only the decision - the prompt is never forwarded upstream.
     * Same detection path as {@code /v1/chat/completions}, exposed for the evaluation harness.
     */
    @PostMapping(
            value = "/v1/screen",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ScreeningReport screen(@RequestBody String body) {
        return ScreeningReport.from(screening.screen(body));
    }

    /**
     * Screen a request through each enabled layer independently and return every layer's verdict.
     * Used by the benchmark to measure per-layer detection; nothing is forwarded upstream.
     */
    @PostMapping(
            value = "/v1/screen/layers",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PerLayerReport screenLayers(@RequestBody String body) {
        return PerLayerReport.from(screening.screenPerLayer(body));
    }

    /** True when the request asked for a streamed response ({@code "stream": true}). */
    private boolean streamRequested(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            return node.path("stream").asBoolean(false);
        } catch (Exception e) {
            // Unparseable body: treat as non-streaming. Screening already ran over it.
            return false;
        }
    }

    /** Write a complete body at once, so the buffered path shares the streamed return type. */
    private static StreamingResponseBody writeAll(String content) {
        String out = content != null ? content : "";
        return outputStream -> {
            outputStream.write(out.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };
    }

    /** Wrap a completed JSON body as a single SSE event followed by the OpenAI {@code [DONE]} sentinel. */
    private static ResponseEntity<StreamingResponseBody> sseEvent(String json) {
        StreamingResponseBody body = outputStream -> {
            outputStream.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }
}
