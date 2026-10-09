package sk.drabikp.bzscraper.importing.application;

import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The one import in progress ({@link ImportSessionUseCase}) over {@link ImportGigsUseCase}: the
 * read runs on the {@code executor}, its start and end are announced ({@link LiveUpdates.Topic#IMPORT}),
 * and so is the import itself (and the catalog it changed).
 */
public class ImportSession implements ImportSessionUseCase {

    private static final System.Logger log = System.getLogger(ImportSession.class.getName());

    private final ImportGigsUseCase importGigs;
    private final Executor executor;
    private final LiveUpdates live;
    private volatile boolean reading;
    private volatile String readError;
    private volatile ImportPlan plan;

    public ImportSession(ImportGigsUseCase importGigs, Executor executor, LiveUpdates live) {
        this.importGigs = importGigs;
        this.executor = executor;
        this.live = live;
    }

    @Override
    public State state() {
        return new State(importGigs.importablePlatforms(), reading, readError, plan);
    }

    @Override
    public synchronized void read(Collection<Platform> platforms) {
        if (reading) {
            return;
        }
        Set<Platform> from = new LinkedHashSet<>(platforms);
        if (from.isEmpty()) {
            throw new UserFacingException("noPlatform", Map.of(), "Pick at least one platform.");
        }
        reading = true;
        readError = null;
        live.changed(LiveUpdates.Topic.IMPORT);
        executor.execute(() -> {
            try {
                plan = importGigs.plan(from);
            } catch (RuntimeException e) {
                log.log(System.Logger.Level.ERROR, "Reading the platforms for an import failed", e);
                readError = e instanceof UserFacingException ? e.getMessage()
                        : "something went wrong — details are in the log";
            } finally {
                reading = false;
                live.changed(LiveUpdates.Topic.IMPORT);
            }
        });
    }

    @Override
    public synchronized ImportResult apply(List<Choice> choices) {
        ImportPlan current = plan;
        if (current == null) {
            throw new UserFacingException("noPlan", Map.of(), "Read the platforms first.");
        }
        List<ImportDecision> decisions = new ArrayList<>();
        for (Choice choice : choices) {
            decisions.add(decision(current.proposals(), choice));
        }
        if (decisions.stream().noneMatch(ImportDecision::include)) {
            throw new UserFacingException("nothingSelected", Map.of(), "Nothing selected to import.");
        }
        ImportResult result = importGigs.apply(decisions);
        plan = null;
        live.changed(LiveUpdates.Topic.IMPORT);
        live.changed(LiveUpdates.Topic.GIGS);
        return result;
    }

    /** The choice about a proposal; a version that isn't there keeps the proposal's default. */
    private static ImportDecision decision(List<ImportProposal> proposals, Choice choice) {
        if (choice.index() < 0 || choice.index() >= proposals.size()) {
            throw new IllegalArgumentException("no proposal " + choice.index());
        }
        ImportProposal proposal = proposals.get(choice.index());
        List<ImportProposal.Version> versions = proposal.versions();
        ImportProposal.Version chosen = choice.version() >= 0 && choice.version() < versions.size()
                ? versions.get(choice.version()) : proposal.defaultVersion();
        return new ImportDecision(proposal, choice.include(), choice.sameGig(), chosen);
    }
}
