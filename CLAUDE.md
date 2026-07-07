# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./mvnw clean install                          # Build
./mvnw spring-boot:run                         # Run (port 8080)
./mvnw test                                     # Run all tests
./mvnw test -Dtest=GigPublishingServiceTest    # Run a single test class
./mvnw -Pproduction                            # Production build (Vaadin frontend optimization)
```

## What This Project Does

A personal Spring Boot tool for a single band's gig admin. It scrapes concert/gig
data from Bandzone.cz, and either exports it as a Bandsintown-compatible CSV or
**publishes gigs to listing platforms** (Bandzone, Bandsintown) so a gig is managed
in one place. A Vaadin web UI (root route) drives band search, date range, CSV
download, and publishing; a REST endpoint exposes gigs as JSON.

## Architecture

Hexagonal (ports & adapters) under `sk.drabikp.bzscraper`:

- **domain/** — framework-free core.
  - `model/` — `GigSummary` (scraped gig record), `DateRange`, `Platform`,
    `GigKey` (date + normalized venue, the idempotency identity), `PublishResult`,
    `PublishStatus`.
  - `service/` — `GigDateFilter` (pure date-range filter).
- **application/** — use cases and the ports they depend on.
  - `port/in/` — `GetGigsForBandUseCase`, `GetGigsBetweenDatesUseCase`,
    `ExportGigsAsCsvUseCase`, `PublishGigsUseCase`.
  - `port/out/` — `GigProvider` (scrape), `GigCsvExporter`, `UploadedGigStore`,
    `GigPublisher` (per-platform push strategy), `BitPortalClient`,
    `BandzonePortalClient`, and the `*UploadException` types.
  - `service/` — `GigQueryService`, `GigCsvExportService`, `GigPublishingService`
    (the publish orchestrator), the `PublishPartitioner` helper, and the two
    `GigPublisher` strategies (`BandsintownGigPublisher`, `BandzoneGigPublisher`).
- **adapter/in/** — `rest/` (`GigSummaryEndpoint` at `/gigs/{band_slug}`),
  `web/` (`GigsDownloadView` Vaadin UI, `AppShellConfig`).
- **adapter/out/** — `bandzone/` (`BandzoneGigProvider` + fetcher/parser scrape;
  `SeleniumBandzonePortalClient` real publisher; `StubBandzonePortalClient`),
  `csv/` (`OpenCsvGigExporter` → Bandsintown 28-col import format),
  `store/` (`TextFileUploadedGigStore` flat-file uploaded-set),
  `bandsintown/` (`StubBitPortalClient`).
- **config/** — `UseCaseConfiguration` wires POJO services as `@Bean`s.

Beans are wired explicitly in `UseCaseConfiguration` (services are plain POJOs);
adapters are `@Component`s.

## Publishing pipeline

```
PublishGigsUseCase.publish(Set<Platform>, gigs)
   └─ GigPublishingService (orchestrator, one per app)
        partition (dedup via UploadedGigStore) ─ skip invalid / already-uploaded
        dispatch by Platform → GigPublisher strategy:
            BandsintownGigPublisher → one CSV (GigCsvExporter) → BitPortalClient.uploadCsv   (batch)
            BandzoneGigPublisher    → per-gig → BandzonePortalClient.createGig                (loop)
        mark ONLY platform-confirmed gigs uploaded (idempotent re-runs, no double-post)
```

Idempotency uses a local "already-uploaded" set keyed by `GigKey` (no platform
IDs needed for create-only). Publishers are pure push and never touch the store —
the orchestrator owns dedup and marking.

**Adding a new platform = one class:** implement `GigPublisher`
(`platform()` + `publishNew(List<GigSummary>)`), register it as a `@Bean`/`@Component`.
The orchestrator, store, and UI do not change (`GigPublishingService` collects all
`GigPublisher` beans via injected `List`).

## Bandzone publishing (Selenium)

`BandzoneGigProvider` scrapes read-only. Writing gigs drives the band-admin 3-step
JS wizard with Selenium. Off by default (stub); enable + configure:

```
bzscraper.bandzone.selenium.enabled=true
bzscraper.bandzone.login / .password / .band-slug     # NEVER hardcode in source
bzscraper.bandzone.selenium.chromium-binary=/usr/bin/chromium
bzscraper.bandzone.selenium.chromedriver=/usr/bin/chromedriver
```

Bandsintown has no write API; `StubBitPortalClient` is a placeholder until the
artist-portal CSV-import flow is captured and a real Selenium client implemented.

## Key Dependencies

- Spring Boot 4.0.3, Java 21
- Vaadin 25.0.5 (UI)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Selenium 4.27.0 (Bandzone publishing) — needs a Chromium + chromedriver runtime
- MapStruct 1.6.3

## Testing

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). Services/strategies are
unit-tested against mocked ports; the domain and CSV exporter have direct unit
tests. `SeleniumBandzonePortalClientLiveTest` is an integration test that hits real
Bandzone and is skipped unless `BZ_LIVE=true` (reads creds from `BZ_LOGIN` /
`BZ_PASSWORD` / `BZ_SLUG` env vars).
