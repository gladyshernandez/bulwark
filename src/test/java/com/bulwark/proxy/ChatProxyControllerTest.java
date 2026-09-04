package com.bulwark.proxy;

import com.bulwark.screening.Action;
import com.bulwark.screening.RefusalResponses;
import com.bulwark.screening.ScreeningResult;
import com.bulwark.screening.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatProxyController.class)
class ChatProxyControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ScreeningService screening;
    @MockBean
    UpstreamClient upstream;
    @MockBean
    RefusalResponses refusals;

    private static ScreeningResult allow() {
        return new ScreeningResult("gpt-test", null, Action.ALLOW);
    }

    private static ScreeningResult block() {
        return new ScreeningResult("gpt-test", null, Action.BLOCK);
    }

    private static ScreeningResult flag() {
        return new ScreeningResult("gpt-test", null, Action.FLAG);
    }

    private static StreamingResponseBody writing(String text) {
        return out -> out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void streamedCleanRequestRelaysUpstreamStreamAsEventStream() throws Exception {
        given(screening.screen(any())).willReturn(allow());
        given(upstream.streamChatCompletion(any()))
                .willReturn(writing("data: {\"delta\":\"hi\"}\n\ndata: [DONE]\n\n"));

        MvcResult started = mvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stream\":true,\"messages\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data: {\"delta\":\"hi\"}")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[DONE]")));

        // A streamed request must never fall back to the buffered path.
        verify(upstream, never()).forwardChatCompletion(any());
    }

    @Test
    void streamedBlockedRequestReturnsRefusalAsEventStreamWithoutForwarding() throws Exception {
        given(screening.screen(any())).willReturn(block());
        given(refusals.forDecision(any(), any())).willReturn("{\"bulwark\":{\"blocked\":true}}");

        MvcResult started = mvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stream\":true,\"messages\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"blocked\":true")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[DONE]")));

        // Blocked: nothing reaches upstream, on either path.
        verify(upstream, never()).streamChatCompletion(any());
        verify(upstream, never()).forwardChatCompletion(any());
    }

    @Test
    void nonStreamedCleanRequestStillForwardsBufferedJson() throws Exception {
        given(screening.screen(any())).willReturn(allow());
        given(upstream.forwardChatCompletion(any()))
                .willReturn(org.springframework.http.ResponseEntity.ok("{\"ok\":true}"));

        MvcResult started = mvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ok\":true")));

        verify(upstream, never()).streamChatCompletion(any());
    }

    @Test
    void flaggedRequestStillForwardsUpstream() throws Exception {
        // Flag mode records the detection but does not block, so the request is forwarded like a clean one.
        given(screening.screen(any())).willReturn(flag());
        given(upstream.forwardChatCompletion(any()))
                .willReturn(org.springframework.http.ResponseEntity.ok("{\"ok\":true}"));

        MvcResult started = mvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ok\":true")));

        verify(upstream).forwardChatCompletion(any());
    }
}
