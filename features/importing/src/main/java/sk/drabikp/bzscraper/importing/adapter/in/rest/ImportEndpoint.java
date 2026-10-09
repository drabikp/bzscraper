package sk.drabikp.bzscraper.importing.adapter.in.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.application.port.out.LiveUpdates;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Import over HTTP. Reading the platforms takes a while (a browser for some), so it runs in
 * the background: {@code POST /read} starts it, the page is told when it ended (live update
 * IMPORT) and fetches the plan; {@code POST /apply} imports what the user decided, naming the
 * plan's proposals by their index. One plan at a time; it is kept until applied or read again.
 */
@RestController
@RequestMapping("/api/import")
class ImportEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ImportEndpoint.class);

    private final ImportGigsUseCase importGigs;
    private final Platforms platforms;
    private final TaskExecutor executor;
    private final LiveUpdates live;
    private volatile boolean reading;
    private volatile String readError;
    private volatile ImportPlan plan;

    ImportEndpoint(ImportGigsUseCase importGigs, Platforms platforms, TaskExecutor executor, LiveUpdates live) {
        this.importGigs = importGigs;
        this.platforms = platforms;
        this.executor = executor;
        this.live = live;
    }

    record StateJson(List<String> platforms, boolean reading, String readError, PlanJson plan) {
    }

    record PlanJson(List<ProposalJson> proposals, int alreadyLinked, List<String> skipped,
                    Map<String, String> failures) {
    }

    /**
     * {@code versions}: the distinct details found, the catalog's first ({@code platform} null);
     * {@code foundOn}: the platforms with a copy.
     */
    record ProposalJson(int index, String date, boolean inCatalog, boolean suggested, boolean hasConflict,
                        List<String> foundOn, List<VersionJson> versions) {
    }

    record VersionJson(String platform, String title, String start, String end, String venue, String city,
                       boolean cancelled) {
    }

    record Read(List<String> platforms) {
    }

    /** {@code version}: the index into the proposal's versions whose details to keep. */
    record DecisionJson(int index, boolean include, boolean sameGig, int version) {
    }

    record Apply(List<DecisionJson> decisions) {
    }

    @GetMapping
    StateJson state() {
        ImportPlan current = plan;
        return new StateJson(importGigs.importablePlatforms().stream().map(Platform::id).toList(), reading, readError,
                current == null ? null : json(current));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.ACCEPTED)
    synchronized void read(@RequestBody Read request) {
        if (reading) {
            return;
        }
        Set<Platform> from = new LinkedHashSet<>();
        for (String id : request.platforms() == null ? List.<String>of() : request.platforms()) {
            from.add(platforms.find(id).orElseThrow(() -> new IllegalArgumentException("not a platform: " + id)));
        }
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
                log.error("Reading the platforms for an import failed", e);
                readError = e instanceof UserFacingException ? e.getMessage()
                        : "something went wrong — details are in the log";
            } finally {
                reading = false;
                live.changed(LiveUpdates.Topic.IMPORT);
            }
        });
    }

    @PostMapping("/apply")
    synchronized ImportResult apply(@RequestBody Apply request) {
        ImportPlan current = plan;
        if (current == null) {
            throw new UserFacingException("noPlan", Map.of(), "Read the platforms first.");
        }
        List<ImportDecision> decisions = new ArrayList<>();
        List<ImportProposal> proposals = current.proposals();
        for (DecisionJson d : request.decisions() == null ? List.<DecisionJson>of() : request.decisions()) {
            if (d.index() < 0 || d.index() >= proposals.size()) {
                throw new IllegalArgumentException("no proposal " + d.index());
            }
            ImportProposal proposal = proposals.get(d.index());
            List<ImportProposal.Version> versions = proposal.versions();
            ImportProposal.Version chosen = d.version() >= 0 && d.version() < versions.size()
                    ? versions.get(d.version()) : proposal.defaultVersion();
            decisions.add(new ImportDecision(proposal, d.include(), d.sameGig(), chosen));
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

    private PlanJson json(ImportPlan plan) {
        List<ProposalJson> proposals = new ArrayList<>();
        for (int i = 0; i < plan.proposals().size(); i++) {
            ImportProposal p = plan.proposals().get(i);
            proposals.add(new ProposalJson(i, p.date().toString(), p.inCatalog(), p.suggested(), p.hasConflict(),
                    p.copies().stream().map(c -> c.platform().id()).distinct().toList(),
                    p.versions().stream().map(v -> version(v.platform(), v.gig())).toList()));
        }
        Map<String, String> failures = new TreeMap<>();
        plan.failures().forEach((platform, why) -> failures.put(platform.id(), why));
        return new PlanJson(proposals, plan.alreadyLinked(), plan.skipped(), failures);
    }

    private static VersionJson version(Platform platform, Gig gig) {
        return new VersionJson(platform == null ? null : platform.id(), gig.title(),
                gig.schedule().start().toLocalDateTime().toString(),
                gig.schedule().end() == null ? null : gig.schedule().end().toLocalDateTime().toString(),
                gig.location().venue(), gig.location().city(), gig.cancelled());
    }
}
