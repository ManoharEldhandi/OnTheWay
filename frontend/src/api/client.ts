// Tiny fetch wrapper that attaches the JWT and surfaces backend error messages.

const TOKEN_KEY = 'ontheway.token';
const REFRESH_TOKEN_KEY = 'ontheway.refreshToken';
let refreshPromise: Promise<boolean> | null = null;

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

export function clearRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function clearAuthTokens(): void {
  clearToken();
  clearRefreshToken();
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res = await rawRequest(method, path, body);
  if (res.status === 401 && !path.startsWith('/api/auth/') && await refreshAccessToken()) {
    res = await rawRequest(method, path, body);
    if (res.status === 401) clearAuthTokens();
  }
  return parseResponse<T>(res);
}

async function rawRequest(method: string, path: string, body?: unknown): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  return fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: unknown;
  if (text) {
    const contentType = res.headers.get('content-type') ?? '';
    if (contentType.includes('json')) {
      try {
        data = JSON.parse(text);
      } catch {
        throw new ApiError(res.status, 'The server returned an invalid JSON response');
      }
    } else {
      data = text;
    }
  }

  if (!res.ok) {
    const message = typeof data === 'object' && data !== null && 'message' in data
      ? String(data.message)
      : typeof data === 'string' && data.trim()
        ? data
        : `Request failed (${res.status})`;
    throw new ApiError(res.status, message);
  }
  return data as T;
}

export async function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = performTokenRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performTokenRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) {
    clearAuthTokens();
    return false;
  }
  const data = await parseResponse<{ accessToken: string; refreshToken: string }>(res);
  if (!data.accessToken || !data.refreshToken) {
    clearAuthTokens();
    return false;
  }
  setToken(data.accessToken);
  setRefreshToken(data.refreshToken);
  return true;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
};
