package com.bulwark.screening;

import org.springframework.stereotype.Service;

/**
 * Orchestrates screening for a chat-completion request: extract the text, run the detection
 * layers in increasing order of cost, decide the action from the configured mode, and log it.
 *
 * <p>Layers escalate cheapest-first: Layer 1 runs first, Layer 2 runs only if Layer 1 came back
 * clean, and the Layer 3 judge runs only on inputs the cheaper layers pass but flag as uncertain.
 * The first layer that doesn't allow the request - an injection, or a degraded layer under a
 * fail-closed policy - short-circuits the rest. Every layer that runs is logged, audited, and
 * metered so per-layer behaviour can be measured.
 */
@Service
public class ScreeningService {

    private final MessageExtractor extractor;
    private final Layer1Scanner layer1;
    private final Layer2Classifier layer2;
    private final Layer3Judge layer3;
    private final DecisionLog decisionLog;
    private final AuditLog auditLog;
    private final ScreeningMetrics metrics;
    private final ScreeningProperties props;

    public ScreeningService(MessageExtractor extractor,
                            Layer1Scanner layer1,
                            Layer2Classifier layer2,
                            Layer3Judge layer3,
                            DecisionLog decisionLog,
                            AuditLog auditLog,
                            ScreeningMetrics metrics,
                            ScreeningProperties props) {
        this.extractor = extractor;
        this.layer1 = layer1;
        this.layer2 = layer2;
        this.layer3 = layer3;
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
        if (a1 != Action.ALLOW) {
            return new ScreeningResult(extracted.model(), d1, a1);
        }

        ScreeningDecision d2 = null;
        if (layer2.isEnabled()) {
            d2 = layer2.scan(text);
            Action a2 = record(text, d2);
            if (a2 != Action.ALLOW) {
                return new ScreeningResult(extracted.model(), d2, a2);
            }
            // Clean, or degraded under fail-open: fall through - Layer 3 may still weigh in.
        }

        if (layer3.shouldJudge(d2)) {
            ScreeningDecision d3 = layer3.scan(text);
            Action a3 = record(text, d3);
            if (a3 != Action.ALLOW) {
                return new ScreeningResult(extracted.model(), d3, a3);
            }
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
        // Refuse a detected injection, or a layer that couldn't run when the policy is fail-closed.
        boolean refuse = decision.isInjection()
                || (decision.isDegraded() && props.onDegrade() == FailMode.CLOSED);
        if (!refuse) {
            return Action.ALLOW;
        }
        return props.mode() == ScreeningMode.BLOCK ? Action.BLOCK : Action.FLAG;
    }
}
