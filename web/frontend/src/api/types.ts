/**
 * The API's shapes under the names the pages use — all generated from the OpenAPI specs
 * (src/api/generated; each module's src/main/openapi/*.yaml). Nothing here is written by hand.
 */
import type { components as AuthApi } from './generated/auth';
import type { components as CalendarApi } from './generated/calendar';
import type { components as CatalogApi } from './generated/catalog';
import type { components as CheckApi } from './generated/check';
import type { components as GigApi } from './generated/gig';
import type { components as ImportApi } from './generated/import';
import type { components as LiveApi } from './generated/live';
import type { components as PlacesApi } from './generated/places';
import type { components as SyncApi } from './generated/sync';

type Shared = GigApi['schemas'];
type Catalog = CatalogApi['schemas'];
type Sync = SyncApi['schemas'];
type Calendar = CalendarApi['schemas'];
type Check = CheckApi['schemas'];
type Import = ImportApi['schemas'];

// the shared parts (gig.yaml)
export type Country = Shared['Country'];
export type EntryType = Shared['EntryType'];
/** A gig as the form holds it. Times are "HH:mm" (or "HH:mm:ss"). */
export type GigDraft = Shared['GigDraft'];
export type Problem = Shared['Problem'];

// the catalog (catalog.yaml)
export type Gig = Catalog['Gig'];
export type GigDetail = Catalog['GigDetail'];
export type GigHistory = Catalog['GigHistory'];
export type Edited = Catalog['GigEdited'];
export type PlatformState = Catalog['PlatformState'];
export type PlatformStateName = Catalog['PlatformStateName'];
export type Platform = Catalog['Platform'];
export type QueueResult = Catalog['QueueResult'];

// the sync (sync.yaml)
export type SyncAction = Sync['SyncAction'];
export type SyncStatusName = Sync['SyncTaskStatus'];
export type Task = Sync['Task'];
export type TaskLog = Sync['LogEntry'];
export type Breaker = Sync['Breaker'];
export type SyncStatus = Sync['SyncStatus'];

// the town picker (places.yaml)
export type Town = PlacesApi['schemas']['Town'];

// the band calendar (calendar.yaml)
export type CalendarKind = Calendar['CalendarKind'];
export type CalendarFilter = Calendar['CalendarFilter'];
export type CalendarRow = Calendar['CalendarRow'];
export type CalendarOverview = Calendar['CalendarOverview'];
export type CalendarRule = Calendar['Rule'];
export type GigRef = Calendar['GigRef'];
export type DifferenceKind = Calendar['MatchDifference']['kind'];

// the platform check (check.yaml)
export type Drift = Check['Drift'];
export type DriftField = Check['DriftField'];
export type DriftRef = Check['DriftRef'];
export type CheckState = Check['CheckState'];

// import (import.yaml)
export type ImportState = Import['ImportState'];
export type ImportProposal = Import['ImportProposal'];
export type ImportVersion = Import['ImportVersion'];
export type ImportDecision = Import['ImportDecision'];
export type ImportResult = Import['ImportResult'];

// signing in (auth.yaml) and the live updates (live.yaml)
export type Me = AuthApi['schemas']['Me'];
export type LiveTopic = LiveApi['schemas']['LiveTopic'];
