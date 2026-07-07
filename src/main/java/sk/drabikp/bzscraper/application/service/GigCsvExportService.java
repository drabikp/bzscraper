package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.ExportGigsAsCsvUseCase;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;

public class GigCsvExportService implements ExportGigsAsCsvUseCase {

    private final GigRepository gigRepository;
    private final GigCsvExporter csvExporter;

    public GigCsvExportService(GigRepository gigRepository, GigCsvExporter csvExporter) {
        this.gigRepository = gigRepository;
        this.csvExporter = csvExporter;
    }

    @Override
    public String csvForCatalog() {
        return csvExporter.export(gigRepository.findAll());
    }
}
