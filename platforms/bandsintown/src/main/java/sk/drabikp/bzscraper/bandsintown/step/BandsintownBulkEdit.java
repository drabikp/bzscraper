package sk.drabikp.bzscraper.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandsintown.BitUploadException;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.bandsintown.portal.BitSession;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepOutcome;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.time.Clock;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bandsintown's bulk edit: one CSV upload with the events' ids edits up to 25 events in
 * place. Past events are not taken — Bandsintown refuses uploads that edit them (seen:
 * {@code INVALID_START_TIME}, {@code INVALID_EVENT_ID}). A cancelled gig needs nothing:
 * cancelling removed it from Bandsintown.
 */
@Component
class BandsintownBulkEdit implements SyncStep {

    static final int MAX_ROWS = 25;

    private final BitPortalClient portalClient;
    private final Clock clock;

    public BandsintownBulkEdit(BitPortalClient portalClient, Clock clock) {
        this.portalClient = portalClient;
        this.clock = clock;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.BULK_EDIT;
    }

    @Override
    public int batchSize() {
        return MAX_ROWS;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return gig.isPast(clock)
                ? Optional.of("Bandsintown's upload doesn't take past events") : Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        StepOutcome[] outcomes = new StepOutcome[items.size()];
        List<Integer> uploaded = new ArrayList<>();
        List<Map.Entry<String, Gig>> edits = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.gig().cancelled()) {
                outcomes[i] = StepOutcome.done("cancelled — Bandsintown keeps no cancelled copy to edit");
            } else {
                uploaded.add(i);
                edits.add(new AbstractMap.SimpleEntry<>(item.externalRef(), item.gig()));
            }
        }
        if (edits.isEmpty()) {
            return List.of(outcomes);
        }
        try (BitSession session = portalClient.openSession()) {
            List<BitSession.Edited> edited = session.updateEvents(edits);
            for (int k = 0; k < uploaded.size(); k++) {
                BitSession.Edited result = k < edited.size() ? edited.get(k) : null;
                outcomes[uploaded.get(k)] = result == null ? StepOutcome.failed("no result for this event")
                        : result.outcome();
            }
        } catch (BitUploadException e) {
            StepOutcome failure = e.outcome();
            uploaded.forEach(i -> outcomes[i] = failure);
        }
        return Arrays.asList(outcomes);
    }
}
