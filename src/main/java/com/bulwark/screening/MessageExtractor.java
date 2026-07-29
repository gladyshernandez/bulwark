package com.bulwark.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Pulls the screenable text out of an OpenAI-compatible chat-completion request.
 *
 * <p>Concatenates the text of every message so Layer 1 has one string to scan.
 * Handles both the string form of {@code content} and the array-of-parts form
 * ({@code [{"type":"text","text":"…"}]}). If the body can't be parsed as the
 * expected shape, the raw body is returned as the text so screening still runs
 * rather than being silently skipped.
 */
@Component
public class MessageExtractor {

    /**
     * @param model the requested model, or {@code null} if absent/unparseable
     * @param text  concatenated message text to screen (never null)
     */
    public record ExtractedRequest(String model, String text) {}

    private final ObjectMapper mapper;

    public MessageExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ExtractedRequest extract(String body) {
        if (body == null || body.isBlank()) {
            return new ExtractedRequest(null, "");
        }
        try {
            JsonNode root = mapper.readTree(body);
            String model = root.path("model").isTextual() ? root.get("model").asText() : null;

            JsonNode messages = root.path("messages");
            if (!messages.isArray()) {
                // Not the shape we expected — fall back to scanning the raw body.
                return new ExtractedRequest(model, body);
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode message : messages) {
                appendContent(message.path("content"), sb);
            }
            return new ExtractedRequest(model, sb.toString());
        } catch (Exception e) {
            // Unparseable JSON — screen the raw body rather than skip screening.
            return new ExtractedRequest(null, body);
        }
    }

    private static void appendContent(JsonNode content, StringBuilder sb) {
        if (content.isTextual()) {
            append(sb, content.asText());
        } else if (content.isArray()) {
            for (JsonNode part : content) {
                if (part.path("type").asText("").equals("text")) {
                    append(sb, part.path("text").asText(""));
                }
            }
        }
    }

    private static void append(StringBuilder sb, String s) {
        if (s != null && !s.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(s);
        }
    }
}
