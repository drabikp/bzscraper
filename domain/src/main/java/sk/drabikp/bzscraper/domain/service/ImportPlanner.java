package sk.drabikp.bzscraper.domain.service;

import sk.drabikp.bzscraper.domain.model.CityName;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.ImportPlan;
import sk.drabikp.bzscraper.domain.model.ImportProposal;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Platforms;
import sk.drabikp.bzscraper.domain.model.Publication;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure domain service: plans an import from one or more platforms into the catalog.
 * <ol>
 *   <li>Platform gigs already linked to the catalog (their platform id is recorded) are
 *       left alone.</li>
 *   <li>The rest are grouped by gig identity (date + venue): with the catalog gig of the
 *       same identity, and with the same gig from the other platform.</li>
 *   <li>A gig still on its own is <em>suggested</em> as the same concert as a catalog gig,
 *       or as a gig from the other platform, on the same date in the same city — but
 *       only when exactly one such candidate exists. The user confirms suggestions.</li>
 * </ol>
 * A platform gig whose identity is taken by another gig already linked on that platform
 * is skipped (a duplicate there), as importing it would overwrite the catalog gig.
 */
public final class ImportPlanner {

    private ImportPlanner() {
    }

    public static ImportPlan plan(Collection<Gig> catalog, Collection<Publication> publications,
                                  Collection<ImportedGig> imported, Map<Platform, String> failures,
                                  Platforms platforms) {
        Map<GigId, Gig> localById = new LinkedHashMap<>();
        catalog.forEach(g -> localById.putIfAbsent(g.id(), g));
        Set<String> linkedRefs = new HashSet<>();
        Map<GigId, Set<Platform>> linkedPlatforms = new HashMap<>();
        for (Publication p : publications) {
            if (p.hasExternalRef()) {
                linkedRefs.add(key(p.platform(), p.externalRef()));
            }
            linkedPlatforms.computeIfAbsent(p.gigId(), id -> new HashSet<>()).add(p.platform());
        }

        int alreadyLinked = 0;
        List<String> skipped = new ArrayList<>();
        Map<GigId, Group> withLocal = new LinkedHashMap<>();
        Map<GigId, Group> withoutLocal = new LinkedHashMap<>();

        List<ImportedGig> ordered = imported.stream()
                .sorted(Comparator.comparingInt((ImportedGig c) -> platforms.traits(c.platform()).importPrecedence())
                        .thenComparing(ImportedGig::platform)).toList();
        for (ImportedGig copy : ordered) {
            if (linkedRefs.contains(key(copy.platform(), copy.externalRef()))) {
                alreadyLinked++;
                continue;
            }
            GigId id = copy.gig().id();
            Gig local = localById.get(id);
            Map<GigId, Group> groups = local != null ? withLocal : withoutLocal;
            boolean takenOnPlatform = local != null
                    && linkedPlatforms.getOrDefault(id, Set.of()).contains(copy.platform());
            Group existing = groups.get(id);
            if (takenOnPlatform || (existing != null && existing.copies.containsKey(copy.platform()))) {
                skipped.add(describe(copy, platforms) + " — another " + platforms.name(copy.platform())
                        + " event already has this date and venue");
                continue;
            }
            groups.computeIfAbsent(id, k -> new Group(local)).copies.put(copy.platform(), copy);
        }

        suggestMatches(localById.values(), linkedPlatforms, withLocal, withoutLocal);

        List<ImportProposal> proposals = new ArrayList<>();
        withLocal.values().forEach(g -> proposals.add(g.toProposal(platforms)));
        withoutLocal.values().forEach(g -> proposals.add(g.toProposal(platforms)));
        proposals.sort(Comparator.comparing(ImportProposal::date).reversed());
        return new ImportPlan(proposals, alreadyLinked, skipped, failures);
    }

    private static void suggestMatches(Collection<Gig> catalog, Map<GigId, Set<Platform>> linkedPlatforms,
                                       Map<GigId, Group> withLocal, Map<GigId, Group> withoutLocal) {
        for (GigId id : new ArrayList<>(withoutLocal.keySet())) {
            Group lonely = withoutLocal.get(id);
            if (lonely == null || lonely.copies.size() != 1) {
                continue;
            }
            ImportedGig copy = lonely.copies.values().iterator().next();
            Platform platform = copy.platform();

            List<Gig> localCandidates = catalog.stream()
                    .filter(g -> sameDayAndCity(g, copy.gig()))
                    .filter(g -> !linkedPlatforms.getOrDefault(g.id(), Set.of()).contains(platform))
                    .filter(g -> !withLocal.containsKey(g.id()) || !withLocal.get(g.id()).copies.containsKey(platform))
                    .toList();
            if (localCandidates.size() == 1) {
                Gig local = localCandidates.getFirst();
                Group target = withLocal.computeIfAbsent(local.id(), k -> new Group(local));
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(id);
                continue;
            }
            if (!localCandidates.isEmpty()) {
                continue;                   // ambiguous: let the user sort it out by hand
            }

            List<Map.Entry<GigId, Group>> otherPlatform = withoutLocal.entrySet().stream()
                    .filter(e -> !e.getKey().equals(id))
                    .filter(e -> !e.getValue().copies.containsKey(platform))
                    .filter(e -> e.getValue().copies.values().stream().allMatch(c -> sameDayAndCity(c.gig(), copy.gig())))
                    .toList();
            if (otherPlatform.size() == 1) {
                Group target = otherPlatform.getFirst().getValue();
                target.copies.put(platform, copy);
                target.suggested = true;
                withoutLocal.remove(id);
            }
        }
    }

    private static boolean sameDayAndCity(Gig a, Gig b) {
        LocalDate dayA = a.schedule().startDate();
        return dayA.equals(b.schedule().startDate()) && CityName.same(a.location().city(), b.location().city());
    }

    private static String key(Platform platform, String ref) {
        return platform + ":" + ref;
    }

    private static String describe(ImportedGig copy, Platforms platforms) {
        Gig g = copy.gig();
        return platforms.name(copy.platform()) + " " + g.schedule().startDate() + " " + g.title()
                + " (" + g.location().displayVenue() + ", " + g.location().city() + ")";
    }

    private static final class Group {
        private final Gig local;
        private final Map<Platform, ImportedGig> copies = new HashMap<>();
        private boolean suggested;

        private Group(Gig local) {
            this.local = local;
        }

        private ImportProposal toProposal(Platforms platforms) {
            return new ImportProposal(local, copies.values().stream()
                    .sorted(Comparator.comparingInt((ImportedGig c) -> platforms.traits(c.platform()).importPrecedence())
                            .thenComparing(ImportedGig::platform))
                    .toList(), suggested);
        }
    }
}
