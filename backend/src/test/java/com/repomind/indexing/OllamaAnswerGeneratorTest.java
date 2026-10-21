package com.repomind.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaAnswerGeneratorTest {
    @Test
    void requiresAnExplicitLocallyConfiguredChatModel() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        Environment environment = mock(Environment.class);
        when(environment.getProperty("repomind.ai.chat-model", "")).thenReturn(" ");
        OllamaAnswerGenerator generator = new OllamaAnswerGenerator(builder, new ObjectMapper(), environment);

        assertThatThrownBy(() -> generator.generate("question", java.util.List.of()))
                .isInstanceOf(RepositoryQuestionException.class)
                .hasMessage("LOCAL_CHAT_MODEL_NOT_CONFIGURED");
    }

    @Test
    void refusesToSendRepositoryContextToANonLoopbackOllamaEndpoint() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        Environment environment = mock(Environment.class);
        when(environment.getProperty("repomind.ai.chat-model", "")).thenReturn("some-chat-model");
        when(environment.getProperty("spring.ai.ollama.base-url", "http://localhost:11434"))
                .thenReturn("https://ai.example.com");
        OllamaAnswerGenerator generator = new OllamaAnswerGenerator(builder, new ObjectMapper(), environment);

        assertThatThrownBy(() -> generator.generate("question", java.util.List.of()))
                .isInstanceOf(RepositoryQuestionException.class)
                .hasMessage("LOCAL_CHAT_MODEL_UNAVAILABLE");
    }
}
