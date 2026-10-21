package com.repomind.indexing;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SourceFilePolicy {
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            ".git", "node_modules", "vendor", "dist", "build", "target", "out", "coverage",
            ".next", ".nuxt", "bower_components", "pods", "generated", "__generated__"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "tar", "jar", "class",
            "exe", "dll", "so", "dylib", "woff", "woff2", "ttf", "otf", "mp3", "mp4", "mov",
            "sqlite", "db", "wasm", "bin", "7z", "rar"
    );
    private static final Pattern TOKEN = Pattern.compile(
            "(?i)(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16})"
    );
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"
    );
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?im)^\\s*(?:api[_-]?(?:key|secret|token)|client[_-]?secret|password|secret|token)\\s*[:=]\\s*['\"]([^'\"\\r\\n]{12,})['\"]\\s*(?:#.*)?$"
    );

    public PathDecision evaluatePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || hasParentSegment(path)) {
            return PathDecision.reject("INVALID_PATH");
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        String fileName = lowerPath.substring(lowerPath.lastIndexOf('/') + 1);
        for (String part : lowerPath.split("/")) {
            if (GENERATED_DIRECTORIES.contains(part)) {
                return PathDecision.reject("GENERATED_PATH");
            }
        }
        if (fileName.startsWith(".env") || fileName.equals(".npmrc") || fileName.equals(".pypirc")
                || fileName.contains("credential") || fileName.contains("secret")
                || fileName.endsWith(".pem") || fileName.endsWith(".key")
                || fileName.equals("id_rsa") || fileName.equals("id_ed25519")) {
            return PathDecision.reject("SENSITIVE_PATH");
        }
        if (fileName.endsWith(".min.js") || fileName.endsWith(".min.css")) {
            return PathDecision.reject("GENERATED_PATH");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && BINARY_EXTENSIONS.contains(fileName.substring(dot + 1))) {
            return PathDecision.reject("BINARY_EXTENSION");
        }
        return PathDecision.accept();
    }

    public ContentDecision evaluateContent(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return ContentDecision.reject("BINARY_CONTENT");
            }
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return ContentDecision.reject("BINARY_CONTENT");
        }
        if (TOKEN.matcher(content).find() || PRIVATE_KEY.matcher(content).find()
                || ASSIGNED_SECRET.matcher(content).find()) {
            return ContentDecision.reject("LIKELY_SECRET");
        }
        return ContentDecision.accept(content);
    }

    private boolean hasParentSegment(String path) {
        for (String part : path.split("/")) {
            if (part.equals("..") || part.equals(".")) return true;
        }
        return false;
    }

    public record PathDecision(boolean eligible, String reason) {
        static PathDecision accept() { return new PathDecision(true, null); }
        static PathDecision reject(String reason) { return new PathDecision(false, reason); }
    }

    public record ContentDecision(boolean eligible, String reason, String text) {
        static ContentDecision accept(String text) { return new ContentDecision(true, null, text); }
        static ContentDecision reject(String reason) { return new ContentDecision(false, reason, null); }
    }
}
