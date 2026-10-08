package sk.drabikp.bzscraper.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepType;
import sk.drabikp.bzscraper.domain.model.SyncAction;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Instant;

/** JPA persistence model for one sync-outbox task; enums stored by name, the gig id serialized. */
@Entity
@Table(name = "sync_task")
public class SyncTaskEntity {

    static final int TEXT_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String gigId;
    private String gigLabel;
    private String platform;
    private String action;
    private String status;
    private int attempts;
    private Instant createdAt;
    private Instant nextAttemptAt;
    private Instant updatedAt;
    private String message;
    private String step;
    /** Optimistic lock: two writers of one task can't both win (the later one fails). */
    @Version
    private Long version;

    protected SyncTaskEntity() {
        // for JPA
    }

    SyncTaskEntity(String gigId, String gigLabel, Platform platform, SyncAction action, Instant now) {
        this.gigId = gigId;
        this.gigLabel = clip(gigLabel, 500);
        this.platform = platform.id();
        this.action = action.name();
        this.status = SyncStatus.PENDING.name();
        this.attempts = 0;
        this.createdAt = now;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    SyncTask toTask() {
        return new SyncTask(id, GigEntityMapper.deserializeId(gigId), gigLabel, Platform.of(platform),
                SyncAction.valueOf(action), SyncStatus.valueOf(status), attempts, createdAt, nextAttemptAt,
                updatedAt, message, step == null ? null : StepType.valueOf(step));
    }

    Long getId() {
        return id;
    }

    SyncStatus status() {
        return SyncStatus.valueOf(status);
    }

    int getAttempts() {
        return attempts;
    }

    void moveTo(String newGigId, String newLabel) {
        this.gigId = newGigId;
        this.gigLabel = clip(newLabel, 500);
    }

    /** Applies the change if the status may make it ({@link SyncStatus#canBecome}); false = ignored. */
    boolean change(SyncStatus newStatus, Instant nextAttempt, String newMessage, Instant now) {
        if (!status().canBecome(newStatus)) {
            return false;
        }
        this.status = newStatus.name();
        this.nextAttemptAt = nextAttempt;
        this.message = clip(newMessage, TEXT_MAX);
        this.updatedAt = now;
        return true;
    }

    /** On to another step of the workflow: due now, attempts counted afresh for it. False = ignored. */
    boolean advance(StepType next, Instant now) {
        if (!status().canBecome(SyncStatus.PENDING)) {
            return false;
        }
        this.step = next.name();
        this.status = SyncStatus.PENDING.name();
        this.nextAttemptAt = now;
        this.attempts = 0;
        this.updatedAt = now;
        return true;
    }

    void startAttempt(Instant now) {
        this.status = SyncStatus.RUNNING.name();
        this.attempts++;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    void resetAttempts() {
        this.attempts = 0;
    }

    /** The attempt just started didn't happen after all. */
    void undoAttempt() {
        this.attempts = Math.max(0, attempts - 1);
    }

    static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
