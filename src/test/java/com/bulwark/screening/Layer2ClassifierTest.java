package com.bulwark.screening;

import com.bulwark.config.Layer2Properties;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class Layer2ClassifierTest {

    private static Layer2Classifier classifier(ClassifierClient client) {
        return new Layer2Classifier(client, new Layer2Properties(null, 0.5, 800));
    }

    @Test
    void scoresAtOrAboveThresholdAreInjections() {
        ScreeningDecision d = classifier(client(OptionalDouble.of(0.9))).scan("hello");

        assertThat(d.isInjection()).isTrue();
        assertThat(d.layer()).isEqualTo(Layer2Classifier.LAYER);
        assertThat(d.rule()).isEqualTo(Layer2Classifier.RULE);
        assertThat(d.score()).isEqualTo(0.9);
    }

    @Test
    void scoresBelowThresholdAreClean() {
        ScreeningDecision d = classifier(client(OptionalDouble.of(0.4))).scan("hello");

        assertThat(d.isInjection()).isFalse();
        assertThat(d.verdict()).isEqualTo(Verdict.CLEAN);
        assertThat(d.score()).isEqualTo(0.4);
        assertThat(d.rule()).isNull();
    }

    @Test
    void unreachableSidecarIsDegradedNotInjection() {
        ScreeningDecision d = classifier(client(OptionalDouble.empty())).scan("hello");

        assertThat(d.isDegraded()).isTrue();
        assertThat(d.isInjection()).isFalse();   // fail-open: not treated as a hit
        assertThat(d.score()).isNull();
    }

    @Test
    void isEnabledFollowsTheClient() {
        assertThat(classifier(client(OptionalDouble.empty(), true)).isEnabled()).isTrue();
        assertThat(classifier(client(OptionalDouble.empty(), false)).isEnabled()).isFalse();
    }

    private static ClassifierClient client(OptionalDouble score) {
        return client(score, true);
    }

    private static ClassifierClient client(OptionalDouble score, boolean enabled) {
        return new ClassifierClient() {
            @Override
            public OptionalDouble injectionScore(String text) {
                return score;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }
        };
    }
}
