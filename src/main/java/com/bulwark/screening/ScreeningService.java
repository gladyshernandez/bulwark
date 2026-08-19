package com.bulwark.screening;

import org.springframework.stereotype.Service;

/**
 * Orchestrates screening for a chat-completion request: extract the text, run the
 * detection layers, decide the action from the configured mode, and log it.
 */
@Service
public class ScreeningService {

    private final MessageExtractor extractor;
    private final Layer1Scanner layer1;
    private final DecisionLog decisionLog;
    private final AuditLog auditLog;
    private final ScreeningMetrics metrics;
    private final ScreeningProperties props;

    public ScreeningService(MessageExtractor extractor,
                            Layer1Scanner layer1,
                            DecisionLog decisionLog,
                            AuditLog auditLog,
                            ScreeningMetrics metrics,
                            ScreeningProperties props) {
        this.extractor = extractor;
        this.layer1 = layer1;
        this.decisionLog = decisionLog;
        this.auditLog = auditLog;
        this.metrics = metrics;
        this.props = props;
    }

    public ScreeningResult screen(String body) {
        MessageExtractor.ExtractedRequest extracted = extractor.extract(body);
        ScreeningDecision decision = layer1.scan(extracted.text());
        Action action = actionFor(decision);
        decisionLog.record(extracted.text(), decision, props.mode(), action);
        auditLog.record(extracted.text(), decision, props.mode(), action);
        metrics.record(decision, props.mode(), action);
        return new ScreeningResult(extracted.model(), decision, action);
    }

    private Action actionFor(ScreeningDecision decision) {
        if (!decision.isInjection()) {
            return Action.ALLOW;
        }
        return props.mode() == ScreeningMode.BLOCK ? Action.BLOCK : Action.FLAG;
    }
}