package com.repomind.indexing;

import java.util.List;

public interface AnswerGenerator {
    GeneratedAnswer generate(String question, List<RepositoryAnswerSource> context);

    record GeneratedAnswer(String answer, List<String> sourceIds, boolean insufficientEvidence) { }
}
