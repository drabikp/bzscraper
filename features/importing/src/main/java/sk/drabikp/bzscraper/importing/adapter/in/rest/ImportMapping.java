package sk.drabikp.bzscraper.importing.adapter.in.rest;

import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportApplyJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportPlanJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportProposalJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportResultJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportStateJson;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportVersionJson;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase.Choice;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase.State;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The import as the API has it (import.yaml); a proposal is named by its index in the plan. */
final class ImportMapping {

    private ImportMapping() {
    }

    static ImportStateJson toJson(State state) {
        return new ImportStateJson(state.platforms().stream().map(Platform::id).toList(), state.reading(),
                state.readError(), state.plan() == null ? null : toJson(state.plan()));
    }

    static ImportResultJson toJson(ImportResult result) {
        return new ImportResultJson(result.added(), result.updated(), result.linked(), result.skipped());
    }

    static List<Choice> choices(ImportApplyJson apply) {
        return apply.getDecisions() == null ? List.of() : apply.getDecisions().stream()
                .map(d -> new Choice(d.getIndex(), Boolean.TRUE.equals(d.getInclude()),
                        Boolean.TRUE.equals(d.getSameGig()), d.getVersion()))
                .toList();
    }

    private static ImportPlanJson toJson(ImportPlan plan) {
        List<ImportProposalJson> proposals = new ArrayList<>();
        for (int i = 0; i < plan.proposals().size(); i++) {
            ImportProposal p = plan.proposals().get(i);
            proposals.add(new ImportProposalJson(i, p.date(), p.inCatalog(), p.suggested(), p.hasConflict(),
                    p.copies().stream().map(c -> c.platform().id()).distinct().toList(),
                    p.versions().stream().map(v -> version(v.platform(), v.gig())).toList()));
        }
        Map<String, String> failures = new TreeMap<>();
        plan.failures().forEach((platform, why) -> failures.put(platform.id(), why));
        return new ImportPlanJson(proposals, plan.alreadyLinked(), plan.skipped(), failures);
    }

    private static ImportVersionJson version(Platform platform, Gig gig) {
        return new ImportVersionJson(platform == null ? null : platform.id(), gig.title(),
                gig.schedule().start().toLocalDateTime().toString(),
                gig.schedule().end() == null ? null : gig.schedule().end().toLocalDateTime().toString(),
                gig.location().venue(), gig.location().city(), gig.cancelled());
    }
}
