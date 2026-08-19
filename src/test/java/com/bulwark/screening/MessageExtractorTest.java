package com.bulwark.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private MessageExtractor extractorWith(List<String> indirectFields) {
        return new MessageExtractor(mapper,
                new ScreeningProperties(ScreeningMode.BLOCK, FailMode.OPEN, indirectFields));
    }

    @Test
    void pullsTextFromStringAndPartsContent() {
        String body = """
                {"model":"m","messages":[
                  {"role":"system","content":"be helpful"},
                  {"role":"user","content":[{"type":"text","text":"hello"},{"type":"image_url","image_url":{}}]}]}
                """;

        MessageExtractor.ExtractedRequest r = extractorWith(List.of()).extract(body);

        assertThat(r.model()).isEqualTo("m");
        assertThat(r.text()).contains("be helpful").contains("hello");
    }

    @Test
    void screensTextInConfiguredIndirectFields() {
        String body = """
                {"messages":[{"role":"user","content":"summarise"}],
                 "documents":[{"id":"d1","text":"secret retrieved content"}],
                 "context":"more retrieved text"}
                """;

        MessageExtractor.ExtractedRequest r =
                extractorWith(List.of("documents", "context")).extract(body);

        assertThat(r.text())
                .contains("summarise")
                .contains("secret retrieved content")
                .contains("more retrieved text");
    }

    @Test
    void ignoresIndirectFieldsWhenNoneConfigured() {
        String body = """
                {"messages":[{"role":"user","content":"summarise"}],
                 "documents":[{"text":"retrieved content"}]}
                """;

        MessageExtractor.ExtractedRequest r = extractorWith(List.of()).extract(body);

        assertThat(r.text()).contains("summarise");
        assertThat(r.text()).doesNotContain("retrieved content");
    }

    @Test
    void fallsBackToRawBodyWhenNotTheExpectedShape() {
        String body = "not json at all";

        MessageExtractor.ExtractedRequest r = extractorWith(List.of("documents")).extract(body);

        assertThat(r.text()).isEqualTo(body);
    }
}
