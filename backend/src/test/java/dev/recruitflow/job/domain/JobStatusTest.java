package dev.recruitflow.job.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStatusTest {

    @Test
    void lifecycleFollowsItsTable() {
        assertThat(JobStatus.DRAFT.allowedNext()).containsExactlyInAnyOrder(JobStatus.OPEN, JobStatus.CLOSED);
        assertThat(JobStatus.OPEN.allowedNext()).containsExactlyInAnyOrder(JobStatus.ON_HOLD, JobStatus.CLOSED);
        assertThat(JobStatus.ON_HOLD.allowedNext()).containsExactlyInAnyOrder(JobStatus.OPEN, JobStatus.CLOSED);
        assertThat(JobStatus.CLOSED.allowedNext()).containsExactly(JobStatus.OPEN);
    }

    @Test
    void aDraftCannotBePutOnHoldAndNothingMovesToItself() {
        assertThat(JobStatus.DRAFT.canMoveTo(JobStatus.ON_HOLD)).isFalse();
        for (JobStatus status : JobStatus.values()) {
            assertThat(status.canMoveTo(status)).isFalse();
        }
    }
}
