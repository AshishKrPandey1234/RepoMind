package com.repomind.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexJobStoreTest {
    @Mock
    private IndexJobRepository jobs;

    @Mock
    private CodeChunkRepository chunks;

    @InjectMocks
    private IndexJobStore store;

    @Test
    void ownerScopedLookupHidesAnotherUsersJob() {
        when(jobs.findByIdAndGithubUserId("job-1", "different-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.findOwned("job-1", "different-user"))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(jobs).findByIdAndGithubUserId("job-1", "different-user");
    }

    @Test
    void repositoryStatusReturnsLatestAttemptAndLatestSuccessfulSnapshotForOwner() {
        IndexJobEntity latest = new IndexJobEntity("job-new", "123", 42, "owner/repo", "feature", "sha-new");
        latest.start();
        latest.progress(8, 4, 2, 200);
        IndexJobEntity successful = new IndexJobEntity("job-old", "123", 42, "owner/repo", "main", "sha-old");
        successful.succeed(10, 8, 2, 20, 500);
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdOrderByCreatedAtDesc(42, "123"))
                .thenReturn(Optional.of(latest));
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "123", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(successful));

        RepositoryIndexStatusResponse status = store.findRepositoryStatus(42, "123");

        org.assertj.core.api.Assertions.assertThat(status.latestJob()).satisfies(job -> {
            org.assertj.core.api.Assertions.assertThat(job.id()).isEqualTo("job-new");
            org.assertj.core.api.Assertions.assertThat(job.status()).isEqualTo(IndexJobStatus.RUNNING);
            org.assertj.core.api.Assertions.assertThat(job.branch()).isEqualTo("feature");
            org.assertj.core.api.Assertions.assertThat(job.filesSkipped()).isEqualTo(2);
        });
        org.assertj.core.api.Assertions.assertThat(status.latestSuccessfulSnapshot()).satisfies(snapshot -> {
            org.assertj.core.api.Assertions.assertThat(snapshot.id()).isEqualTo("job-old");
            org.assertj.core.api.Assertions.assertThat(snapshot.branch()).isEqualTo("main");
            org.assertj.core.api.Assertions.assertThat(snapshot.commitSha()).isEqualTo("sha-old");
        });
        verify(jobs).findFirstByGithubRepositoryIdAndGithubUserIdOrderByCreatedAtDesc(42, "123");
    }

    @Test
    void repositoryStatusIsEmptyWhenUserHasNeverIndexedRepository() {
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdOrderByCreatedAtDesc(42, "123"))
                .thenReturn(Optional.empty());
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "123", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThat(store.findRepositoryStatus(42, "123"))
                .isEqualTo(new RepositoryIndexStatusResponse(null, null));
    }
}
