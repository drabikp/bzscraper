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
  - `model/` — sync outbox types (`SyncTask`, `SyncAction`, `SyncStatus`, `SyncLogEntry`,
    `QueueResult`); `Gig` aggregate (immutable record; invariants in the compact
    constructor) built from value objects `GigSchedule`, `Location`, `Admission`;
    identified by `GigId` (start date + normalized venue, or `@` + city when the venue is
    unknown/"TBA" — it **changes** when an edit moves the date or venue). `CityName`
    compares city spellings across platforms (accents, "Prague" = "Praha", "Vsetín 1"). `Platform`, `PublishResult`/`PublishStatus`,
    `PlatformResult` (outcome of one update/withdraw on one platform),
    `WithdrawAction` (CANCEL/DELETE), `Publication` (a gig's copy on a platform), import
    types (`ImportedGig`, `ImportProposal`, `ImportPlan`, `ImportDecision`,
    `ImportResult`), band calendar types (`CalendarEvent`, `BandProfile` of `ProfileRule`s
    by `RuleKind`/`RuleOrigin`, `CalendarClassification`, `CalendarEventKind`/`Status`).
    `GigSummary` is the legacy scraped record.
  - `service/` — `ImportPlanner` (multi-platform import plan), `GigMerge`,
    `CalendarEventClassifier`, `SyncRetryPolicy`, `GigDateFilter`, `GigSummaryToGigMapper`.
- **application/** — use cases and the ports they depend on.
  - `port/in/` — catalog (`SaveGig`, `ListGigs`, `UpdateGig`, `CancelGig`,
    `DeleteGig` — each returns what it queued), `PublishGigsUseCase`, `ResyncGigUseCase`,
    `SyncLogUseCase`, `DispatchSyncUseCase`,
    `ImportGigsUseCase`, `ReviewCalendarUseCase`, legacy scrape/CSV use cases.
  - `port/out/` — `GigRepository`, `PublishedGigStore`, `Transactions`, per-platform strategies
    `GigPublisher` / `GigUpdater` / `GigWithdrawer` / `GigImporter`,
    `BandzonePortalClient` + `BandzoneSession`, `BitPortalClient` + `BitSession`,
    `CalendarFeed`, `BandProfileStore`, `CalendarDecisionStore`, `SyncOutbox`, `SyncTrigger`,
    `SyncNotifier`, exceptions.
  - `service/` — `GigCatalogService`, `GigPublishingService`, `GigResyncService` (these
    queue platform work via `SyncRequests`), `SyncDispatcher` (runs it), `SyncLogService`,
    `GigImportService`, `CalendarReviewService`, the publisher
    strategies `BandzoneGigPublisher` / `BandsintownGigPublisher`, legacy
    `GigQueryService` / `GigCsvExportService`.
- **adapter/in/** — `web/` Vaadin: `GigListView` (root route; the catalog grid with
  add/edit/cancel/reactivate/re-sync/delete/publish and a "Platforms" column linking
  each gig's platform page via `PlatformLinks` plus its live sync state), `SyncLogView`
  (`/sync`), `SyncBroadcaster`, `AddGigView`, `ImportView`, `CalendarView`,
  `GigForm`, `PublishSummaries`; `sync/` `SyncWorker`; `rest/` `GigSummaryEndpoint`.
- **adapter/out/** — `persistence/` (JPA `GigEntity`/`JpaGigRepository`,
  `PublishedGigEntity`/`JpaPublishedGigStore`, `JpaBandProfileStore`,
  `JpaCalendarDecisionStore`, `SpringTransactions`, `H2ScriptBackup`); `calendar/`
  (`IcsCalendarFeed` + `IcsParser`, `ProfileFile` rule format);
  `bandzone/` (scrape provider + importer, Selenium
  `SeleniumBandzonePortalClient`, `BandzoneLineupPage`, `BandzoneGigUpdater`,
  `BandzoneGigWithdrawer`, `StubBandzonePortalClient`); `csv/` (`OpenCsvGigExporter`, the
  manual-import download); `bandsintown/` (`BandsintownCsv` format, Selenium
  `SeleniumBitPortalClient` + `SeleniumBitSession`, `HumanPacer`, `Totp`,
  `BandsintownGigUpdater`, `BandsintownGigWithdrawer`, `StubBitPortalClient`).
- **config/** — `UseCaseConfiguration` wires POJO services as `@Bean`s; `BandProfileSync`
  loads the shipped + band calendar rules on start.

Services are plain POJOs wired explicitly in `UseCaseConfiguration`; adapters are
`@Component`s. `SyncDispatcher` collects its per-platform strategies via an injected
`List` into an `EnumMap<Platform, …>` (two beans for one platform = startup error), so
**a new platform is new strategy beans only** — orchestrators and UI don't change.

## Platform sync — outbox, eventually consistent

The catalog is the source of truth; the platforms follow it **eventually**. Every change
to a gig that is on a platform, and every Publish, writes its platform work as rows in the
**sync outbox** (`SyncOutbox`, table `sync_task`, history in `sync_log`) IN THE SAME
TRANSACTION as the catalog change (`SyncRequests`, called by `GigCatalogService`,
`GigPublishingService`, `GigResyncService`). The UI never waits for a platform.

`SyncWorker` (one background thread, woken on enqueue + every `bzscraper.sync.poll-seconds`)
calls `SyncDispatcher.runNext()` until nothing is due — one task at a time:

```
PUBLISH     a platform's due publishes run as ONE batch (one browser session);
            record the platform id per PUBLISHED gig (with DONE, one transaction);
            skipped (DONE + note) if the gig was deleted/cancelled/published meanwhile
UPDATE      GigUpdater.update(ref, gig as it is NOW) — later edits ride along
CANCEL      GigWithdrawer.withdraw(ref, CANCEL)
DELETE      GigWithdrawer.withdraw(ref, DELETE) → forget the record (with DONE)
REACTIVATE  delete the cancelled copy → forget → publishNew → record the new id
```

- Tasks work from the state when they RUN; work that became moot ends DONE with a note.
- Queueing rules (`SyncRequests`): no second UPDATE while one (or the PUBLISH) is PENDING;
  delete discards the gig's PENDING tasks and queues DELETE where it is published (the
  `published_gig` record outlives the catalog row until that succeeds); a pending CANCEL
  and REACTIVATE cancel out into an UPDATE; an identity change moves records AND tasks.
- `SyncRetryPolicy`: UPDATE/CANCEL/DELETE (repeatable) retry after 1, 5, 15 min, then
  FAILED; PUBLISH/REACTIVATE never auto-retry (a failure may have created the event);
  a PERMANENT failure fails at once — "not supported", a platform switched off, or the
  platform refusing the data (`permanent()` on the platform exceptions; Bandsintown's
  per-row errors like `INVALID_START_TIME`, seen when editing a past event). After a
  restart, RUNNING repeatable tasks run again, others go FAILED ("check the platform"). FAILED waits for the user: Retry / Discard on
  `/sync` (`SyncLogView`). `markRunning` only starts a still-PENDING task.
- UI: actions return a `QueueResult` (shown as "Queued: …"); the catalog's Platforms
  column and a summary line show queued/running/retrying/failed live (`SyncBroadcaster`
  → server push); actions on a gig whose task is RUNNING are blocked.
- Tests: `bzscraper.sync.worker.enabled=false` (tasks queue, never run); service tests
  use `SyncFakes` (in-memory outbox/records/repository, mutable clock).

## Import (`/import`)

Initial (and repeatable) import of the band's existing gigs, upcoming and past, from
any platform, LINKED to the platform events so they are managed from the catalog
instead of published again. `GigImporter` per platform returns `ImportedGig`s
(gig + platform id): Bandzone scrapes the public band page (year tabs + the "planned"
tab) keeping the concert id and cancelled state; Bandsintown reads the portal.
`ImportPlanner`: skip platform events already linked; group by `GigId` with the
catalog gig and across platforms; else SUGGEST a match on same date + city (only when
the candidate is unique) — the user confirms (default: unconfirmed = import each copy
on its own). Where versions differ (title/time/venue/city) the user picks which to
keep (default catalog > Bandzone > Bandsintown); `GigMerge` fills gaps from the other
versions. `GigImportService.apply` saves + records links in one transaction. Never
deletes.

## Band calendar (`/calendar`)

Read-only: the band's calendar (private iCal address, `bzscraper.calendar.ical-url` — a
SECRET, never in git; the local copy lives in the git-ignored `./calendar` file) mixes gigs
with rehearsals, travel, absences, calls. `CalendarEventClassifier` sorts each event into
GIG / UNSURE / NOT_GIG with the band's `BandProfile`: weighted `ProfileRule`s — the code
knows only rule KINDS, every band's words/labels/weights are DATA. Score ≥ 4 gig, ≤ −1
not a gig (unless there's evidence for a gig and nothing ≤ −4 against), between = the user
decides; `CATALOG_GIG_SAME_DAY` doesn't count after a strong negative. Status
(confirmed/tentative/cancelled) comes from separate role rules. The user's verdict per
event id (`calendar_decision`) always wins. Every matched rule is shown as a reason.

Rules: `classpath:calendar/base.profile` (language-independent) + presets
(`bzscraper.calendar.presets`, `sk-cz`) are re-synced as PRESET on start; the band's file
(`bzscraper.calendar.profile-file`, default `./data/calendar-profile.txt` — members' names,
never in git) as USER rules. Format: `KIND weight value|alternatives`. Steps 2–3
(learning the profile from platform history, setup wizard for any band) are planned in
`docs/calendar-plan.md`. Calendar notes hold fees/phones — never publish them.

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

## Bandsintown (Selenium)

Bandsintown has no write API for artists, and its internal API signs every request, so
the artist portal (artists.bandsintown.com) is driven in Chromium. Off by default (stub):

```
bzscraper.bandsintown.selenium.enabled=true
bzscraper.bandsintown.login / .password / .totp-secret   # authenticator base32 secret; NEVER in source
bzscraper.bandsintown.notify-followers=false              # default: publish silently
bzscraper.bandsintown.artist-id / .artist-name            # optional; to pick the artist
bzscraper.bandsintown.selenium.profile-dir                # default ~/.bzscraper/bandsintown-browser
bzscraper.bandsintown.pacing.min-ms / .max-ms             # human pauses between steps
```

- **Create** — Bulk Upload of a `BandsintownCsv` (template columns, ≤ 25 rows per upload)
  creates drafts; the upload reply maps CSV row → event id. In the success dialog
  "Notify my followers" is set from config, then Publish; the event list is read back
  to confirm PUBLISHED. Silent = "Do Not Announce = Y" + switch off (`announced_at`
  2000-01-01).
- **Update** — Bulk Upload of a row WITH `Event Id` (+ `Status`) edits the event in place.
- **List (import)** — Upcoming + Past tabs' event lists (past is paged: `x-next-page`,
  more load on scroll) → `BitEventMapper` (no entry info on Bandsintown).
- **Cancel/Delete** — Bandsintown has no cancelled state: both remove the event via the
  row's "⋯" → Delete, reason CANCELED or OTHER. Already-gone = success (so reactivate =
  delete no-op + create). Editing a cancelled gig skips Bandsintown.
- Replies are read by wrapping `window.fetch` in the page. The row to delete is found by
  its index in the captured event list and checked (city, day) before clicking.
- **Human pacing** (`HumanPacer`, user requirement): random pauses, real mouse clicks,
  key-by-key typing, one browser at a time, saved login reused (authenticator code only
  when the session expired), no automation flags / HeadlessChrome UA.
- Failures save a screenshot to `$TMPDIR/bzscraper-bandsintown-<step>.png`.

## Database

H2 file DB at `./data/bzscraper-gigs` (`bzscraper.db.path`). The schema is owned by
**Flyway** (`src/main/resources/db/migration/V<n>__*.sql`); Hibernate runs with
`ddl-auto=validate`, so every entity change needs a new migration (tests run the
migrations on an in-memory H2 and fail on a mismatch). A pre-Flyway database is
baselined at V1. V3 re-keys venue-less gigs (and their publications) to the `@city` identity; V4 adds the band-calendar tables (`calendar_rule`, `calendar_decision`); V5 the sync outbox (`sync_task`, `sync_log`). The H2 version is pinned in `pom.xml` (`h2.version`) because its file
format changes between versions. `H2ScriptBackup` writes a plain-SQL `SCRIPT` backup on
every start to `./data/backups` (one per day, newest 14 kept); restore with
`org.h2.tools.RunScript`.

## Key Dependencies

- Spring Boot 4.0.3, Java 21; Spring Data JPA + H2 2.4.240 (pinned) + Flyway
- Vaadin 25.0.5 (UI)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Selenium 4.27.0 (Bandzone publishing) — needs a Chromium + chromedriver runtime
- MapStruct 1.6.3

## Testing

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). Services/strategies are
unit-tested against mocked ports; the domain and CSV exporter have direct unit tests;
`JpaGigRepositoryTest` / `JpaPublishedGigStoreTest` cover persistence against the
migrated schema.

`SeleniumBandzonePortalClientLiveTest` creates, edits (every field, incl. lineup and
poster) and deletes a real Bandzone gig. Skipped unless `BZ_LIVE=true`; reads
`BZ_LOGIN` / `BZ_PASSWORD` / `BZ_SLUG`, `BZ_LINEUP_BAND` (a real band that gets
notified), optional `BZ_KEEP=true`, `BZ_CHROMIUM` / `BZ_CHROMEDRIVER`. Run it only
against a test band.

`SeleniumBitPortalClientLiveTest` creates a made-up Bandsintown event (published
WITHOUT notifying followers), edits it and deletes it. Skipped unless `BIT_LIVE=true`;
reads `BIT_LOGIN` / `BIT_PASSWORD` / `BIT_TOTP`; `BIT_CLEANUP_ID=<id>` only deletes a
leftover event. There is no Bandsintown test artist — the event is public for ~1 minute.

`CalendarClassificationLiveTest` prints how the shipped + band rules sort the REAL
calendar (read-only). Skipped unless `CAL_LIVE=true`; reads `CAL_URL`, optional
`CAL_PROFILE` (band rules file) and `CAL_CATALOG_DAYS` (file of ISO dates with gigs).
