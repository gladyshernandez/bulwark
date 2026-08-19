package com.bulwark.screening;

import com.bulwark.config.Layer2Properties;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClassifierClientTest {

    @Test
    void isDisabledWhenNoUrlConfigured() {
        HttpClassifierClient client = new HttpClassifierClient(new Layer2Properties("", 0.5, 800));

        assertThat(client.isEnabled()).isFalse();
        assertThat(client.injectionScore("anything")).isEqualTo(OptionalDouble.empty());
    }

    @Test
    void isEnabledWhenUrlConfigured() {
        HttpClassifierClient client =
                new HttpClassifierClient(new Layer2Properties("http://localhost:8000", 0.5, 800));

        assertThat(client.isEnabled()).isTrue();
    }
}
