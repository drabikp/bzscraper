package sk.drabikp.bzscraper.importing.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase.Choice;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms.BANDZONE;

class ImportSessionTest {

    private static final ZonedDateTime DAY = ZonedDateTime.of(2026, 10, 17, 20, 0, 0, 0, ZoneId.of("Europe/Prague"));

    private final Gig catalogs = TestGigs.gig("Fest", "Klub 007", DAY);
    private final Gig platforms = TestGigs.gig("Fest 2026", "Klub 007", DAY);
    /** The catalog's version and a platform's that differs: two versions. */
    private final ImportProposal proposal = new ImportProposal(catalogs,
            List.of(new ImportedGig(BANDZONE, platforms, "563406")), false);
    private final Fake importGigs = new Fake(new ImportPlan(List.of(proposal), 0, List.of(), Map.of()));
    private final List<Runnable> background = new ArrayList<>();
    private final List<LiveUpdates.Topic> announced = new ArrayList<>();
    private final ImportSession session = new ImportSession(importGigs, background::add, announced::add);

    @Test
    void the_platforms_are_read_in_the_background_and_the_start_and_end_are_announced() {
        session.read(List.of(BANDZONE));

        assertThat(session.state().reading()).isTrue();
        assertThat(session.state().plan()).isNull();
        session.read(List.of(BANDZONE));                       // a read already runs: nothing more
        assertThat(background).hasSize(1);

        background.getFirst().run();

        assertThat(importGigs.read).containsExactly(BANDZONE);
        assertThat(session.state().reading()).isFalse();
        assertThat(session.state().plan().proposals()).containsExactly(proposal);
        assertThat(announced).containsExactly(LiveUpdates.Topic.IMPORT, LiveUpdates.Topic.IMPORT);
    }

    @Test
    void a_read_that_failed_says_why_and_reading_needs_a_platform() {
        importGigs.fails = true;
        session.read(List.of(BANDZONE));
        background.getFirst().run();

        assertThat(session.state().readError()).isEqualTo("something went wrong — details are in the log");
        assertThatThrownBy(() -> session.read(List.of())).isInstanceOf(UserFacingException.class)
                .extracting("code").isEqualTo("noPlatform");
    }

    @Test
    void the_choices_name_the_plans_proposals_and_versions_by_index() {
        Executor now = Runnable::run;
        ImportSession session = new ImportSession(importGigs, now, announced::add);
        session.read(List.of(BANDZONE));

        session.apply(List.of(new Choice(0, true, true, 1)));

        ImportDecision decision = importGigs.applied.getFirst();
        assertThat(decision.proposal()).isEqualTo(proposal);
        assertThat(decision.include()).isTrue();
        assertThat(decision.chosen().platform()).isEqualTo(BANDZONE);
        assertThat(session.state().plan()).isNull();            // applied: read again for another import
        assertThat(announced).endsWith(LiveUpdates.Topic.IMPORT, LiveUpdates.Topic.GIGS);
    }

    @Test
    void a_version_that_isnt_there_keeps_the_default_and_an_unknown_proposal_is_a_bad_request() {
        ImportSession session = new ImportSession(importGigs, Runnable::run, LiveUpdates.NONE);
        session.read(List.of(BANDZONE));

        session.apply(List.of(new Choice(0, true, false, 7)));

        assertThat(importGigs.applied.getFirst().chosen()).isEqualTo(proposal.defaultVersion());
        session.read(List.of(BANDZONE));
        assertThatThrownBy(() -> session.apply(List.of(new Choice(3, true, false, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nothing_is_imported_without_a_plan_or_without_a_choice() {
        assertThatThrownBy(() -> session.apply(List.of(new Choice(0, true, false, 0))))
                .isInstanceOf(UserFacingException.class).extracting("code").isEqualTo("noPlan");

        ImportSession session = new ImportSession(importGigs, Runnable::run, LiveUpdates.NONE);
        session.read(List.of(BANDSINTOWN));
        assertThatThrownBy(() -> session.apply(List.of(new Choice(0, false, false, 0))))
                .isInstanceOf(UserFacingException.class).extracting("code").isEqualTo("nothingSelected");
        assertThat(importGigs.applied).isEmpty();
    }

    /** Reads the given plan; remembers what was read and applied. */
    private static final class Fake implements ImportGigsUseCase {

        private final ImportPlan plan;
        private final List<Platform> read = new ArrayList<>();
        private final List<ImportDecision> applied = new ArrayList<>();
        private boolean fails;

        Fake(ImportPlan plan) {
            this.plan = plan;
        }

        @Override
        public Set<Platform> importablePlatforms() {
            return Set.of(BANDZONE, BANDSINTOWN);
        }

        @Override
        public ImportPlan plan(Set<Platform> platforms) {
            if (fails) {
                throw new IllegalStateException("the portal changed");
            }
            read.addAll(platforms);
            return plan;
        }

        @Override
        public ImportResult apply(List<ImportDecision> decisions) {
            applied.addAll(decisions);
            return new ImportResult(0, 0, decisions.size(), 0);
        }
    }
}
