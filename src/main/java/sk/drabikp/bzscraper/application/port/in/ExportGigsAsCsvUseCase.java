package sk.drabikp.bzscraper.application.port.in;

/** Exports the local gig catalog as a Bandsintown-compatible CSV. */
public interface ExportGigsAsCsvUseCase {

    String csvForCatalog();
}
