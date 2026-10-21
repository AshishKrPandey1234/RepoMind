package com.repomind.indexing;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class CodeChunker {

    public List<CodeChunk> chunk(String source, int maxChars, int overlapChars) {
        if (maxChars < 1 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("chunk limits are invalid");
        }
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        if (text.isEmpty()) return List.of();
        int[] newlinePositions = newlinePositions(text);

        List<CodeChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int tentativeEnd = Math.min(start + maxChars, text.length());
            int end = tentativeEnd;
            if (tentativeEnd < text.length()) {
                int newline = text.lastIndexOf('\n', tentativeEnd - 1);
                if (newline > start + maxChars / 2) end = newline + 1;
            }
            if (end <= start) end = tentativeEnd;

            String content = text.substring(start, end);
            int lineStart = lineAt(newlinePositions, start);
            int lineEnd = lineAt(newlinePositions, Math.max(start, end - 1));
            chunks.add(new CodeChunk(chunks.size(), content, lineStart, lineEnd, sha256(content)));
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlapChars);
        }
        return List.copyOf(chunks);
    }

    private int[] newlinePositions(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') count++;
        }
        int[] positions = new int[count];
        for (int index = 0, lineBreak = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') positions[lineBreak++] = index;
        }
        return positions;
    }

    private int lineAt(int[] newlinePositions, int position) {
        int low = 0;
        int high = newlinePositions.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (newlinePositions[middle] < position) low = middle + 1;
            else high = middle;
        }
        return low + 1;
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CodeChunk(int index, String content, int lineStart, int lineEnd, String sha256) {
    }
}
