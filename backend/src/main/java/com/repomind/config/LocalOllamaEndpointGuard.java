package com.repomind.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Prevents repository source and prompts from being sent to a remote Ollama endpoint. */
@Component
public class LocalOllamaEndpointGuard {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public LocalOllamaEndpointGuard(Environment environment) {
        String endpoint = environment.getProperty("spring.ai.ollama.base-url", "http://localhost:11434");
        if (!isLocal(endpoint)) {
            throw new IllegalStateException("spring.ai.ollama.base-url must use a local loopback endpoint.");
        }
    }

    public static boolean isLocal(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null && Set.of("http", "https").contains(scheme.toLowerCase(Locale.ROOT))
                    && uri.getUserInfo() == null && host != null
                    && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
