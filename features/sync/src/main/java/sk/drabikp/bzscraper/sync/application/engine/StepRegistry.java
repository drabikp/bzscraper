package sk.drabikp.bzscraper.sync.application.engine;

import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;
import sk.drabikp.bzscraper.sync.domain.SyncAction;
import sk.drabikp.bzscraper.sync.domain.SyncTask;
import sk.drabikp.bzscraper.sync.domain.Workflows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The sync steps each platform provides ({@link SyncStep}, at most one per type), and the
 * questions about them: which step of a workflow takes a gig first, and — when work is
 * queued — whether any does ({@link SyncAdmission}). A platform without a step of its own for
 * "remove and create again" gets one built from its remove and create steps.
 */
public class StepRegistry implements SyncAdmission {

    private final Map<Platform, Map<StepType, SyncStep>> steps = new HashMap<>();
    private final Platforms platforms;
    private final Transactions transactions;
    private final PublishedGigStore publishedGigStore;

    public StepRegistry(List<SyncStep> steps, Platforms platforms, Transactions transactions,
                        PublishedGigStore publishedGigStore) {
        for (SyncStep step : steps) {
            if (this.steps.computeIfAbsent(step.platform(), p -> new EnumMap<>(StepType.class))
                    .put(step.type(), step) != null) {
                throw new IllegalStateException("Two " + step.type() + " steps registered for " + step.platform());
            }
        }
        this.platforms = platforms;
        this.transactions = transactions;
        this.publishedGigStore = publishedGigStore;
    }

    @Override
    public Optional<String> leftOut(Platform platform, SyncAction action, Gig gig) {
        List<String> reasons = new ArrayList<>();
        return firstTaking(platform, Workflows.of(action).orElseThrow().getFirst(), gig, reasons).isPresent()
                ? Optional.empty() : Optional.of(byHand(platform, reasons));
    }

    /** The step a task waits at (the workflow's first before it started). */
    static StepType stepOf(SyncTask task) {
        return task.step() != null ? task.step() : Workflows.of(task.action()).orElseThrow().getFirst();
    }

    /**
     * From {@code from} on, the first step the platform has that takes the gig; why the others
     * didn't goes to {@code reasons}. Without the gig (deleted from the catalog), a step is
     * taken as it is — it was checked when the work was queued.
     */
    Optional<StepType> firstTaking(Platform platform, StepType from, Gig gig, List<String> reasons) {
        for (StepType type = from; type != null; type = Workflows.after(type).orElse(null)) {
            SyncStep step = step(platform, type);
            if (step == null) {
                reasons.add(type.label() + ": none for " + platforms.name(platform));
                continue;
            }
            Optional<String> refusal = gig == null ? Optional.empty() : step.refusal(gig);
            if (refusal.isEmpty()) {
                return Optional.of(type);
            }
            reasons.add(type.label() + ": " + refusal.get());
        }
        return Optional.empty();
    }

    String byHand(Platform platform, List<String> reasons) {
        return "nothing here can do this on " + platforms.name(platform) + " — do it there by hand ("
                + String.join("; ", reasons) + ")";
    }

    /** The platform's step of that type — for RECREATE, else built from its remove and create steps. */
    SyncStep step(Platform platform, StepType type) {
        Map<StepType, SyncStep> own = steps.getOrDefault(platform, Map.of());
        if (own.containsKey(type) || type != StepType.RECREATE) {
            return own.get(type);
        }
        List<SyncStep> removes = Stream.of(StepType.REMOVE, StepType.FORM_REMOVE)
                .map(own::get).filter(Objects::nonNull).toList();
        SyncStep create = own.containsKey(StepType.FORM_CREATE) ? own.get(StepType.FORM_CREATE)
                : own.get(StepType.BULK_CREATE);
        return removes.isEmpty() || create == null ? null : new Recreate(removes, create);
    }

    /**
     * Reactivating where the platform can't un-cancel: the cancelled copy is removed (and its
     * record forgotten at once — from then on, no record means a later Publish creates it),
     * then the gig is created again; the new id is recorded when this is settled. The copy is
     * removed the first of the platform's ways that takes the gig.
     */
    private final class Recreate implements SyncStep {

        private final List<SyncStep> removes;
        private final SyncStep create;

        Recreate(List<SyncStep> removes, SyncStep create) {
            this.removes = removes;
            this.create = create;
        }

        @Override
        public Platform platform() {
            return create.platform();
        }

        @Override
        public StepType type() {
            return StepType.RECREATE;
        }

        @Override
        public Optional<String> refusal(Gig gig) {
            return removing(gig).isPresent() ? create.refusal(gig) : removes.getFirst().refusal(gig);
        }

        private Optional<SyncStep> removing(Gig gig) {
            return removes.stream().filter(remove -> remove.refusal(gig).isEmpty()).findFirst();
        }

        @Override
        public List<StepOutcome> run(List<Item> items) {
            return items.stream().map(this::recreate).toList();
        }

        private StepOutcome recreate(Item item) {
            SyncStep remove = removing(item.gig()).orElse(removes.getLast());
            StepOutcome removed = remove.run(List.of(item)).getFirst();
            if (removed.kind() != StepOutcome.Kind.DONE) {
                return removed.kind() == StepOutcome.Kind.REFUSED ? removed
                        : new StepOutcome(removed.kind(), "could not remove the cancelled copy: " + removed.note(), null);
            }
            transactions.inTransaction(() -> publishedGigStore.remove(platform(), item.gig().id()));
            StepOutcome created = create.run(List.of(new Item(null, item.gig()))).getFirst();
            if (created.kind() == StepOutcome.Kind.DONE && created.ref() != null) {
                return created;
            }
            return StepOutcome.failedForGood("cancelled copy removed, but re-creating failed (" + created.note()
                    + ") — publish the gig again");
        }
    }
}
