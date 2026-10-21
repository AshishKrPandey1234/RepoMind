package com.repomind.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalChromaEndpointGuardTest {
    @Test
    void rejectsRemoteVectorStoreBecauseItReceivesSourceChunkText() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.ai.vectorstore.chroma.client.host", "http://localhost"))
                .thenReturn("https://vectors.example.com");

        assertThatThrownBy(() -> new LocalChromaEndpointGuard(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("spring.ai.vectorstore.chroma.client.host must use a local loopback endpoint.");
    }
}
