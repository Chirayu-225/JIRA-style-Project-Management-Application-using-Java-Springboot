import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api';
import { useAuth } from '../auth';
import { ErrorText } from '../components/bits';
import { Brand, Icon, type IconName } from '../components/icons';
import { ThemeToggle } from '../theme';

const DEMO = { email: 'demo@example.com', password: 'demo12345' };

const FEATURES: { icon: IconName; title: string; text: string }[] = [
  { icon: 'board', title: 'Kanban boards', text: 'Drag work across To Do, In Progress, Review and Done.' },
  { icon: 'target', title: 'Sprints & backlog', text: 'Plan sprints, track story points and see progress.' },
  { icon: 'zap', title: 'Live updates', text: 'Changes from teammates appear instantly.' },
  { icon: 'shield', title: 'Role-based access', text: 'Owner, admin, member and viewer permissions.' },
];

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<'form' | 'demo' | null>(null);

  if (user) return <Navigate to={from} replace />;

  async function submit(e: FormEvent, demo = false) {
    e.preventDefault();
    setBusy(demo ? 'demo' : 'form');
    setError(null);
    setFieldErrors({});
    try {
      if (demo) await login(DEMO.email, DEMO.password);
      else if (mode === 'register') await register(email, password, fullName);
      else await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) { setError(err.message); setFieldErrors(err.fieldErrors); }
      else setError('Something went wrong');
    } finally {
      setBusy(null);
    }
  }

  const isLogin = mode === 'login';

  return (
    <div className="auth">
      <aside className="auth-hero">
        <div className="auth-hero-inner">
          <Brand />
          <h1>Plan, track and ship work with your team.</h1>
          <p className="auth-lead">A JIRA-style issue tracker with boards, sprints and real-time collaboration.</p>
          <ul className="feature-list">
            {FEATURES.map((f) => (
              <li key={f.title}>
                <span className="feature-icon"><Icon name={f.icon} size={18} /></span>
                <div><strong>{f.title}</strong><span>{f.text}</span></div>
              </li>
            ))}
          </ul>
          <BoardPreview />
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-top"><ThemeToggle /></div>
        <form className="auth-card" onSubmit={(e) => submit(e)}>
          <div className="auth-card-head">
            <h2>{isLogin ? 'Welcome back' : 'Create your account'}</h2>
            <p className="muted">{isLogin ? 'Sign in to continue to your projects.' : 'Start organising your team’s work in minutes.'}</p>
          </div>

          {!isLogin && (
            <label className="field">
              <span className="field-label">Full name</span>
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={100}
                     autoComplete="name" placeholder="Jane Doe" />
              <ErrorText error={fieldErrors.fullName ?? null} />
            </label>
          )}
          <label className="field">
            <span className="field-label">Email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required
                   autoComplete="email" placeholder="you@company.com" />
            <ErrorText error={fieldErrors.email ?? null} />
          </label>
          <label className="field">
            <span className="field-label">Password</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required
                   minLength={isLogin ? undefined : 8} placeholder={isLogin ? '••••••••' : 'At least 8 characters'}
                   autoComplete={isLogin ? 'current-password' : 'new-password'} />
            <ErrorText error={fieldErrors.password ?? null} />
          </label>

          <ErrorText error={Object.keys(fieldErrors).length ? null : error} />

          <button className="btn btn-primary btn-lg btn-block" disabled={busy !== null}>
            {busy === 'form' ? <span className="spinner spinner-sm spinner-light" /> : null}
            {isLogin ? 'Sign in' : 'Create account'}
          </button>

          {isLogin && (
            <>
              <div className="divider"><span>or</span></div>
              <button type="button" className="btn btn-secondary btn-lg btn-block" disabled={busy !== null}
                      onClick={(e) => submit(e, true)}>
                {busy === 'demo' ? <span className="spinner spinner-sm" /> : <Icon name="play" size={14} />}
                Explore the demo workspace
              </button>
            </>
          )}

          <p className="auth-switch">
            {isLogin
              ? <>Don’t have an account? <Link to="/register">Sign up</Link></>
              : <>Already have an account? <Link to="/login">Sign in</Link></>}
          </p>
        </form>
      </main>
    </div>
  );
}

/** Decorative mini board on the sign-in page. */
function BoardPreview() {
  const cols = [
    { name: 'To Do', cls: 'todo', cards: [72, 54] },
    { name: 'In Progress', cls: 'in_progress', cards: [64, 80] },
    { name: 'Done', cls: 'done', cards: [58] },
  ];
  return (
    <div className="preview" aria-hidden="true">
      {cols.map((c) => (
        <div key={c.name} className="preview-col">
          <div className="preview-head"><span className={`dot dot-${c.cls}`} />{c.name}</div>
          {c.cards.map((w, i) => (
            <div key={i} className="preview-card">
              <span className="preview-line" style={{ width: `${w}%` }} />
              <span className="preview-line short" />
              <div className="preview-meta"><span className={`preview-tag tag-${i % 2 ? 'b' : 'a'}`} /><span className="preview-avatar" /></div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
