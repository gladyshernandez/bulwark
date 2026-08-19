package com.bulwark.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds an OpenAI-compatible {@code chat.completion} object that carries a
 * refusal instead of a model answer. Returned in BLOCK mode when a layer flags an
 * injection — the prompt never reaches the upstream model.
 *
 * <p>The refusal uses {@code finish_reason: "content_filter"} so standard OpenAI
 * clients treat it as a normal (if filtered) completion rather than an error. A
 * {@code bulwark} object is attached with the machine-readable decision.
 */
@Component
public class RefusalResponses {

    private final ObjectMapper mapper;

    public RefusalResponses(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String forDecision(String model, ScreeningDecision decision) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "chatcmpl-bulwark-" + UUID.randomUUID());
        root.put("object", "chat.completion");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model != null ? model : "bulwark-firewall");

        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", contentFor(decision));
        choice.putNull("logprobs");
        choice.put("finish_reason", "content_filter");

        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);

        ObjectNode bulwark = root.putObject("bulwark");
        bulwark.put("blocked", true);
        bulwark.put("layer", decision.layer());
        bulwark.put("verdict", decision.verdict().name());
        bulwark.put("rule", decision.rule());
        bulwark.put("evidence", decision.evidence());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            // Fall back to a minimal body.
            return "{\"error\":{\"message\":\"blocked by bulwark\",\"type\":\"content_filter\"}}";
        }
    }

    /** The refusal text - a detected injection reads differently from a fail-closed degrade. */
    private static String contentFor(ScreeningDecision decision) {
        if (decision.isDegraded()) {
            return "Request blocked by Bulwark: screening layer " + decision.layer()
                    + " was unavailable and the fail-closed policy refused the request. "
                    + "No content was sent to the model.";
        }
        return "Request blocked by Bulwark: the prompt was flagged as a prompt-injection by "
                + decision.layer() + " (rule " + decision.rule() + "). No content was sent to the model.";
    }
}