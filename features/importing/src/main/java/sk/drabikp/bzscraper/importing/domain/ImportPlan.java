package sk.drabikp.bzscraper.importing.domain;

import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;
import java.util.Map;

/**
 * What an import would do, for the user to review before applying.
 *
 * @param proposals     gigs to add or link, newest first
 * @param alreadyLinked platform gigs already linked to the catalog (nothing to do)
 * @param skipped       platform gigs left out, each with the reason
 * @param failures      platforms that could not be read, with the reason
 */
public record ImportPlan(List<ImportProposal> proposals, int alreadyLinked, List<String> skipped,
                         Map<Platform, String> failures) {
}
