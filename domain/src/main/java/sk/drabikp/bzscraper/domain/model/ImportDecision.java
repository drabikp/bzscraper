package sk.drabikp.bzscraper.domain.model;

/**
 * The user's answer for one {@link ImportProposal}.
 *
 * @param include whether to import this proposal at all
 * @param sameGig for a suggested match: true = one gig, false = import each copy on its own
 *                (ignored for proposals that are not suggestions)
 * @param chosen  the version whose details the catalog gig takes
 */
public record ImportDecision(ImportProposal proposal, boolean include, boolean sameGig,
                             ImportProposal.Version chosen) {

    /** The defaults: include it, accept non-suggested grouping, use the default version. */
    public static ImportDecision byDefault(ImportProposal proposal) {
        return new ImportDecision(proposal, true, !proposal.suggested(), proposal.defaultVersion());
    }
}
