package dev.recruitflow.pipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransitionPolicyTest {

    /** The approved table, written out independently of the code under test. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> EXPECTED = Map.of(
            ApplicationStatus.NEW, EnumSet.of(ApplicationStatus.SCREENING, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.SCREENING, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.INTERVIEW, EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.OFFER, EnumSet.of(ApplicationStatus.HIRED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN),
            ApplicationStatus.REJECTED, EnumSet.of(ApplicationStatus.NEW),
            ApplicationStatus.HIRED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));

    @Test
    void everyPairOfStatusesIsLegalExactlyWhenTheApprovedTableSaysSo() {
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                assertThat(TransitionPolicy.isLegal(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(EXPECTED.get(from).contains(to));
            }
        }
    }

    @Test
    void noStatusCanMoveToItself() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(TransitionPolicy.isLegal(status, status)).isFalse();
        }
    }

    @Test
    void hiredAndWithdrawnAreTerminalAndRejectedCanOnlyBeReopened() {
        assertThat(ApplicationStatus.HIRED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.WITHDRAWN.isTerminal()).isTrue();
        assertThat(ApplicationStatus.REJECTED.allowedNext()).containsExactly(ApplicationStatus.NEW);
        assertThat(ApplicationStatus.NEW.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"HIRED", "WITHDRAWN", "REJECTED"})
    void anyOpenStageCanBeRejectedOrWithdrawn(ApplicationStatus open) {
        assertThat(TransitionPolicy.isLegal(open, ApplicationStatus.REJECTED)).isTrue();
        assertThat(TransitionPolicy.isLegal(open, ApplicationStatus.WITHDRAWN)).isTrue();
    }

    @Test
    void onlyOfferHireAndReopenNeedTheOwnerOrAnAdmin() {
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : from.allowedNext()) {
                boolean restricted = to == ApplicationStatus.OFFER || to == ApplicationStatus.HIRED
                        || (from == ApplicationStatus.REJECTED && to == ApplicationStatus.NEW);
                assertThat(TransitionPolicy.needsOwnerOrAdmin(from, to)).as("%s -> %s", from, to).isEqualTo(restricted);
            }
        }
    }

    @Test
    void onlyRejectionRequiresAReason() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(TransitionPolicy.requiresReason(status)).isEqualTo(status == ApplicationStatus.REJECTED);
        }
    }
}
