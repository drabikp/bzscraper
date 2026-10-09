/** Calls the app's API: JSON both ways, the session cookie, and the CSRF token on every change. */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly args: Record<string, string>,
    message: string,
  ) {
    super(message);
  }
}

/** Told when the API says the session is gone (the page then shows the sign-in). */
let onUnauthorized: () => void = () => {};
export function whenUnauthorized(listener: () => void) {
  onUnauthorized = listener;
}

function cookie(name: string): string | null {
  const found = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return found ? decodeURIComponent(found.substring(name.length + 1)) : null;
}

async function csrfToken(fresh = false): Promise<string> {
  const known = fresh ? null : cookie('XSRF-TOKEN');
  if (known) return known;
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  const body = (await response.json()) as { token: string };
  return cookie('XSRF-TOKEN') ?? body.token;
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function api<T>(path: string, method: Method = 'GET', body?: unknown, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken(retried);
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 403 && method !== 'GET' && !retried) {
    return api<T>(path, method, body, true); // the token was stale (e.g. after signing in)
  }
  if (response.status === 401) {
    if (!path.startsWith('/api/auth/')) onUnauthorized();
    throw await problem(response);
  }
  if (!response.ok) throw await problem(response);
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function problem(response: Response): Promise<ApiError> {
  try {
    const p = (await response.json()) as { code?: string; args?: Record<string, string>; message?: string };
    return new ApiError(response.status, p.code ?? 'error', p.args ?? {}, p.message ?? response.statusText);
  } catch {
    return new ApiError(response.status, response.status === 401 ? 'unauthorized' : 'error', {}, response.statusText);
  }
}
