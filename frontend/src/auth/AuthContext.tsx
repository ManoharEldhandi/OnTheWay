import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import type { ReactNode } from 'react';
import {
  api, clearAuthTokens, getRefreshToken, getToken, setRefreshToken, setToken,
} from '../api/client';
import type { UserResponse, UserRole } from '../types';

interface AuthState {
  user: UserResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string, role: UserRole) => Promise<void>;
  logout: () => Promise<void>;
}

interface AuthResponse {
  token: string;
  accessToken?: string;
  refreshToken: string;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadMe = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const me = await api.get<UserResponse>('/api/users/me');
      setUser(me);
    } catch {
      clearAuthTokens();
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMe();
  }, [loadMe]);

  const login = useCallback(async (email: string, password: string) => {
    const { accessToken, token, refreshToken } = await api.post<AuthResponse>(
      '/api/auth/login', { email, password },
    );
    setToken(accessToken ?? token);
    setRefreshToken(refreshToken);
    await loadMe();
  }, [loadMe]);

  const register = useCallback(
    async (email: string, password: string, name: string, role: UserRole) => {
      await api.post('/api/auth/register', { email, password, name, role });
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      const payload = JSON.stringify({ refreshToken });
      if (navigator.sendBeacon) {
        navigator.sendBeacon('/api/auth/logout', new Blob([payload], { type: 'application/json' }));
      } else {
        void fetch('/api/auth/logout', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload, keepalive: true,
        });
      }
    }
    clearAuthTokens();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
