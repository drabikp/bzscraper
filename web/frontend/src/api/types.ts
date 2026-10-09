/** The API's shapes (see the *Endpoint classes of each feature's adapter.in.rest). */

export type Country = 'CZECHIA' | 'SLOVAKIA';
export type EntryType = 'FREE' | 'VOLUNTARY' | 'PAID';

/** A gig as the form holds it (the kernel's GigDraft). Times are "HH:mm" (or "HH:mm:ss"). */
export interface GigDraft {
  title: string;
  date: string | null;
  time: string | null;
  endDate: string | null;
  endTime: string | null;
  slotDate: string | null;
  slotTime: string | null;
  slotEndTime: string | null;
  venue: string | null;
  city: string | null;
  country: Country | null;
  street: string | null;
  postalCode: string | null;
  district: string | null;
  region: string | null;
  latitude: number | null;
  longitude: number | null;
  lineup: string[];
  entry: EntryType;
  price: string | null;
  description: string | null;
  facebookUrl: string | null;
  ticketUrl: string | null;
  posterUrl: string | null;
  cancelled: boolean;
}

export type PlatformStateName = 'live' | 'none' | 'queued' | 'running' | 'retrying' | 'failed';

export interface PlatformState {
  platform: string;
  state: PlatformStateName;
  ref: string | null;
  url: string | null;
  taskId: number | null;
  action: SyncAction | null;
  attempts: number;
  nextAttemptAt: string | null;
  message: string | null;
}

export interface Gig {
  id: string;
  rev: string;
  past: boolean;
  gig: GigDraft;
  platforms: PlatformState[];
}

export type SyncAction = 'PUBLISH' | 'UPDATE' | 'CANCEL' | 'DELETE' | 'REACTIVATE';
export type SyncStatusName = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'DISCARDED';

export interface GigHistory {
  taskId: number;
  platform: string;
  action: SyncAction;
  status: SyncStatusName;
  at: string;
  message: string | null;
}

export interface GigDetail {
  gig: Gig;
  history: GigHistory[];
}

export interface Platform {
  id: string;
  name: string;
  keepsCancelledEvents: boolean;
  listsBandSlot: boolean;
  carriesAdmission: boolean;
  exportable: boolean;
}

export interface QueueResult {
  queued: { taskId: number; platform: string; action: SyncAction }[];
  notQueued: string[];
}

export interface Edited {
  gig: Gig;
  queued: QueueResult;
}

export interface Task {
  id: number;
  gigId: string;
  gigLabel: string;
  platform: string;
  action: SyncAction;
  status: SyncStatusName;
  retrying: boolean;
  attempts: number;
  createdAt: string;
  updatedAt: string;
  nextAttemptAt: string | null;
  message: string | null;
  step: string | null;
}

export interface TaskLog {
  at: string;
  message: string;
}

export interface Breaker {
  platform: string;
  failures: number;
  heldUntil: string | null;
  held: boolean;
  trial: boolean;
  lastFailure: string | null;
}

export interface SyncStatus {
  paused: boolean;
  breakers: Breaker[];
  counts: { queued: number; running: number; retrying: number; failed: number };
}

export interface Town {
  name: string;
  district: string | null;
  region: string | null;
  postalCode: string | null;
  country: Country | null;
  latitude: number | null;
  longitude: number | null;
}

export type CalendarKind = 'GIG' | 'UNSURE' | 'NOT_GIG';
export type CalendarFilter = 'NEEDS_A_LOOK' | 'MISSING' | 'NOT_SURE' | 'GIGS' | 'NOT_GIGS' | 'DECIDED' | 'ALL';
export type DifferenceKind = 'DATE' | 'SHOW_TIME' | 'CANCELLED' | 'REMOVED';

export interface GigRef {
  id: string;
  title: string;
  start: string;
  city: string;
  cancelled: boolean;
}

export interface CalendarRow {
  eventId: string;
  title: string;
  location: string;
  notes: string;
  start: string;
  end: string;
  allDay: boolean;
  kind: CalendarKind;
  suggested: CalendarKind;
  decidedByUser: boolean;
  status: 'CONFIRMED' | 'TENTATIVE' | 'CANCELLED';
  score: number;
  reasons: { why: string; value: string | null; weight: number; text: string }[];
  draft: {
    title: string;
    date: string;
    showTime: string | null;
    eventStart: string | null;
    venue: string | null;
    city: string | null;
    country: Country | null;
    street: string | null;
    postalCode: string | null;
  };
  match: {
    state: 'LINKED' | 'SAME_DAY' | 'MISSING';
    gig: GigRef | null;
    sameDay: GigRef[];
    differences: { kind: DifferenceKind; text: string; calendar: string | null; catalog: string | null }[];
  };
  change: { type: 'NEW' | 'CHANGED' | 'REMOVED' | 'RETURNED'; fields: string[]; suggestedBefore: CalendarKind | null; at: string } | null;
  removed: boolean;
  missingFromCatalog: boolean;
  needsAttention: boolean;
  filters: CalendarFilter[];
}

export interface CalendarOverview {
  configured: boolean;
  lastRead: string | null;
  counts: {
    events: number;
    gigs: number;
    notSure: number;
    missing: number;
    changes: number;
    differences: number;
    linkable: number;
    decided: number;
  } | null;
  rows: CalendarRow[];
}

export interface CalendarRule {
  kind: string;
  value: string | null;
  weight: number;
  weighted: boolean;
  origin: string;
  enabled: boolean;
}

export type DriftField = 'DATE' | 'TIME' | 'NAME' | 'VENUE' | 'TOWN' | 'COUNTRY' | 'PLACE' | 'CANCELLED';

export interface Drift {
  platform: string;
  gigId: string;
  gigLabel: string;
  externalRef: string;
  kind: 'MISSING' | 'DIFFERENT';
  differences: { field: DriftField; catalog: string | null; platform: string | null; text: string }[];
}

export interface CheckState {
  running: boolean;
  last: {
    checkedAt: string;
    drifts: Drift[];
    unreadable: Record<string, string>;
    unlinked: Record<string, number>;
  } | null;
}

export interface ImportVersion {
  platform: string | null;
  title: string;
  start: string;
  end: string | null;
  venue: string | null;
  city: string;
  cancelled: boolean;
}

export interface ImportProposal {
  index: number;
  date: string;
  inCatalog: boolean;
  suggested: boolean;
  hasConflict: boolean;
  foundOn: string[];
  versions: ImportVersion[];
}

export interface ImportState {
  platforms: string[];
  reading: boolean;
  readError: string | null;
  plan: {
    proposals: ImportProposal[];
    alreadyLinked: number;
    skipped: string[];
    failures: Record<string, string>;
  } | null;
}

export interface ImportResult {
  added: number;
  updated: number;
  linked: number;
  skipped: number;
}

export interface Me {
  username: string;
}
