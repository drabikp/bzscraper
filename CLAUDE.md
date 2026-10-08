# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./mvnw clean install                                   # Build every module
./mvnw -pl app -am spring-boot:run                     # Run (port 8080; working dir = repo root)
./mvnw test                                            # Run all tests
./mvnw test -Dtest=GigPublishingServiceTest -Dsurefire.failIfNoSpecifiedTests=false   # One class
./mvnw -pl adapters/bandzone -am test                  # One module (and what it needs)
./mvnw -Pproduction -pl app -am package                # Production build (Vaadin frontend optimization)
```

**Modules** (Maven multi-module; packages unchanged, so a class's module follows its package):

```
domain/                 bzscraper-domain        framework-free model + rules; test-jar: TestGigs, TestPlatforms
application/            bzscraper-application   use cases, ports, workflow engine; framework-free;
                                                test-jar: SyncOutboxContract
adapters/persistence    JPA entities/stores, Flyway migrations (db/migration), H2 backup
adapters/browser        PlatformBrowser (a platform's one Chromium), BrowserProfile, WarmBrowser,
                        BrowserProperties/SeleniumOptions; test-jar: TestChromium
adapters/bandzone       Bandzone: PlatformTraits, scrape + Selenium client, sync steps, properties
adapters/bandsintown    Bandsintown: PlatformTraits, portal client, sync steps, properties + the
                        CSV download (adapter.out.csv)
adapters/calendar       iCal feed, profile file format, shipped presets (calendar/*.profile)
adapters/places         Photon town search
adapters/web            Vaadin views (the theme itself lives in app/src/main/frontend)
adapters/rest           REST endpoint (the catalog's gigs)
adapters/sync-worker    SyncWorker, PlatformCheckSchedule, sync/check properties
app/                    BzscraperApplication, config/ wiring, application.properties, Vaadin theme
                        + dev bundle (Vaadin takes app/ as its project folder)
```

Adapters depend on `application` (and `browser`), never on each other otherwise; only `app` sees
them all. Configuration is typed: a `@ConfigurationProperties` record per module
(`BandzoneProperties`, `BandsintownProperties`, `BrowserProperties`, `CalendarProperties`,
`SyncProperties`, `CheckProperties`, `BackupProperties`, `PlacesProperties`; secrets are
hidden in their `toString`); a platform switched on without its login stops the app at start. `spring-boot:run` runs in the repo root (`./data`, `./calendar` are relative to it).
Spring tests: `app` (whole context) and `persistence` (its own `PersistenceTestApplication`).

## What This Project Does

A personal Spring Boot tool for a single band's gig admin — a **gig sync hub**. Gigs
are kept in a local catalog (H2 file DB, the source of truth), can be imported from
Bandzone.cz, and are **published to listing platforms** (Bandzone, Bandsintown). Edits,
cancel, reactivate and delete in the catalog are propagated to every platform the gig
was published to. A Vaadin UI drives it; a per-platform file download (`GigExporter`, the
Bandsintown CSV) and a REST endpoint (`/gigs/{band_slug}`, the catalog's gigs) exist too.

## Architecture

Hexagonal (ports & adapters) under `sk.drabikp.bzscraper`. **The core is platform-agnostic**:
domain and application never name a platform or branch on one. A `Platform` is a value (an id
string); what a platform IS comes from its adapter — a `PlatformTraits` bean (display name,
keeps cancelled events, lists the band's slot, carries admission, import precedence, event
URL) and the `SyncStep`s / `GigImporter` / `GigExporter` beans it provides. `Platforms` (the
registry, in import-precedence order) is built from the traits beans. A new platform is a new
adapter module with those beans — the core, the engine and the UI don't change.

- **domain/** — framework-free core.
  - `model/` — `Gig` aggregate (immutable record; invariants in the compact constructor) built
    from value objects `GigSchedule` (the whole event — a festival may span days — plus the
    band's own optional `Slot`), `Location` (+ `Address`), `Admission`; identified by `GigId`
    (start date + normalized venue, or `@` + city when the venue is unknown/"TBA" — it
    **changes** when an edit moves the date or venue). `CityName` compares city spellings
    across platforms (accents, "Prague" = "Praha", "Vsetín 1"). `Platform`, `PlatformTraits`,
    `Platforms`; sync types (`SyncTask`, `SyncAction`, `SyncStatus` with its allowed
    transitions `canBecome`, `SyncLogEntry`, `StepType`, `StepOutcome`, `QueueResult`);
    `Publication` (a gig's copy on a platform); import types (`ImportedGig`, `ImportProposal`,
    `ImportPlan`, `ImportDecision`, `ImportResult`); platform check (`Drift`, `PlatformCheck`);
    band calendar types (`CalendarEvent`, `BandProfile` of `ProfileRule`s by
    `RuleKind`/`RuleOrigin`, `CalendarClassification`, `CalendarRow`, `CalendarFilter` — the
    calendar page's views and counts); `Town`.
  - `service/` — `Workflows` (each action's step types), `MootWork` (work that became
    unnecessary), `SyncRetryPolicy`, `ImportPlanner`, `GigMerge`, `Reconciler` (platform
    check), `CalendarEventClassifier`, `CalendarGigDrafter`, `CalendarChanges`,
    `CalendarCatalogMatcher`, `TownChoice`.
- **application/** — use cases and the ports they depend on.
  - `port/in/` — catalog (`SaveGig`, `ListGigs`, `UpdateGig`, `CancelGig`, `DeleteGig` — each
    returns what it queued), `PublishGigsUseCase`, `ResyncGigUseCase`, `ExportGigsUseCase`,
    `SyncLogUseCase`, `DispatchSyncUseCase`, `PauseSyncUseCase`, `PlatformBreakerUseCase`,
    `CheckPlatformsUseCase`, `ImportGigsUseCase`, `ReviewCalendarUseCase`,
    `CalendarCatalogUseCase`, `FindPlacesUseCase`, `ListPublicationsUseCase`, `SyncWorkSignal`;
    `UserFacingException` (a rule's refusal the user reads: `GigBusyException`,
    `GigIdentityTakenException`, `ConcurrentChangeException`).
  - `port/out/` — `GigRepository`, `PublishedGigStore`, `Transactions`, `SyncOutbox`,
    `SettingsStore`, `SyncTrigger`, `SyncNotifier`, per-platform strategies `SyncStep` (+
    `OneAtATimeStep`), `GigImporter`, `GigExporter`; `PlatformException` with its
    `FailureKind`; `PlaceSearch`, calendar ports (`CalendarFeed`, `BandProfileStore`,
    `CalendarDecisionStore`, `CalendarSnapshotStore`, `CalendarLinkStore`).
  - `service/` — `CatalogWrites` (THE write path for gigs, see below), `GigCatalogService`,
    `GigPublishingService`, `GigResyncService` (these queue platform work via `SyncRequests`),
    `StepRegistry` (the platforms' steps; also what may be queued: `SyncAdmission`),
    `WorkflowEngine` + `SyncDispatcher` (run it), `PlatformBreakers` (behind
    `PlatformHealth`), `SyncPause`, `SyncWakeUp`, `SyncLogService`, `GigImportService`,
    `GigExportService`, `PlatformCheckService`, `CalendarReviewService`, `PlaceService`.
- **adapter/in/** — `web/` Vaadin: `GigListView` (root route; the catalog grid with
  add/edit/cancel/reactivate/re-sync/delete/publish/download and a "Platforms" column linking
  each gig's platform page via `PlatformLinks` plus its live sync state), `SyncLogView`
  (`/sync`), `PlatformCheckView` (`/check`), `ImportView`, `CalendarView`, `AddGigView`,
  `GigForm`, `SyncBroadcaster`, `SyncLabels`, `UserErrors` (every view shows refusals and
  failures the same way; unexpected ones are logged, not shown raw); `sync/` `SyncWorker`,
  `PlatformCheckSchedule`; `rest/` `GigSummaryEndpoint`.
- **adapter/out/** — `persistence/` (JPA entities + stores: `JpaGigRepository`,
  `JpaPublishedGigStore`, `JpaSyncOutbox`, `JpaSettingsStore`, calendar stores,
  `SpringTransactions`, `H2ScriptBackup`); `browser/` (`PlatformBrowser`); `calendar/`
  (`IcsCalendarFeed` + `IcsParser`, `ProfileFile` rule format); `places/`
  (`PhotonPlaceSearch`); `bandzone/` (`BandzonePlatform` traits; scrape provider +
  importer; Selenium `SeleniumBandzonePortalClient` (login) + `SeleniumBandzoneSession` over
  page objects `BandzoneBrowser`, `BandzoneForm` (shared fields: town, club, info),
  `BandzoneCreateWizard`, `BandzoneUpdateForm`, `BandzoneDeleteTab`, `BandzoneLineupPage`;
  steps `BandzoneFormCreate`/`BandzoneFormEdit`/`BandzoneCancel`/`BandzoneRemove`;
  `StubBandzonePortalClient`); `bandsintown/` (`BandsintownPlatform` traits;
  `BandsintownCsv` format; Selenium `SeleniumBitPortalClient` (login) + `SeleniumBitSession`
  over page objects `BitPortal`, `BitEventsPage`, `BitBulkUpload`, `BitEventForm`,
  `BitDeleteDialog`; the portal's replies typed once: `PortalReplies` (capture) →
  `PortalReply`, `BitEvent`, `BitPlace`; `HumanPacer`, `Totp`, `RemovalReason`; steps
  `BandsintownBulkCreate`/`BandsintownBulkEdit`/`BandsintownFormEdit`/`BandsintownCancel`/
  `BandsintownRemove`/`BandsintownFormCancel`/`BandsintownFormRemove`;
  `StubBitPortalClient`); `csv/` (`OpenCsvGigExporter`, the manual-import download).
- **config/** — `UseCaseConfiguration` wires POJO services as `@Bean`s; `BandProfileSync`
  loads the shipped + band calendar rules on start.

Services are plain POJOs wired explicitly in `UseCaseConfiguration`; adapters are
`@Component`s. `StepRegistry` collects every `SyncStep` bean by (platform, step type) — two
beans for one slot = startup error — and `GigImportService`/`GigExportService` collect the
importers/exporters the same way.

**Catalog writes** (`CatalogWrites`): the add page, edits, the calendar and Import all write
gigs through it, so its rules hold for every writer — a gig never overwrites another (a new gig
or an identity move onto an existing gig → `GigIdentityTakenException`); an edit applies only
to the gig as the writer saw it (`ConcurrentChangeException`); a gig whose platform work is
RUNNING is not changed, deleted, re-synced or unlinked (`GigBusyException`); an identity move
takes the gig's platform records, queued work and calendar links along; an edit queues the
update everywhere the gig is published.

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
UPDATE      bulk edit (BIT: BandsintownBulkEdit, no past events) → form edit (BZ: BandzoneFormEdit;
            BIT: BandsintownFormEdit, one at a time — past events and refused rows)
CANCEL      cancel (BZ: BandzoneCancel; BIT: BandsintownCancel = remove "canceled", no past events)
            → cancel in the form (BIT: BandsintownFormCancel — past events)
DELETE      remove (BZ: BandzoneRemove; BIT: BandsintownRemove, no past events)
            → remove in the form (BIT: BandsintownFormRemove — past events) → record forgotten
REACTIVATE  remove and create again — built by the engine from the platform's remove (the
            first of remove / remove in the form that takes the gig) + create
```

A step's `refusal(gig)` passes the gig on (also asked at queue time → "Left out: … by hand",
and a delete then forgets the copy). Outcomes: done (a created event's id recorded, a removed
one's forgotten — in the same transaction) / refused (the platform said no, nothing happened
→ next step) / failed (retry at the same step per `SyncRetryPolicy`; never for creates) /
failed for good (→ user) / postponed (the platform's browser was busy with a read — again in a
minute, no attempt used, not counted by the breaker). The run's step is `sync_task.step`; the path is logged ("bulk edit:
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
  what a platform failure means is TYPED where it happens (`FailureKind` on
  `PlatformException` ← `BitUploadException`/`BandzoneUploadException`): TEMPORARY (retry),
  REFUSED (the platform said no this way — next step, e.g. Bandsintown's row error
  `INVALID_START_TIME` on a past event), NEEDS_USER (switched off or not configured, a town to
  correct, a draft left behind — fails at once), BUSY (postponed); steps return
  `e.outcome()`, and per-row replies (`BitSession.Created`/`Edited`) carry their kind too. After a
  restart, RUNNING repeatable tasks run again, others go FAILED ("check the platform").
  FAILED waits for the user: Retry (from the step where it stopped) / Discard on
  `/sync` (`SyncLogView`). `markRunning` only starts a still-PENDING task; every status change
  is checked against `SyncStatus.canBecome` (a DONE task stays done whoever writes late) and
  `sync_task.version` (V12) makes two concurrent writers fail instead of overwriting.
- A new task REPLACES the gig's FAILED tasks it redoes on that platform (`replaceFailed`):
  a new publish the failed publish, a new update the failed update, a delete the failed
  updates/cancels (never a failed publish — it may have created the event; the user checks).
- **Pause / Resume** on `/sync` (`PauseSyncUseCase`, `SyncPause`): the gig in progress
  finishes, nothing new starts, the waiting work stays queued; a pause survives a restart
  (`SettingsStore`, table `app_setting`).
- **Circuit breaker per platform** (`PlatformBreakers`, `PlatformBreakerUseCase`, in memory):
  a batch whose results are ALL temporary failures counts against its platform (anything
  else — done, refused, failed for good — shows the platform answering and resets it);
  `bzscraper.sync.breaker.failures` (3) in a row → the platform is held back for
  `.cooldown-minutes` (30): its tasks stay queued (`SyncOutbox.nextDue(now, skipping)`), the
  other platforms go on; then one trial batch decides. `/sync` shows it with "Resume <platform>".
- **One browser per platform** (`PlatformBrowser`): a sync step doesn't wait for a browser
  another operation holds (BUSY → postponed); a read the user started (Import, the platform
  check) waits up to 10 min. The browser stays warm ~60 s after a session (`WarmBrowser`), so
  the next step or task takes it over (one start + login check per run, not per gig); closed
  when idle and on shutdown. Failures leave a screenshot,
  `$TMPDIR/bzscraper-<platform>-<step>.png`.
- UI: actions return a `QueueResult` (shown as "Queued: …"); the catalog's Platforms
  column and a summary line show queued/running/retrying/failed live (`SyncBroadcaster`
  → server push); actions on a gig whose task is RUNNING are refused by the core
  (`CatalogWrites`), the views only grey the buttons.
- **Optimistic locking**: `UpdateGigUseCase.update(seen, updated)` — `seen` is the gig as the
  user opened it; if the catalog's gig is no longer that (changed in another window, by an
  import or the calendar, or deleted) nothing changes and `ConcurrentChangeException` says
  "changed meanwhile, reload". Two transactions saving one gig at once: `gig.version`
  (`@Version`, V10; `JpaGigRepository.save` carries the version it read) — the later commit
  fails, `SpringTransactions` turns it into the same exception.

## Platform check (`/check`) — reconciliation

The sync only knows what the app did; the platforms can drift by hand or by their own
guessing (a same-named town). `CheckPlatformsUseCase` (`PlatformCheckService`) reads every
platform through its `GigImporter` (read-only, the same reading as Import; a platform held
back by its breaker is skipped) and `Reconciler` (domain) compares each PUBLISHED gig of the
upcoming ones and the last `bzscraper.check.past-days` (60) with its copy there: MISSING (the
event is gone) or DIFFERENT — day, time (Bandsintown: the band's slot), name, venue (a
placeholder "-" is none; a longer platform name is fine), town ("Košice I" = Košice),
country, cancelled state (not on Bandsintown, which has none), and the place: Bandsintown
coordinates (`ImportedGig.latitude/longitude`) > 25 km from a town picked from the place
search. Gigs with sync work still queued are skipped. Fixes, always the user's click:
Re-sync (`ResyncGigUseCase`) or Forget link (a gone event; Publish creates it again). Also
counts platform events no gig is published as (→ Import). Runs nightly
(`PlatformCheckSchedule`, `bzscraper.check.cron`, default 04:30; `bzscraper.check.enabled`);
the result is kept in memory.

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
bzscraper.bandzone.login / .password / .band-slug     # required when enabled; NEVER in source
bzscraper.bandzone.selenium.chromium-binary / .chromedriver   # else bzscraper.browser.chromium-binary / .chromedriver
bzscraper.bandzone.selenium.profile-dir              # default ~/.bzscraper/bandzone-browser/<account hash>
```

The real band's login lives in the git-ignored `.bz-creds` (line 1 login, line 2 password,
`chmod 600`); `.bz-test-creds` is the test band.

**Saved logins** (`BrowserProfile`, both platforms): the browser keeps the platform's login
in a profile directory that is private (`700`), one per account (a test account can never act
as the real band), with cookies encrypted by the desktop keyring
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
bzscraper.bandsintown.login / .password / .totp-secret   # required when enabled (totp: authenticator base32); NEVER in source
bzscraper.bandsintown.artist-name                         # required (rows + CSV carry it; no default)
bzscraper.bandsintown.artist-id                           # optional; picks the artist on a multi-artist account
bzscraper.bandsintown.notify-followers=false              # default: publish silently
bzscraper.bandsintown.selenium.profile-dir                # default ~/.bzscraper/bandsintown-browser/<account hash>
bzscraper.bandsintown.pacing.min-ms / .max-ms             # human pauses between steps (900 / 2600)
```

- **Create** — Bulk Upload of a `BandsintownCsv` (template columns, ≤ 25 rows per upload)
  creates drafts; the upload reply maps CSV row → event id. In the success dialog
  "Notify my followers" is set from config, then Publish; the event list is read back
  to confirm PUBLISHED. Silent = "Do Not Announce = Y" + switch off (`announced_at`
  2000-01-01).
- **Update** — Bulk Upload of a row WITH `Event Id` (+ `Status`) edits the event in place.
- **Form edit** (`BandsintownFormEdit`, `editEventInForm`) — the event's single-page form
  (`/artists/<id>/events/<event>?version=single-page`), which opens for past events too:
  "Clear value" on the venue, type "venue town", pick the suggestion in the gig's town
  (`BitPlaces`, by Google `place_id`) and check the field shows it; then dates, times, name,
  description; Save → the reply's place is checked (`placeCheck`, > 25 km off → a note).
- **When** — rows carry the band's `Slot` when the gig has one (`GigSchedule.showStart/showEnd`),
  else the event's start/end: Bandsintown is about when the artist plays.
- **Past events** — a published past gig is listed under Past Events, not Upcoming: the
  create read-back checks the past list too. The list's "⋯ → Delete" doesn't reach PAST
  events, so they are removed through the form (below); an event in neither list counts as
  already removed. Bulk
  edits of past events are refused (`INVALID_START_TIME`) → the form edit does them.
- **List (import)** — Upcoming + Past tabs' event lists (past is paged: `x-next-page`,
  more load on scroll) → `BitEventMapper` (no entry info on Bandsintown).
- **Cancel/Delete** — Bandsintown has no cancelled state: both remove the event via the
  row's "⋯" → Delete, `RemovalReason` CANCELED or OTHER. Already-gone = success (so reactivate =
  delete no-op + create). Editing a cancelled gig skips Bandsintown.
- **Form cancel/remove** (`BandsintownFormCancel`/`BandsintownFormRemove`, `deleteEventInForm`)
  — past events (and whatever the list refused): the single-page form → Delete opens the same
  "remove this event?" dialog; its reason, details and Delete are looked up INSIDE the dialog
  (the form has dropdowns, text areas and a Delete of its own). A form that doesn't open for
  an event neither list shows = already gone.
- Replies are read by wrapping `window.fetch` in the page. The row to delete is found by
  its index in the captured event list and checked (city, day) before clicking.
- **Human pacing** (`HumanPacer`, user requirement): random pauses, real mouse clicks,
  key-by-key typing, one browser at a time, saved login reused (authenticator code only
  when the session expired), no automation flags / HeadlessChrome UA.
- Setup problems (no login, no authenticator secret, several artists, no artist id) need the
  user (NEEDS_USER), not a retry.

## Database

H2 file DB at `./data/bzscraper-gigs` (`bzscraper.db.path`). The schema is owned by
**Flyway** (`adapters/persistence/src/main/resources/db/migration/V<n>__*.sql`); Hibernate runs with
`ddl-auto=validate`, so every entity change needs a new migration (tests run the
migrations on an in-memory H2 and fail on a mismatch). A pre-Flyway database is
baselined at V1. V3 re-keys venue-less gigs (and their publications) to the `@city` identity; V4 adds the band-calendar tables (`calendar_rule`, `calendar_decision`); V5 the sync outbox (`sync_task`, `sync_log`); V6 the saved calendar copy and event → gig links (`calendar_event`, `calendar_link`); V7 the band's slot (`gig.slot_start/slot_end`); V8 a workflow run's step (`sync_task.step`); V9 the gig's address (`gig.street/postal_code/district/region/latitude/longitude`); V10 the gig's optimistic-lock version (`gig.version`); V11 platform ids as text (`published_gig.platform` was an H2 enum — platforms come from adapters now); V12 the sync task's version and `app_setting`. H2's `AUTO_SERVER` (a second process on the same file) is opt-in: `bzscraper.db.options=;AUTO_SERVER=TRUE`. The H2 version is pinned in `pom.xml` (`h2.version`) because its file
format changes between versions. `H2ScriptBackup` writes a plain-SQL `SCRIPT` backup on
every start to `./data/backups` (one per day, newest 14 kept); restore with
`org.h2.tools.RunScript`.

## Key Dependencies

- Spring Boot 4.0.3, Java 21; Spring Data JPA + H2 2.4.240 (pinned) + Flyway
- Vaadin 25.0.5 (UI)
- JSoup 1.22.1 (HTML scraping)
- OpenCSV 5.12.0 (CSV)
- Selenium 4.27.0 (Bandzone + Bandsintown) — needs a Chromium + chromedriver runtime

## Testing

JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`). The domain has direct unit tests;
service tests use `SyncFakes` (in-memory outbox/records/repository/settings, mutable clock,
builders for the registry, engine, dispatcher, requests and catalog) and `TestPlatforms`
(two made-up platforms' traits — the core tests never need the real adapters). The fake
outbox and `JpaSyncOutbox` both pass `SyncOutboxContract` (application test-jar), so the
fake can't drift from the real one. `bzscraper.sync.worker.enabled=false` in Spring tests
(tasks queue, never run). Persistence tests run the migrations on an in-memory H2
(`JpaGigConcurrencyTest`: the optimistic lock across real transactions).

**Page objects against copies of the platforms' pages** (headless Chromium from
`TestChromium`, browser test-jar; skipped where Chromium + chromedriver are missing, paths
`-Dbzscraper.test.chromium` / `-Dbzscraper.test.chromedriver`, default `/usr/bin/…`;
`PlatformBrowserTest` covers the browser lease itself): `BitPortalPagesTest` runs the Bandsintown
pages against `PortalFixture` — an in-process HTTP server with copies of the portal's list
and event-form pages (`src/test/resources/bandsintown-portal`, built like the real ones as
the probes saw them: the row "⋯" menu, the reason dialog, a form with its own Delete,
dropdowns and text areas, a debounced Google-places venue search) that plays the portal's
API, with the real reply capture. `BandzonePagesTest` does the same with `BandzoneFixture`
(`src/test/resources/bandzone-admin`: the edit form that re-renders after an autocomplete
pick and shows what was stored, the city search with three Hranice, the club search, the
delete tab with `confirm()`, the two-step wizard, the performers tab with its band search and
"band without profile" stub). Change a page object → run these first;
the live tests below then confirm against the real sites.

`SeleniumBandzonePortalClientLiveTest` creates, edits (every field, incl. lineup and
poster) and deletes a real Bandzone gig. Skipped unless `BZ_LIVE=true`; reads
`BZ_LOGIN` / `BZ_PASSWORD` / `BZ_SLUG`, `BZ_LINEUP_BAND` (a real band that gets
notified), optional `BZ_KEEP=true`, `BZ_CHROMIUM` / `BZ_CHROMEDRIVER`. Run it only
against a test band.

`SeleniumBitPortalClientLiveTest` creates a made-up Bandsintown event (published
WITHOUT notifying followers), edits it and deletes it. Skipped unless `BIT_LIVE=true`;
reads `BIT_LOGIN` / `BIT_PASSWORD` / `BIT_TOTP` / `BIT_ARTIST_NAME`; `BIT_CLEANUP_ID=<id>` only deletes a
leftover event. There is no Bandsintown test artist — the event is public for ~1 minute.

`CalendarClassificationLiveTest` prints how the shipped + band rules sort the REAL
calendar (read-only). Skipped unless `CAL_LIVE=true`; reads `CAL_URL`, optional
`CAL_PROFILE` (band rules file) and `CAL_CATALOG_DAYS` (file of ISO dates with gigs).
