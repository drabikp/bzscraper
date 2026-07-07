package sk.drabikp.bzscraper.domain.service;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.ReconciliationEntry;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain service: reconciles a set of imported gigs against the local catalog
 * by {@link GigId}. Each imported gig is NEW (no local match), MATCHED (identical),
 * or CONFLICT (same identity, different data); each unmatched local gig is
 * LOCAL_ONLY. Equality is full value-equality of the {@link Gig} aggregate, so any
 * difference surfaces as a conflict for the user to resolve.
 */
public final class GigReconciliation {

    private GigReconciliation() {
    }

    public static ReconciliationResult reconcile(Collection<Gig> local, Collection<Gig> imported) {
        Map<GigId, Gig> localById = indexById(local);
        Map<GigId, Gig> importedById = indexById(imported);

        List<ReconciliationEntry> entries = new ArrayList<>();
        for (Map.Entry<GigId, Gig> e : importedById.entrySet()) {
            Gig importedGig = e.getValue();
            Gig localGig = localById.get(e.getKey());
            if (localGig == null) {
                entries.add(ReconciliationEntry.added(importedGig));
            } else if (localGig.equals(importedGig)) {
                entries.add(ReconciliationEntry.matched(localGig, importedGig));
            } else {
                entries.add(ReconciliationEntry.conflict(localGig, importedGig));
            }
        }
        for (Map.Entry<GigId, Gig> e : localById.entrySet()) {
            if (!importedById.containsKey(e.getKey())) {
                entries.add(ReconciliationEntry.localOnly(e.getValue()));
            }
        }
        return new ReconciliationResult(entries);
    }

    private static Map<GigId, Gig> indexById(Collection<Gig> gigs) {
        Map<GigId, Gig> byId = new LinkedHashMap<>();
        for (Gig gig : gigs) {
            byId.putIfAbsent(gig.id(), gig); // first wins on duplicate identity within a source
        }
        return byId;
    }
}
