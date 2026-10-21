package com.repomind.indexing;

public class SemanticSearchException extends RuntimeException {
    private final int status;
    private final String code;

    public SemanticSearchException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
