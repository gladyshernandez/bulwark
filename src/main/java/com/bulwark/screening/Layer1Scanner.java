package com.bulwark.screening;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer 1 — a fast regex / heuristic scan.
 *
 * <p>Catches the obvious, high-signal cases: instruction-override phrases, known
 * jailbreak markers, system-prompt exfiltration, and coarse obfuscation hints
 * (long base64 blobs, invisible/bidi unicode). It deliberately trades recall for
 * speed and cheapness - paraphrase and subtle attempts are the job of Layers 2–3.
 * False positives here are expected and are measured.
 */
@Component
public class Layer1Scanner {

    public static final String LAYER = "layer1-regex";

    /** A named detection rule. */
    private record Rule(String id, Pattern pattern) {}

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Rule> RULES = List.of(
            // Instruction override: "ignore all previous instructions", etc.
            new Rule("override-instructions", Pattern.compile(
                    "\\b(ignore|disregard|forget|override)\\b[^.\\n]{0,40}?\\b"
                            + "(all|any|the|your|previous|prior|above|earlier|preceding)\\b"
                            + "[^.\\n]{0,40}?\\b(instructions?|prompts?|directions?|rules?|context)\\b",
                    CI)),

            // System-prompt / instruction exfiltration.
            new Rule("prompt-exfiltration", Pattern.compile(
                    "\\b(reveal|show|print|repeat|expose|leak|display|output)\\b[^.\\n]{0,30}?\\b"
                            + "(system\\s+prompt|initial\\s+instructions?|your\\s+(instructions?|prompt|rules?))\\b",
                    CI)),

            // Explicit "new instructions:" injection preamble.
            new Rule("new-instructions", Pattern.compile(
                    "\\bnew\\s+instructions?\\s*[:\\-]", CI)),

            // Role / persona override.
            new Rule("role-override", Pattern.compile(
                    "\\b(you\\s+are\\s+now|from\\s+now\\s+on\\s+you|act\\s+as\\s+(an?\\s+)?"
                            + "|pretend\\s+(to\\s+be|you\\s+are))\\b",
                    CI)),

            // Known jailbreak markers.
            new Rule("jailbreak-marker", Pattern.compile(
                    "\\b(DAN|do\\s+anything\\s+now|developer\\s+mode|jailbreak|unfiltered|"
                            + "without\\s+any\\s+restrictions?)\\b",
                    CI)),

            // Coarse obfuscation hint: a long base64-looking blob.
            new Rule("base64-blob", Pattern.compile(
                    "[A-Za-z0-9+/]{40,}={0,2}")),

            // Coarse obfuscation hint: invisible / bidi / unicode-tag characters.
            new Rule("invisible-unicode", Pattern.compile(
                    "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]"
                            + "|[\\x{E0000}-\\x{E007F}]"))
    );

    private static final int EVIDENCE_MAX = 120;

    /**
     * Scan {@code text} and return the first rule that fires, or a clean verdict.
     *
     * @param text concatenated request content to screen (never null)
     */
    public ScreeningDecision scan(String text) {
        long start = System.nanoTime();
        if (text != null && !text.isEmpty()) {
            for (Rule rule : RULES) {
                Matcher m = rule.pattern().matcher(text);
                if (m.find()) {
                    return ScreeningDecision.injection(
                            LAYER, rule.id(), truncate(m.group()), elapsedMicros(start));
                }
            }
        }
        return ScreeningDecision.clean(LAYER, elapsedMicros(start));
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }

    private static String truncate(String s) {
        String cleaned = s.strip();
        return cleaned.length() <= EVIDENCE_MAX ? cleaned : cleaned.substring(0, EVIDENCE_MAX) + "…";
    }
}