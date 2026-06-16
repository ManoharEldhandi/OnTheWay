import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import type { UserRole } from '../types';

export function LoginPage() {
  const { login, register, user } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('alice@ontheway.app');
  const [password, setPassword] = useState('password123');
  const [name, setName] = useState('');
  const [role, setRole] = useState<UserRole>('USER');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) {
    navigate('/', { replace: true });
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(email, password);
      } else {
        await register(email, password, name || email.split('@')[0], role);
      }
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  function quick(demoEmail: string) {
    setMode('login');
    setEmail(demoEmail);
    setPassword('password123');
  }

  return (
    <div className="center">
      <div className="card col login-card">
        <div className="row">
          <span className="login-mark" />
          <div className="brand" style={{ fontSize: 24 }}>On<span>The</span>Way</div>
        </div>
        <p className="sub">Order ahead. Arrive. Pick up. Get on your way.</p>

        <form className="col" onSubmit={submit}>
          {mode === 'register' && (
            <div className="col">
              <label>Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" />
            </div>
          )}
          <div className="col">
            <label>Email</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="col">
            <label>Password</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {mode === 'register' && (
            <div className="col">
              <label>Account type</label>
              <select value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
                <option value="USER">Customer</option>
                <option value="MERCHANT">Merchant</option>
              </select>
            </div>
          )}
          {error && <div className="error">{error}</div>}
          <button className="primary" disabled={busy} type="submit">
            {busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
        </form>

        <div className="spread">
          <span className="muted small">
            {mode === 'login' ? 'New here?' : 'Have an account?'}
          </span>
          <button className="ghost small" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? 'Create an account' : 'Log in instead'}
          </button>
        </div>

        <hr />
        <span className="muted small">Demo logins (password: password123)</span>
        <div className="row wrap">
          <button className="chip" onClick={() => quick('alice@ontheway.app')}>Customer</button>
          <button className="chip" onClick={() => quick('biryani@ontheway.app')}>Merchant</button>
          <button className="chip" onClick={() => quick('admin@ontheway.app')}>Admin</button>
        </div>
      </div>
    </div>
  );
}
