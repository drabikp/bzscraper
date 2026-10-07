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

A personal Spring Boot tool for a single band's gig admin — a **gig sync hub**. Gigs
are kept in a local catalog (H2 file DB, the source of truth), can be imported from
Bandzone.cz, and are **published to listing platforms** (Bandzone, Bandsintown). Edits,
cancel, reactivate and delete in the catalog are propagated to every platform the gig
was published to. A Vaadin UI drives it; the legacy scrape → Bandsintown CSV export and
a REST endpoint (`/gigs/{band_slug}`) still exist.

## Architecture

Hexagonal (ports & adapters) under `sk.drabikp.bzscraper`:

- **domain/** — framework-free core.
  - `model/` — `Gig` aggregate (immutable record; invariants in the compact
    constructor) built from value objects `GigSchedule`, `Location`, `Admission`;
    identified by `GigId` (start date + normalized venue — it **changes** when an edit
    moves the date or venue). `Platform`, `PublishResult`/`PublishStatus`,
    `PlatformResult` (outcome of one update/withdraw on one platform),
    `WithdrawAction` (CANCEL/DELETE), reconciliation types. `GigSummary` is the legacy
    scraped record.
  - `service/` — `GigReconciliation` (import diff), `GigDateFilter`,
    `GigSummaryToGigMapper`.
- **application/** — use cases and the ports they depend on.
  - `port/in/` — catalog (`SaveGig`, `ListGigs`, `UpdateGig`, `CancelGig`,
    `DeleteGig`), `PublishGigsUseCase`, `WithdrawGigsUseCase`, `ResyncGigUseCase`,
    `ImportGigsUseCase`, legacy scrape/CSV use cases.
  - `port/out/` — `GigRepository`, `PublishedGigStore`, per-platform strategies
    `GigPublisher` / `GigUpdater` / `GigWithdrawer` / `GigImporter`,
    `BandzonePortalClient` + `BandzoneSession`, `BitPortalClient`, exceptions.
  - `service/` — `GigCatalogService`, `GigPublishingService` (+ `PublishPartitioner`),
    `GigResyncService`, `GigWithdrawalService`, `GigImportService`, the publisher
    strategies `BandzoneGigPublisher` / `BandsintownGigPublisher`, legacy
    `GigQueryService` / `GigCsvExportService`.
- **adapter/in/** — `web/` Vaadin: `GigListView` (root route; the catalog grid with
  add/edit/cancel/reactivate/re-sync/delete/publish), `AddGigView`, `ImportView`,
  `GigForm`, `PublishSummaries`; `rest/` `GigSummaryEndpoint`.
- **adapter/out/** — `persistence/` (JPA `GigEntity`, `JpaGigRepository`);
  `store/` `TextFilePublishedGigStore` (TSV, default `~/.bzscraper/published-gigs.tsv`,
  `bzscraper.publish.state-file`); `bandzone/` (scrape provider + importer, Selenium
  `SeleniumBandzonePortalClient`, `BandzoneLineupPage`, `BandzoneGigUpdater`,
  `BandzoneGigWithdrawer`, `StubBandzonePortalClient`); `csv/`; `bandsintown/`
  `StubBitPortalClient`.
- **config/** — `UseCaseConfiguration` wires POJO services as `@Bean`s.

Services are plain POJOs wired explicitly in `UseCaseConfiguration`; adapters are
`@Component`s. Each orchestrator collects its per-platform strategies via an injected
`List` into an `EnumMap<Platform, …>` (two beans for one platform = startup error), so
**a new platform is new strategy beans only** — orchestrators and UI don't change.

## Platform sync

`PublishedGigStore` maps (Platform, GigId) → the platform's external id (`externalRef`,
e.g. the Bandzone concert id). Orchestrators own the store; strategies are pure push.

```
publish     GigPublishingService: skip already-published/invalid → GigPublisher.publishNew
            → record ref for PUBLISHED results only (idempotent re-runs)
edit        GigResyncService.pushEdit(previousId, gig): per platform published under
            previousId → move the record to the new GigId (same ref) → GigUpdater.update(ref, gig)
            (no updater → reported "update by hand")
re-sync     pushEdit(id, gig) with the same id — re-pushes the catalog state
cancel      GigWithdrawalService → GigWithdrawer.withdraw(ref, CANCEL)
delete      GigWithdrawer.withdraw(ref, DELETE) → forget the record
reactivate  GigResyncService.reactivate: Bandzone can't un-cancel, so DELETE the
            cancelled copy → forget → publishNew → record the new ref
```

The UI runs platform work on a `TaskExecutor` and pushes results back via `ui.access`.
Editing a cancelled gig keeps it cancelled (`GigForm.toGig()` always builds an active gig).

## Bandzone (Selenium)

`BandzoneGigProvider`/`BandzoneGigImporter` scrape read-only. Writes drive the
band-admin pages with Selenium; off by default (stub). Enable + configure:

```
bzscraper.bandzone.selenium.enabled=true
bzscraper.bandzone.login / .password / .band-slug     # NEVER hardcode in source
bzscraper.bandzone.selenium.chromium-binary=/usr/bin/chromium
bzscraper.bandzone.selenium.chromedriver=/usr/bin/chromedriver
```

- **Create** — 2-step wizard: date/time/city → (optional "similar concerts" screen,
  answered "new") → info (name, entry, description, Facebook) → redirect to
  `/koncert/{id}-…`; the id is taken from that URL. `BandzoneGigPublisher` then
  completes the gig via the edit form (end, venue, poster, lineup); if that fails the
  gig is still PUBLISHED with a note to use Re-sync.
- **Update** — `/koncert/{id}/update`: poster upload, city then venue autocomplete
  (venue search is scoped to the city; no exact club match → free-text venue), dates,
  info fields; then read back and verified. Autocomplete picks and the poster upload
  re-render the form via AJAX, so they run **before** plain fields and are followed
  by a staleness wait.
- **Lineup** (`BandzoneLineupPage`) — full sync: removes performers not in the lineup
  (never the band itself, whose name comes from the profile's `og:title`); adds an
  exact case-insensitive profile match, else a "band without profile" stub.
- Not supported by Bandzone: un-cancel, removing a poster, ticket URL. Per-band set
  times are not modeled.

Bandsintown has no write API; `StubBitPortalClient` is a placeholder until the
artist-portal flow is captured. Bandsintown has no updater/withdrawer yet.

## Key Dependencies

- Spring Boot 4.0.3, Java 21; Spring Data JPA + H2 (file DB `./data/`, `bzscraper.db.path`)
- Vaadin 25.0.5 (UI)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Selenium 4.27.0 (Bandzone publishing) — needs a Chromium + chromedriver runtime
- MapStruct 1.6.3

## Testing

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). Services/strategies are
unit-tested against mocked ports; the domain and CSV exporter have direct unit tests;
`JpaGigRepositoryTest` covers persistence.

`SeleniumBandzonePortalClientLiveTest` creates, edits (every field, incl. lineup and
poster) and deletes a real Bandzone gig. Skipped unless `BZ_LIVE=true`; reads
`BZ_LOGIN` / `BZ_PASSWORD` / `BZ_SLUG`, `BZ_LINEUP_BAND` (a real band that gets
notified), optional `BZ_KEEP=true`, `BZ_CHROMIUM` / `BZ_CHROMEDRIVER`. Run it only
against a test band.
