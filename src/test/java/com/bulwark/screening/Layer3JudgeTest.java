package com.bulwark.screening;

import com.bulwark.config.Layer3Properties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Layer3JudgeTest {

    private static final double FLOOR = 0.2;

    /** A judge client with a canned reply and a controllable enabled flag. */
    private record FakeJudge(boolean enabled, Optional<Judgement> reply) implements JudgeClient {
        @Override
        public Optional<Judgement> judge(String text) {
            return reply;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    private Layer3Judge judgeWith(FakeJudge client) {
        return new Layer3Judge(client, new Layer3Properties(true, "key", "claude-sonnet-5", FLOOR, 4000));
    }

    private static ScreeningDecision layer2Clean(double score) {
        return ScreeningDecision.cleanScored(Layer2Classifier.LAYER, score, 10);
    }

    @Test
    void disabledJudgeNeverRuns() {
        Layer3Judge judge = judgeWith(new FakeJudge(false, Optional.empty()));

        assertThat(judge.isEnabled()).isFalse();
        assertThat(judge.shouldJudge(null)).isFalse();
        assertThat(judge.shouldJudge(layer2Clean(0.9))).isFalse();
    }

    @Test
    void judgesWhenLayer2DidNotRun() {
        Layer3Judge judge = judgeWith(new FakeJudge(true, Optional.empty()));

        // No Layer 2 decision at all - uncertain by default.
        assertThat(judge.shouldJudge(null)).isTrue();
        // Layer 2 degraded, so there is no score to lean on - also uncertain.
        assertThat(judge.shouldJudge(ScreeningDecision.degraded(Layer2Classifier.LAYER, 10))).isTrue();
    }

    @Test
    void judgesOnlyInsideTheUncertaintyBand() {
        Layer3Judge judge = judgeWith(new FakeJudge(true, Optional.empty()));

        assertThat(judge.shouldJudge(layer2Clean(FLOOR - 0.01))).isFalse();  // confidently clean
        assertThat(judge.shouldJudge(layer2Clean(FLOOR))).isTrue();          // at the floor
        assertThat(judge.shouldJudge(layer2Clean(0.4))).isTrue();            // inside the band
    }

    @Test
    void scanFlagsAnInjectionWithItsReason() {
        Layer3Judge judge = judgeWith(
                new FakeJudge(true, Optional.of(new JudgeClient.Judgement(true, "asks to ignore instructions"))));

        ScreeningDecision d = judge.scan("some subtle attempt");

        assertThat(d.isInjection()).isTrue();
        assertThat(d.layer()).isEqualTo(Layer3Judge.LAYER);
        assertThat(d.rule()).isEqualTo(Layer3Judge.RULE);
        assertThat(d.evidence()).isEqualTo("asks to ignore instructions");
    }

    @Test
    void scanPassesACleanVerdict() {
        Layer3Judge judge = judgeWith(
                new FakeJudge(true, Optional.of(new JudgeClient.Judgement(false, "ordinary question"))));

        ScreeningDecision d = judge.scan("what is the capital of France?");

        assertThat(d.isInjection()).isFalse();
        assertThat(d.isDegraded()).isFalse();
    }

    @Test
    void scanDegradesWhenTheJudgeIsUnreachable() {
        Layer3Judge judge = judgeWith(new FakeJudge(true, Optional.empty()));

        ScreeningDecision d = judge.scan("anything");

        assertThat(d.isDegraded()).isTrue();
        assertThat(d.isInjection()).isFalse();
    }
}
