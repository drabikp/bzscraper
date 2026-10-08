# Platform sync as workflows (plan)

The catalog is the source of truth; platforms follow it through the sync outbox (see
CLAUDE.md, "Platform sync"). Today every platform action is ONE adapter method per platform
(publisher / updater / withdrawer). That can't express what the platforms really need:

- Bandsintown edits upcoming events by bulk CSV upload but refuses edits to past events
  (`INVALID_START_TIME`, `INVALID_EVENT_ID`) — maybe its edit form takes them.
- Bandzone has no bulk upload: everything goes one gig at a time through its forms.
- A future platform (Songkick, in the backlog) needs a venue lookup the user may have to
  confirm, and waits days for approval.

So each action becomes a **workflow**: an ordered list of **steps**, where every step is a
way of doing (part of) the work, and platforms contribute **step implementations**. What a
platform can do follows from which implementations exist — never from configuration.

## Concepts

- **Run** — one outbox task: gig × platform × action (publish, update, cancel, delete,
  reactivate). The outbox stays: runs are created in the same transaction as the catalog
  change; the engine runs inside the existing sync worker.
- **Workflow** — per action, in code: an ordered list of step types. Example:
  `update = bulk edit → form edit → by hand`.
- **Step type** — an interface for one kind of work (bulk edit, form edit, venue lookup,
  read-back, wait for approval). Its implementations are per platform; at each step the
  engine uses the implementation(s) the run's platform has, and skips the step when it has
  none.
- **Step contract**
  - `check(gig)` → *yes*, or *no + reason* ("past event"). Asked when queueing (so the user
    sees "left out" at once) and again before running (the gig may have changed).
  - `run(batch)` → one result per gig:
    - **done** (platform id / note),
    - **refused** — a definite per-gig "no" from the platform: the run moves to the next
      step, but ONLY for actions that are safe to repeat (edits); never for creates,
    - **failed** — technical (timeout, login lost, site changed): retry later at the same
      step, never move on — it may have half-happened,
    - **waiting** — check again at a given time (an approval),
    - **needs you** — a choice in the UI (e.g. which venue).
  - **mode**: BATCH (list in, per-gig results out, with a maximum size — Bandsintown 25
    rows) or SINGLE (one gig at a time; the engine loops). The workflow doesn't care: a
    batch step's leftovers are simply the next step's input.
  - **side effects**: whether a run interrupted inside the step may have changed the
    platform (create: yes → after a restart it stops and asks the user to check).
- **Session** — a step names the platform session it needs (the browser login); the engine
  opens it once per platform per pass and shares it across that platform's steps.
- **State** (DB): each run's current step, attempt, next check time, and the path so far;
  every step outcome is a sync-log line ("bulk edit: refused (INVALID_START_TIME) → edit
  form: done").

## What happens when the user syncs

Example: Re-sync of 5 gigs — all on Bandzone; on Bandsintown 3 upcoming + 2 past.

1. **Click → queue** (one transaction, immediate answer). One run per gig × platform. The
   workflow's steps are asked `check(gig)` for that platform; a run no step can do is not
   queued but reported ("Left out: … edit it on Bandsintown by hand"). The page shows
   "Queued: …", rows show ⏳, the worker wakes.
2. **Worker pass** — due runs grouped by platform and current step; one platform at a
   time (one browser per platform); its session is opened once.
3. **Batch step** (Bandsintown bulk edit) — takes the 3 upcoming gigs (the 2 past ones skip
   it: "bulk edit: not for past events"); one upload; per-row results: done ones are
   committed and show ✓ at once, a refused row moves to the next step. A failure of the
   whole upload moves nothing on — all 3 retry the bulk step later.
4. **Single step** (edit form) — the leftovers, one after another in the same session,
   with human pacing:
   - each gig's result is committed as soon as it's done (live progress "editing via form
     2 of 3"); a restart re-runs only the gig in progress (or asks to check it, for steps
     with side effects);
   - the gig is read fresh before its turn (edited or deleted meanwhile);
   - one gig's failure doesn't stop the others; a session-level failure (login lost) puts
     the rest back to wait at the same step;
   - a pass gives a platform at most ~10 gigs or ~10 minutes, then other platforms and new
     work get their turn; upcoming gigs before past ones.
5. **By hand** — whatever no step could do ends clearly marked with the reason and waits for
   the user on `/sync` (Retry / Discard).
6. **Bandzone** — its update workflow has no bulk step: all 5 go to its single form step in
   its own session.
7. **Result** — Platforms column ✓/✗ per platform with the reason, the summary line, and on
   `/sync` the path of each run. Retry restarts at the step where the run stopped (after
   `check(gig)` again). A Stop button ends the pass after the gig in progress.

## Replacing what exists

| Today | As steps |
| --- | --- |
| `GigPublisher` (BZ wizard + edit form; BIT CSV + publish dialog) | publish = create (BIT: batch, BZ: single) → complete (BZ: lineup/poster via edit form) → read back |
| `GigUpdater` | update = bulk edit (BIT) → form edit (BZ; BIT if its form takes past events) → by hand |
| `GigWithdrawer` CANCEL / DELETE | cancel = cancel (BZ form) / remove (BIT: no cancelled state) → by hand; delete = remove → by hand |
| `SyncDispatcher.reactivate` (delete + publish) | reactivate = un-cancel (none yet) → remove + create (the composed fallback) |
| `PlatformCapabilities` / adapter flags | gone: capability = some step accepts the gig |

The outbox tables stay; a run gets "current step" + "next check" columns.

## Order of work

1. Read-only check: does Bandsintown's edit form open (and allow changes) for a past event?
2. **Done (2026-10-08):** engine core (`WorkflowEngine`, `SyncStep`, `sync_task.step`) + the
   **update** workflow; Bandzone (form edit) and Bandsintown (bulk edit) on it. The edit form
   as a Bandsintown fallback waits for step 1. Not yet: a session shared by a platform's
   steps in one pass (each step opens its own; the saved login keeps that cheap), "waiting"
   and "needs you" outcomes, a Stop button.
3. Publish (with read-back), cancel, delete, reactivate moved onto it.
4. Backlog: Songkick (venue lookup + "needs you", bulk add for future dates, add/edit forms,
   approval wait, locked events → email).

## Not doing

No external engine (Temporal is a separate server; Flowable brings BPMN and its own tables;
Spring State Machine models states, not "try these ways in turn") — the app is one instance
driving a browser, and the engine's needs are small: steps, batching, waiting and state in
the existing database.
