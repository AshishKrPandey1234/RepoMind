package com.repomind.indexing;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds stable file and declaration context to each chunk before it is embedded. */
final class CodeEmbeddingText {
    private static final Pattern JAVA_DECLARATION = Pattern.compile(
            "\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)"
                    + "|\\b([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[\\w., ]+)?\\s*\\{");

    private CodeEmbeddingText() { }

    static String build(String filePath, String source) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String language = language(fileName);
        Set<String> declarations = new LinkedHashSet<>();
        if ("Java".equals(language)) {
            Matcher matcher = JAVA_DECLARATION.matcher(source);
            while (matcher.find() && declarations.size() < 12) {
                if (matcher.group(2) != null) declarations.add(matcher.group(1) + " " + matcher.group(2));
                else declarations.add("method " + matcher.group(3));
            }
        }
        return "Repository source file: " + fileName + "\nPath: " + filePath
                + "\nLanguage: " + language
                + (declarations.isEmpty() ? "" : "\nDeclarations: " + String.join(", ", declarations))
                + "\nSource code:\n" + source;
    }

    private static String language(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String extension = dot < 0 ? "" : lower.substring(dot + 1);
        return switch (extension) {
            case "java" -> "Java";
            case "js", "jsx", "mjs", "cjs" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "py" -> "Python";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "cs" -> "C#";
            case "kt", "kts" -> "Kotlin";
            case "sql" -> "SQL";
            case "yml", "yaml" -> "YAML";
            case "xml" -> "XML";
            case "md" -> "Markdown";
            default -> "text";
        };
    }
}
