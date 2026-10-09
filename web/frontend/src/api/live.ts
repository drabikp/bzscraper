import { useEffect } from 'react';
import { QueryClient, useQueryClient } from '@tanstack/react-query';

/** What each live event makes the page read again. */
const READ_AGAIN: Record<string, string[][]> = {
  gigs: [['gigs']],
  sync: [['sync'], ['gigs']],
  check: [['check']],
  import: [['import']],
  calendar: [['calendar']],
};

function refresh(client: QueryClient, topic: string) {
  (READ_AGAIN[topic] ?? []).forEach((queryKey) => client.invalidateQueries({ queryKey }));
}

/**
 * Follows the app's live updates (server-sent events, /api/events) while signed in: each event
 * names what changed, and that part is read again. A dropped stream is opened again (the
 * browser retries on its own; after the phone woke the app up, it is reopened) and everything
 * is read again, since events may have been missed meanwhile.
 */
export function useLiveUpdates(enabled: boolean) {
  const client = useQueryClient();
  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') return;
    let source: EventSource | null = null;
    let opened = false;
    const open = () => {
      source?.close();
      source = new EventSource('/api/events');
      source.addEventListener('hello', () => {
        if (opened) client.invalidateQueries();
        opened = true;
      });
      Object.keys(READ_AGAIN).forEach((topic) => source!.addEventListener(topic, () => refresh(client, topic)));
    };
    const wake = () => {
      if (document.visibilityState === 'visible' && (!source || source.readyState === EventSource.CLOSED)) {
        open();
        client.invalidateQueries();
      }
    };
    open();
    document.addEventListener('visibilitychange', wake);
    return () => {
      document.removeEventListener('visibilitychange', wake);
      source?.close();
    };
  }, [enabled, client]);
}
