package com.repomind.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Chroma stores the chunk text as well as embeddings, so the current local-only setup must stay loopback-bound. */
@Component
public class LocalChromaEndpointGuard {
    public LocalChromaEndpointGuard(Environment environment) {
        String endpoint = environment.getProperty("spring.ai.vectorstore.chroma.client.host", "http://localhost");
        if (!LocalOllamaEndpointGuard.isLocal(endpoint)) {
            throw new IllegalStateException(
                    "spring.ai.vectorstore.chroma.client.host must use a local loopback endpoint.");
        }
    }
}
