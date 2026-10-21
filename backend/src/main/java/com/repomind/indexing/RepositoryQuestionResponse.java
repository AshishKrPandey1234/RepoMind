package com.repomind.indexing;

import java.util.List;

public record RepositoryQuestionResponse(long repositoryId, String repositoryFullName, String branch,
                                         String commitSha, String question, String answer,
                                         boolean insufficientEvidence, List<RepositoryAnswerSource> sources) { }
