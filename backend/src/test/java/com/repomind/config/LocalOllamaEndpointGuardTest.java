package com.repomind.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalOllamaEndpointGuardTest {
    @Test
    void allowsLoopbackHttpEndpoint() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.ai.ollama.base-url", "http://localhost:11434"))
                .thenReturn("http://127.0.0.1:11434");

        new LocalOllamaEndpointGuard(environment);

        assertThat(LocalOllamaEndpointGuard.isLocal("http://localhost:11434")).isTrue();
        assertThat(LocalOllamaEndpointGuard.isLocal("http://[::1]:11434")).isTrue();
    }

    @Test
    void rejectsRemoteOrCredentialBearingEndpointBeforeSpringAiCanSendSource() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.ai.ollama.base-url", "http://localhost:11434"))
                .thenReturn("https://user:pass@models.example.com");

        assertThatThrownBy(() -> new LocalOllamaEndpointGuard(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("spring.ai.ollama.base-url must use a local loopback endpoint.");
        assertThat(LocalOllamaEndpointGuard.isLocal("https://models.example.com")).isFalse();
    }
}
