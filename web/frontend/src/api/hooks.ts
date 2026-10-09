import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  CalendarKind,
  CalendarOverview,
  CalendarRule,
  CheckState,
  Edited,
  Gig,
  GigDetail,
  GigDraft,
  ImportResult,
  ImportState,
  Me,
  Platform,
  QueueResult,
  SyncStatus,
  Task,
  TaskLog,
  Town,
} from './types';

// --- who is signed in --------------------------------------------------------------------

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api<Me>('/api/auth/me');
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
      api<Me>('/api/auth/login', 'POST', login),
    onSuccess: (me) => {
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
      client.setQueryData(['me'], me);
    },
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('/api/auth/logout', 'POST'),
    onSettled: () => {
      client.setQueryData(['me'], null);
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' });
    },
  });
}

// --- the catalog ---------------------------------------------------------------------------

export function usePlatforms() {
  return useQuery({ queryKey: ['platforms'], queryFn: () => api<Platform[]>('/api/platforms'), staleTime: Infinity });
}

export function useGigs() {
  return useQuery({ queryKey: ['gigs'], queryFn: () => api<Gig[]>('/api/gigs') });
}

export function useGig(id: string | undefined) {
  return useQuery({
    queryKey: ['gigs', id],
    queryFn: () => api<GigDetail>(`/api/gigs/${id}`),
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
  return useMutation({ mutationFn: (gig: GigDraft) => api<Gig>('/api/gigs', 'POST', gig), onSuccess: changed });
}

export function useEditGig() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (edit: { id: string; rev: string; gig: GigDraft }) =>
      api<Edited>(`/api/gigs/${edit.id}`, 'PUT', { rev: edit.rev, gig: edit.gig }),
    onSuccess: changed,
  });
}

export function useGigAction() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'cancel' | 'reactivate' | 'delete' }) =>
      action === 'delete'
        ? api<QueueResult>(`/api/gigs/${id}`, 'DELETE')
        : api<QueueResult>(`/api/gigs/${id}/${action}`, 'POST'),
    onSuccess: changed,
  });
}

export function usePublish() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (selection: { gigs: string[]; platforms: string[] }) =>
      api<QueueResult>('/api/gigs/publish', 'POST', selection),
    onSuccess: changed,
  });
}

export function useResync() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: (gigs: string[]) => api<QueueResult>('/api/gigs/resync', 'POST', { gigs }),
    onSuccess: changed,
  });
}

export function useTowns(query: string) {
  return useQuery({
    queryKey: ['places', query],
    queryFn: () => api<Town[]>(`/api/places?q=${encodeURIComponent(query)}`),
    enabled: query.trim().length >= 2,
    staleTime: 5 * 60_000,
  });
}

export function resolveTown(name: string, country: string | null, postalCode: string | null) {
  const params = new URLSearchParams({ name });
  if (country) params.set('country', country);
  if (postalCode) params.set('postalCode', postalCode);
  return api<Town | undefined>(`/api/places/resolve?${params}`);
}

// --- the sync ------------------------------------------------------------------------------

export function useSyncTasks(show: 'all' | 'open' | 'failed') {
  return useQuery({ queryKey: ['sync', 'tasks', show], queryFn: () => api<Task[]>(`/api/sync/tasks?show=${show}`) });
}

export function useTaskLog(id: number | null) {
  return useQuery({
    queryKey: ['sync', 'log', id],
    queryFn: () => api<TaskLog[]>(`/api/sync/tasks/${id}/log`),
    enabled: id !== null,
  });
}

export function useSyncStatus() {
  return useQuery({ queryKey: ['sync', 'status'], queryFn: () => api<SyncStatus>('/api/sync/status') });
}

export function useSyncAction() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ path }: { path: string }) => api<void>(`/api/sync/${path}`, 'POST'),
    onSuccess: changed,
  });
}

// --- the calendar --------------------------------------------------------------------------

export function useCalendar() {
  return useQuery({ queryKey: ['calendar'], queryFn: () => api<CalendarOverview>('/api/calendar') });
}

export function useCalendarRules(enabled: boolean) {
  return useQuery({
    queryKey: ['calendar', 'rules'],
    queryFn: () => api<CalendarRule[]>('/api/calendar/rules'),
    enabled,
  });
}

export function useReadCalendar() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api<CalendarOverview>('/api/calendar/read', 'POST'),
    onSuccess: (overview) => client.setQueryData(['calendar'], overview),
  });
}

export function useCalendarAction() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) =>
      api<{ linked?: number } | undefined>(`/api/calendar/${path}`, 'POST', body),
    onSuccess: changed,
  });
}

export function decide(eventId: string, kind: CalendarKind) {
  return { path: `events/${encodeURIComponent(eventId)}/decide`, body: { kind } };
}

export function useAddFromCalendar() {
  const changed = useCatalogChanged();
  return useMutation({
    mutationFn: ({ eventId, gig }: { eventId: string; gig: GigDraft }) =>
      api<void>(`/api/calendar/events/${encodeURIComponent(eventId)}/gig`, 'POST', { gig }),
    onSuccess: changed,
  });
}

// --- the platform check and import ---------------------------------------------------------

export function useCheck() {
  return useQuery({ queryKey: ['check'], queryFn: () => api<CheckState>('/api/check') });
}

export function useCheckAction() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => api<void>(`/api/check/${path}`, 'POST', body),
    onSuccess: () => client.invalidateQueries({ queryKey: ['check'] }),
  });
}

export function useImport() {
  return useQuery({ queryKey: ['import'], queryFn: () => api<ImportState>('/api/import') });
}

export function useReadForImport() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (platforms: string[]) => api<void>('/api/import/read', 'POST', { platforms }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['import'] }),
  });
}

export function useApplyImport() {
  const changed = useCatalogChanged();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (decisions: { index: number; include: boolean; sameGig: boolean; version: number }[]) =>
      api<ImportResult>('/api/import/apply', 'POST', { decisions }),
    onSuccess: () => {
      changed();
      client.invalidateQueries({ queryKey: ['import'] });
    },
  });
}
