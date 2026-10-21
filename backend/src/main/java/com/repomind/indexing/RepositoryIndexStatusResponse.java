package com.repomind.indexing;

/** The latest attempt and the last usable snapshot are reported separately. */
public record RepositoryIndexStatusResponse(IndexJobResponse latestJob,
                                            IndexJobResponse latestSuccessfulSnapshot) {
}
