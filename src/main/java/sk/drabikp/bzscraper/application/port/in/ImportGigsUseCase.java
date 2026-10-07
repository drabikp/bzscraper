package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.ImportDecision;
import sk.drabikp.bzscraper.domain.model.ImportPlan;
import sk.drabikp.bzscraper.domain.model.ImportResult;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;
import java.util.Set;

/**
 * Brings the band's existing gigs from the platforms into the catalog, linked to their
 * platform events — so they are managed from here (edit, cancel, re-sync) instead of
 * being published again. Two steps: {@link #plan} reads the platforms and proposes what
 * to add or link; {@link #apply} carries out the user's decisions. Import never deletes.
 */
public interface ImportGigsUseCase {

    Set<Platform> importablePlatforms();

    /** Reads the given platforms; a platform that can't be read is reported, not fatal. */
    ImportPlan plan(Set<Platform> platforms);

    ImportResult apply(List<ImportDecision> decisions);
}
