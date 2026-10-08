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
    constructor) built from value objects `GigSchedule` (the whole event — a festival may
    span days — plus the band's own optional `Slot`), `Location`, `Admission`;
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
    `CalendarEventClassifier`, `CalendarGigDrafter`, `CalendarChanges`, `CalendarCatalogMatcher`,
    `SyncRetryPolicy`, `GigDateFilter`, `GigSummaryToGigMapper`.
- **application/** — use cases and the ports they depend on.
  - `port/in/` — catalog (`SaveGig`, `ListGigs`, `UpdateGig`, `CancelGig`,
    `DeleteGig` — each returns what it queued), `PublishGigsUseCase`, `ResyncGigUseCase`,
    `SyncLogUseCase`, `DispatchSyncUseCase`,
    `ImportGigsUseCase`, `ReviewCalendarUseCase`, `CalendarCatalogUseCase`, legacy scrape/CSV use cases.
  - `port/out/` — `GigRepository`, `PublishedGigStore`, `Transactions`, per-platform strategies
    `GigImporter`, workflow steps `SyncStep`, `PlaceSearch`,
    `BandzonePortalClient` + `BandzoneSession`, `BitPortalClient` + `BitSession`,
    `CalendarFeed`, `BandProfileStore`, `CalendarDecisionStore`, `CalendarSnapshotStore`,
    `CalendarLinkStore`, `SyncOutbox`, `SyncTrigger`,
    `SyncNotifier`, exceptions.
  - `service/` — `GigCatalogService`, `GigPublishingService`, `GigResyncService` (these
    queue platform work via `SyncRequests`), `SyncDispatcher` (runs it), `SyncLogService`,
    `GigImportService`, `CalendarReviewService`, `WorkflowEngine`, `PlaceService`, legacy
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
  `SeleniumBandzonePortalClient`, `BandzoneLineupPage`, `BandzoneFormEdit` (step),
  `BandzoneFormCreate`/`BandzoneCancel`/`BandzoneRemove` (steps), `StubBandzonePortalClient`); `csv/` (`OpenCsvGigExporter`, the
  manual-import download); `bandsintown/` (`BandsintownCsv` format, Selenium
  `SeleniumBitPortalClient` + `SeleniumBitSession`, `HumanPacer`, `Totp`,
  `BandsintownBulkCreate`/`BandsintownBulkEdit`/`BandsintownCancel`/`BandsintownRemove` (steps), `StubBitPortalClient`).
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

**Workflows** (`docs/sync-workflow-plan.md`): every task is a run of its action's workflow
(`Workflows`) in the `WorkflowEngine` — an ordered list of step types; platforms implement
`SyncStep`s, and what a platform can do is which steps it has (no flags, no config):

```
PUBLISH     bulk upload (BIT: BandsintownBulkCreate, 25 rows) → create form (BZ: BandzoneFormCreate)
UPDATE      bulk edit (BIT: BandsintownBulkEdit, no past events) → form edit (BZ: BandzoneFormEdit)
CANCEL      cancel (BZ: BandzoneCancel; BIT: BandsintownCancel = remove "canceled", no past events)
DELETE      remove (BZ: BandzoneRemove; BIT: BandsintownRemove, no past events) → record forgotten
REACTIVATE  remove and create again — built by the engine from the platform's remove + create
```

A step's `refusal(gig)` passes the gig on (also asked at queue time → "Left out: … by hand",
and a delete then forgets the copy). Outcomes: done (a created event's id recorded, a removed
one's forgotten — in the same transaction) / refused (the platform said no, nothing happened
→ next step) / failed (retry at the same step per `SyncRetryPolicy`; never for creates) /
failed for good (→ user). The run's step is `sync_task.step`; the path is logged ("bulk edit:
… → form edit"); due runs at one step go together (batch, or ≤10 one by one, each saved on
its own). Moot work (gig deleted/cancelled/published meanwhile) ends done with a note.
`SyncWorker` (one thread, woken on enqueue + every `bzscraper.sync.poll-seconds`) calls
`SyncDispatcher.runNext()` (→ engine) until nothing is due.

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
  restart, RUNNING repeatable tasks run again, others go FAILED ("check the platform").
  FAILED waits for the user: Retry (from the step where it stopped) / Discard on
  `/sync` (`SyncLogView`). `markRunning` only starts a still-PENDING task.
- A new task REPLACES the gig's FAILED tasks it redoes on that platform (`replaceFailed`):
  a new publish the failed publish, a new update the failed update, a delete the failed
  updates/cancels (never a failed publish — it may have created the event; the user checks).
- **Pause / Resume** on `/sync` (`PauseSyncUseCase`, `SyncPause`, in memory): the gig in
  progress finishes, nothing new starts, the waiting work stays queued.
- **Warm browser** (`WarmBrowser`): a platform's browser stays open ~60 s after a session, so
  the next step or task takes it over (one start + login check per run, not per gig); closed
  when idle and on shutdown; still one browser per platform at a time.
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

**Saved copy + changes**: each read is saved (`calendar_event`, `CalendarSnapshotStore`, notes
included — local DB only) and compared with the last one (`CalendarChanges`): NEW / CHANGED
(title, time, place, notes, status; plus what the rules said before, when the change made
them say something else) / REMOVED / RETURNED, kept until the user marks them seen. The very
first read is the baseline. The page shows the saved copy; "Read calendar" reads again.

**Into the catalog**: `CalendarCatalogUseCase` (same `CalendarReviewService`).
`CalendarGigDrafter` pre-fills the gig form from an event: show time from the
`SHOWTIME_LABEL` rule kind (the event's own start is the arrival; a show before 06:00 is the
next day), venue/city/country from the map-style place (`Venue, Street, 811 05
City-District, Country`). The user checks and saves → gig + `calendar_link` (event → GigId,
moved by `GigCatalogService.update` on an identity change). Unlinked gig events are matched
to the catalog's gigs that day (link one, or bulk-link where there is exactly one).
`CalendarCatalogMatcher` compares a LINKED upcoming gig with the calendar: another day,
another show time, cancelled in the calendar, gone from the calendar (a multi-day event
pairs on any of its days; the calendar's show is compared with the band's `Slot`, or offered
as the slot) — the page offers
Update / Cancel / Delete / Keep, always a user click through the catalog use cases (so the
platforms follow via the outbox); nothing is changed automatically.

## Places (towns)

Town names repeat (three Hranice, two Czech villages and a Slovak city called Košice), and
both platforms got it wrong with only a name. So a gig's `Location` carries an optional
`Address` (street, postal code, district "okres Přerov", region, the town's coordinates —
V9), filled when the town is picked from the place search: `PlaceSearch` →
`PhotonPlaceSearch` (OpenStreetMap Photon, free, no key; CZ/SK towns; its `county` is the
district as Bandzone writes it). The gig form's City is a town picker (typed towns still
allowed, flagged "not picked from the list"); the calendar pre-fill picks the town when
exactly one fits name + country + postal area (`TownChoice`).
- **Bandzone** has its own town list: `BandzoneTowns` picks the suggestion with the same name,
  country and district — several fits or none → a clear permanent error, never a guess.
- **Bandsintown** geocodes the uploaded text: rows carry street and postal code; after a
  publish the event's coordinates are compared with the town's (> 25 km → the task's note
  says "check the place there").

## Bandzone (Selenium)

`BandzoneGigProvider`/`BandzoneGigImporter` scrape read-only. Writes drive the
band-admin pages with Selenium; off by default (stub). Enable + configure:

```
bzscraper.bandzone.selenium.enabled=true
bzscraper.bandzone.login / .password / .band-slug     # NEVER hardcode in source
bzscraper.bandzone.selenium.chromium-binary=/usr/bin/chromium
bzscraper.bandzone.selenium.chromedriver=/usr/bin/chromedriver
bzscraper.bandzone.selenium.profile-dir              # default ~/.bzscraper/bandzone-browser/<account hash>
```

The real band's login lives in the git-ignored `.bz-creds` (line 1 login, line 2 password,
`chmod 600`); `.bz-test-creds` is the test band.

**Saved logins** (`BrowserProfile`, both platforms): the browser keeps the platform's login
in a profile directory that is private (`700`), one per Bandzone account (a test account can
never act as the real band), with cookies encrypted by the desktop keyring
(`bzscraper.browser.password-store=auto`, default: the keyring when a desktop session bus
is reachable, else Chromium's own store — on a server the private directory, owned by the
service user on an encrypted volume, is the protection). A still-valid saved login is
reused; the password (and the Bandsintown authenticator code) is used only when the
platform ended it. Deleting the profile directory logs out.

- **Create** — 2-step wizard: date/time/city → (optional "similar concerts" screen,
  answered "new") → info (name, entry, description, Facebook) → redirect to
  `/koncert/{id}-…`; the id is taken from that URL. `BandzoneFormCreate` then
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
- Not supported by Bandzone: un-cancel, removing a poster, ticket URL, per-band set times
  (Bandzone gets the whole event; the band's `Slot` goes to Bandsintown only).
- A city Bandzone's search doesn't know fails at once (permanent) with "correct the city".

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
- **When** — rows carry the band's `Slot` when the gig has one (`GigSchedule.showStart/showEnd`),
  else the event's start/end: Bandsintown is about when the artist plays.
- **Past events** — a published past gig is listed under Past Events, not Upcoming: the
  create read-back checks the past list too. Deleting a PAST event is not automated (refused,
  permanent: "delete it by hand"); an event in neither list counts as already removed. Edits
  of past events were refused with `INVALID_START_TIME`.
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
baselined at V1. V3 re-keys venue-less gigs (and their publications) to the `@city` identity; V4 adds the band-calendar tables (`calendar_rule`, `calendar_decision`); V5 the sync outbox (`sync_task`, `sync_log`); V6 the saved calendar copy and event → gig links (`calendar_event`, `calendar_link`); V7 the band's slot (`gig.slot_start/slot_end`); V8 a workflow run's step (`sync_task.step`); V9 the gig's address (`gig.street/postal_code/district/region/latitude/longitude`). The H2 version is pinned in `pom.xml` (`h2.version`) because its file
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
