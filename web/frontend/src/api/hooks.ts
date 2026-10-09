import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, data } from './client';
import type { CalendarKind, DriftRef, GigDraft, ImportDecision } from './types';

/**
 * One TanStack Query hook per API resource; every call is typed by the OpenAPI specs (paths,
 * parameters, bodies and answers). A change reads the affected parts again.
 */

// --- who is signed in --------------------------------------------------------------------

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await data(api.GET('/api/auth/me'));
      } catch {
        return null;
      }
    },
    staleTime: Infinity,
  });
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (login: { username: string; password: string; remember: boolean }) =>
      data(api.POST('/api/auth/login', { body: login })),
    onSuccess: (me) => {
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
      client.setQueryData(['me'], me);
    },
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => data(api.POST('/api/auth/logout')),
    onSettled: () => {
      client.setQueryData(['me'], null);
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
    },
  });
}

// --- the catalog ---------------------------------------------------------------------------

export function usePlatforms() {
  return useQuery({ queryKey: ['platforms'], queryFn: () => data(api.GET('/api/platforms')), staleTime: Infinity });
}

export function useGigs() {
  return useQuery({ queryKey: ['gigs'], queryFn: () => data(api.GET('/api/gigs')) });
}

export function useGig(id: string | undefined) {
  return useQuery({
    queryKey: ['gigs', id],
    queryFn: () => data(api.GET('/api/gigs/{id}', { params: { path: { id: id! } } })),
    enabled: !!id,
  });
}

/** After any change to the catalog: the lists, the gig and the sync state are read again. */
function useCatalogChanged() {
  const client = useQueryClient();
  return () => {
    client.invalidateQueries({ queryKey: ['gigs'] });
    client.invalidateQueries({ queryKey: ['sync'] });
    client.invalidateQueries({ queryKey: ['calendar'] });
  };
}

export function useAddGig() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (gig: GigDraft) => data(api.POST('/api/gigs', { body: gig })),
    onSuccess: changed,
  });
}

export function useEditGig() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (edit: { id: string; rev: string; gig: GigDraft }) =>
      data(api.PUT('/api/gigs/{id}', { params: { path: { id: edit.id } }, body: { rev: edit.rev, gig: edit.gig } })),
    onSuccess: changed,
  });
}

export function useGigAction() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'cancel' | 'reactivate' | 'delete' }) => {
      const params = { params: { path: { id } } };
      return data(action === 'delete' ? api.DELETE('/api/gigs/{id}', params)
        : action === 'cancel' ? api.POST('/api/gigs/{id}/cancel', params)
          : api.POST('/api/gigs/{id}/reactivate', params));
    },
    onSuccess: changed,
  });
}

export function usePublish() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (selection: { gigs: string[]; platforms: string[] }) =>
      data(api.POST('/api/gigs/publish', { body: selection })),
    onSuccess: changed,
  });
}

export function useResync() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (gigs: string[]) => data(api.POST('/api/gigs/resync', { body: { gigs } })),
    onSuccess: changed,
  });
}

export function useTowns(query: string) {
  return useQuery({
    queryKey: ['places', query],
    queryFn: () => data(api.GET('/api/places', { params: { query: { q: query } } })),
    enabled: query.trim().length >= 2,
    staleTime: 5 * 60_000,
  });
}

export function resolveTown(name: string, country: GigDraft['country'], postalCode: string | null) {
  return data(api.GET('/api/places/resolve', {
    params: { query: { name, country: country ?? undefined, postalCode: postalCode ?? undefined } },
  }));
}

// --- the sync ------------------------------------------------------------------------------

export function useSyncTasks(show: 'all' | 'open' | 'failed') {
  return useQuery({
    queryKey: ['sync', 'tasks', show],
    queryFn: () => data(api.GET('/api/sync/tasks', { params: { query: { show } } })),
  });
}

export function useTaskLog(id: number | null) {
  return useQuery({
    queryKey: ['sync', 'log', id],
    queryFn: () => data(api.GET('/api/sync/tasks/{id}/log', { params: { path: { id: id! } } })),
    enabled: id !== null,
  });
}

export function useSyncStatus() {
  return useQuery({ queryKey: ['sync', 'status'], queryFn: () => data(api.GET('/api/sync/status')) });
}

/** What the user can tell the sync. */
export type SyncCommand =
  | { kind: 'retry'; task: number }
  | { kind: 'discard'; task: number }
  | { kind: 'pause' }
  | { kind: 'resume' }
  | { kind: 'resumePlatform'; platform: string };

