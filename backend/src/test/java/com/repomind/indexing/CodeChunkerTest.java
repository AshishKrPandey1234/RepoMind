package com.repomind.indexing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {
    private final CodeChunker chunker = new CodeChunker();

    @Test
    void chunksOverlapAndKeepOneBasedSourceLineRanges() {
        var chunks = chunker.chunk("abcde\nfghij\nklmno", 8, 2);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).lineStart()).isEqualTo(1);
        assertThat(chunks.get(0).lineEnd()).isEqualTo(1);
        assertThat(chunks.get(1).lineStart()).isEqualTo(1);
        assertThat(chunks.get(1).lineEnd()).isEqualTo(2);
        assertThat(chunks.get(2).lineStart()).isEqualTo(2);
        assertThat(chunks.get(2).lineEnd()).isEqualTo(3);
        assertThat(chunks.get(0).content().substring(chunks.get(0).content().length() - 2))
                .isEqualTo(chunks.get(1).content().substring(0, 2));
    }

    @Test
    void normalizesCrLfWithoutChangingLineReferences() {
        var chunks = chunker.chunk("one\r\ntwo\r\nthree", 5, 1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.lineStart()).isGreaterThanOrEqualTo(1));
        assertThat(chunks.get(chunks.size() - 1).lineEnd()).isEqualTo(3);
    }
}
