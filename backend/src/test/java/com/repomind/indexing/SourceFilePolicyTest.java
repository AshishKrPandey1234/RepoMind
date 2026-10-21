package com.repomind.indexing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFilePolicyTest {
    private final SourceFilePolicy policy = new SourceFilePolicy();

    @Test
    void excludesGeneratedSensitiveAndBinaryPaths() {
        assertThat(policy.evaluatePath("src/main/App.java").eligible()).isTrue();
        assertThat(policy.evaluatePath("node_modules/pkg/index.js").eligible()).isFalse();
        assertThat(policy.evaluatePath(".env.local").eligible()).isFalse();
        assertThat(policy.evaluatePath("assets/logo.png").eligible()).isFalse();
        assertThat(policy.evaluatePath("../outside.java").eligible()).isFalse();
    }

    @Test
    void rejectsBinaryAndLikelySecretContents() {
        assertThat(policy.evaluateContent(new byte[]{'a', 0, 'b'}).eligible()).isFalse();
        String secret = "api_secret='" + "made-up-long-demo-value" + "'";
        assertThat(policy.evaluateContent(secret.getBytes(StandardCharsets.UTF_8)).reason()).isEqualTo("LIKELY_SECRET");
        assertThat(policy.evaluateContent("class Example {}".getBytes(StandardCharsets.UTF_8)).text())
                .isEqualTo("class Example {}");
    }
}
