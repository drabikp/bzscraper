package sk.drabikp.bzscraper.calendar.domain.rules;

/** Where a band-profile rule comes from — decides who may replace it. */
public enum RuleOrigin {
    /** Shipped with the app (language-independent signals, language presets). */
    PRESET,
    /** Learned from the band's own history; replaced on every re-learn. */
    LEARNED,
    /** Set by the band; never changed by learning. */
    USER
}
