package sk.drabikp.bzscraper.importing.domain;

/** What applying an import did. {@code linked} counts platform events tied to catalog gigs. */
public record ImportResult(int added, int updated, int linked, int skipped) {
}
