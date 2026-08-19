package com.bulwark.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Pulls the screenable text out of an OpenAI-compatible chat-completion request.
 *
 * <p>Concatenates the text of every message so the layers have one string to scan. Handles both
 * the string form of {@code content} and the array-of-parts form ({@code [{"type":"text",…}]}).
 *
 * <p>It also screens <em>indirect</em> content: retrieved documents and tool output that arrive in
 * configured request fields ({@code bulwark.screening.indirect-fields}, e.g. {@code documents}) get
 * folded in too, so an injection hidden in a retrieved document is screened like any user input.
 * If the body can't be parsed as the expected shape, the raw body is returned so screening still
 * runs rather than being silently skipped.
 */
@Component
public class MessageExtractor {

    /**
     * @param model the requested model, or {@code null} if absent/unparseable
     * @param text  concatenated message and indirect-field text to screen (never null)
     */
    public record ExtractedRequest(String model, String text) {}

    private final ObjectMapper mapper;
    private final Set<String> indirectFields;

    public MessageExtractor(ObjectMapper mapper, ScreeningProperties props) {
        this.mapper = mapper;
        this.indirectFields = Set.copyOf(props.indirectFields());
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
            if (!indirectFields.isEmpty()) {
                appendIndirect(root, sb);
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

    /** Walk the request for any configured indirect field and screen all text beneath it. */
    private void appendIndirect(JsonNode node, StringBuilder sb) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (indirectFields.contains(field.getKey())) {
                    appendAllText(field.getValue(), sb);
                } else {
                    appendIndirect(field.getValue(), sb);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                appendIndirect(child, sb);
            }
        }
    }

    /** Collect every string leaf under a node - retrieved content may be a string, array, or object. */
    private static void appendAllText(JsonNode node, StringBuilder sb) {
        if (node.isTextual()) {
            append(sb, node.asText());
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                appendAllText(child, sb);
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