function syncCall(command: SyncCommand) {
  switch (command.kind) {
    case 'retry':
      return api.POST('/api/sync/tasks/{id}/retry', { params: { path: { id: command.task } } });
    case 'discard':
      return api.POST('/api/sync/tasks/{id}/discard', { params: { path: { id: command.task } } });
    case 'pause':
      return api.POST('/api/sync/pause');
    case 'resume':
      return api.POST('/api/sync/resume');
    case 'resumePlatform':
      return api.POST('/api/sync/breakers/{platform}/resume', { params: { path: { platform: command.platform } } });
  }
}

export function useSyncAction() {
  const changed = useCatalogChanged();
  return useMutation({ mutationFn: (command: SyncCommand) => data(syncCall(command)), onSuccess: changed });
}

// --- the calendar --------------------------------------------------------------------------

export function useCalendar() {
  return useQuery({ queryKey: ['calendar'], queryFn: () => data(api.GET('/api/calendar')) });
}

export function useCalendarRules(enabled: boolean) {
  return useQuery({
    queryKey: ['calendar', 'rules'],
    queryFn: () => data(api.GET('/api/calendar/rules')),
    enabled,
  });
}

export function useReadCalendar() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => data(api.POST('/api/calendar/read')),
    onSuccess: (overview) => client.setQueryData(['calendar'], overview),
  });
}

/** What the user can tell about the calendar's events. */
export type CalendarCommand =
  | { kind: 'decide'; event: string; verdict: CalendarKind }
  | { kind: 'forget'; event: string }
  | { kind: 'seen'; event: string }
  | { kind: 'seenAll' }
  | { kind: 'link'; event: string; gig: string }
  | { kind: 'unlink'; event: string }
  | { kind: 'linkSameDay' };

/** The answer: how many were linked (link-same-day), nothing otherwise. */
async function calendarCall(command: CalendarCommand): Promise<{ linked: number } | undefined> {
  const event = (eventId: string) => ({ params: { path: { eventId } } });
  switch (command.kind) {
    case 'decide':
      return data(api.POST('/api/calendar/events/{eventId}/decide', { ...event(command.event), body: { kind: command.verdict } }));
    case 'forget':
      return data(api.POST('/api/calendar/events/{eventId}/forget', event(command.event)));
    case 'seen':
      return data(api.POST('/api/calendar/events/{eventId}/seen', event(command.event)));
    case 'seenAll':
      return data(api.POST('/api/calendar/seen'));
    case 'link':
      return data(api.POST('/api/calendar/events/{eventId}/link', { ...event(command.event), body: { gig: command.gig } }));
    case 'unlink':
      return data(api.POST('/api/calendar/events/{eventId}/unlink', event(command.event)));
    case 'linkSameDay':
      return data(api.POST('/api/calendar/link-same-day'));
  }
}

export function useCalendarAction() {
  const changed = useCatalogChanged();
  return useMutation({ mutationFn: calendarCall, onSuccess: changed });
}

export function useAddFromCalendar() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ eventId, gig }: { eventId: string; gig: GigDraft }) =>
      data(api.POST('/api/calendar/events/{eventId}/gig', { params: { path: { eventId } }, body: { gig } })),
    onSuccess: changed,
  });
}

// --- the platform check and import ---------------------------------------------------------

export function useCheck() {
  return useQuery({ queryKey: ['check'], queryFn: () => data(api.GET('/api/check')) });
}

/** What the user can tell the platform check. */
export type CheckCommand = { kind: 'run' } | { kind: 'forget'; ref: DriftRef } | { kind: 'dismiss'; ref: DriftRef };

function checkCall(command: CheckCommand) {
  switch (command.kind) {
    case 'run':
      return api.POST('/api/check/run');
    case 'forget':
      return api.POST('/api/check/forget', { body: command.ref });
    case 'dismiss':
      return api.POST('/api/check/dismiss', { body: command.ref });
  }
}

export function useCheckAction() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (command: CheckCommand) => data(checkCall(command)),
    onSuccess: () => client.invalidateQueries({ queryKey: ['check'] }),
  });
}

export function useImport() {
  return useQuery({ queryKey: ['import'], queryFn: () => data(api.GET('/api/import')) });
}

export function useReadForImport() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (platforms: string[]) => data(api.POST('/api/import/read', { body: { platforms } })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['import'] }),
  });
}

export function useApplyImport() {
  const changed = useCatalogChanged();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (decisions: ImportDecision[]) => data(api.POST('/api/import/apply', { body: { decisions } })),
    onSuccess: () => {
      changed();
      client.invalidateQueries({ queryKey: ['import'] });
    },
  });
}
