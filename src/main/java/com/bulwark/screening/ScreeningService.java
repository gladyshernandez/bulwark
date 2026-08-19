package com.bulwark.screening;

import org.springframework.stereotype.Service;

/**
 * Orchestrates screening for a chat-completion request: extract the text, run the detection
 * layers in increasing order of cost, decide the action from the configured mode, and log it.
 *
 * <p>Layers escalate: Layer 1 runs first, and Layer 2 runs only if Layer 1 came back clean.
 * The first layer to flag an injection determines the action; every layer that runs is logged,
 * audited, and metered so per-layer behaviour can be measured.
 */
@Service
public class ScreeningService {

    private final MessageExtractor extractor;
    private final Layer1Scanner layer1;
    private final Layer2Classifier layer2;
    private final DecisionLog decisionLog;
    private final AuditLog auditLog;
    private final ScreeningMetrics metrics;
    private final ScreeningProperties props;

    public ScreeningService(MessageExtractor extractor,
                            Layer1Scanner layer1,
                            Layer2Classifier layer2,
                            DecisionLog decisionLog,
                            AuditLog auditLog,
                            ScreeningMetrics metrics,
                            ScreeningProperties props) {
        this.extractor = extractor;
        this.layer1 = layer1;
        this.layer2 = layer2;
        this.decisionLog = decisionLog;
        this.auditLog = auditLog;
        this.metrics = metrics;
        this.props = props;
    }

    public ScreeningResult screen(String body) {
        MessageExtractor.ExtractedRequest extracted = extractor.extract(body);
        String text = extracted.text();

        ScreeningDecision d1 = layer1.scan(text);
        Action a1 = record(text, d1);
        if (d1.isInjection()) {
            return new ScreeningResult(extracted.model(), d1, a1);
        }

        if (layer2.isEnabled()) {
            ScreeningDecision d2 = layer2.scan(text);
            Action a2 = record(text, d2);
            if (d2.isInjection()) {
                return new ScreeningResult(extracted.model(), d2, a2);
            }
            // Clean or degraded: fall through and allow (fail-open).
        }

        return new ScreeningResult(extracted.model(), d1, a1);
    }

    /** Log, audit, and meter one layer's decision, returning the action it implies. */
    private Action record(String text, ScreeningDecision decision) {
        Action action = actionFor(decision);
        decisionLog.record(text, decision, props.mode(), action);
        auditLog.record(text, decision, props.mode(), action);
        metrics.record(decision, props.mode(), action);
        return action;
    }

    private Action actionFor(ScreeningDecision decision) {
        if (!decision.isInjection()) {
            return Action.ALLOW;
        }
        return props.mode() == ScreeningMode.BLOCK ? Action.BLOCK : Action.FLAG;
    }
}
