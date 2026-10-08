package sk.drabikp.bzscraper.domain.model;

/**
 * Value object: how admission is priced. Invariant — PAID requires a non-blank
 * amount; FREE and VOLUNTARY carry no amount. Constructed only via the factories,
 * so an inconsistent admission (e.g. PAID with no price) cannot exist.
 */
public record Admission(EntryType type, String amount) {

    public Admission {
        if (type == null) {
            throw new IllegalArgumentException("admission type is required");
        }
        if (type == EntryType.PAID && (amount == null || amount.isBlank())) {
            throw new IllegalArgumentException("paid admission requires an amount");
        }
        if (type != EntryType.PAID && amount != null) {
            throw new IllegalArgumentException(type + " admission must not carry an amount");
        }
    }

    public static Admission free() {
        return new Admission(EntryType.FREE, null);
    }

    public static Admission voluntary() {
        return new Admission(EntryType.VOLUNTARY, null);
    }

    public static Admission paid(String amount) {
        return new Admission(EntryType.PAID, amount);
    }

    public boolean isPaid() {
        return type == EntryType.PAID;
    }
}
