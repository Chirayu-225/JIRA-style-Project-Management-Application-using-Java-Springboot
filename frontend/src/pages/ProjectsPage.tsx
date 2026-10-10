import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, type Project } from '../api';
import { useAuth } from '../auth';
import { Avatar, colorFor, EmptyState, ErrorText, Modal, Spinner } from '../components/bits';
import { Brand, Icon } from '../components/icons';
import { ThemeToggle } from '../theme';

/** "Mobile App Redesign" -> "MAR" */
function suggestKey(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const key = words.length > 1 ? words.map((w) => w[0]).join('') : (words[0] ?? '').slice(0, 4);
  return key.replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 10);
}

export default function ProjectsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    api.projects().then(setProjects).catch((e: ApiError) => setError(e.message));
  }, []);

  return (
    <div className="page-shell">
      <header className="topbar topbar-plain">
        <Brand compact />
        <div className="topbar-right">
          <ThemeToggle />
          <div className="user-chip">
            <Avatar user={user} size={28} />
            <span className="hide-sm">{user?.fullName}</span>
          </div>
          <button className="icon-btn" onClick={logout} title="Sign out" aria-label="Sign out"><Icon name="logout" size={17} /></button>
        </div>
      </header>

      <main className="container">
        <div className="page-head">
          <div>
            <h1>Projects</h1>
            <p className="page-sub">All the projects you’re a member of.</p>
          </div>
          <button className="btn btn-primary" onClick={() => setCreating(true)}><Icon name="plus" size={16} /> New project</button>
        </div>

        <ErrorText error={error} />
        {projects === null && !error && <Spinner />}
        {projects?.length === 0 && (
          <EmptyState icon="folder" title="No projects yet">
            <p>Create a project to get a board, a backlog and sprints.</p>
            <button className="btn btn-primary" onClick={() => setCreating(true)}><Icon name="plus" size={16} /> Create project</button>
          </EmptyState>
        )}

        <div className="project-grid">
          {projects?.map((p) => (
            <Link key={p.id} to={`/p/${p.id}/board`} className="project-card">
              <div className="project-card-top">
                <span className="project-tile" style={{ background: colorFor(p.id) }}>{p.key.slice(0, 3)}</span>
                <span className="role-badge">{p.myRole.toLowerCase()}</span>
              </div>
              <h3>{p.name}</h3>
              <p className="clamp-2 muted">{p.description || 'No description'}</p>
              <div className="project-card-foot">
                <span className="meta"><Icon name="users" size={14} /> {p.memberCount} member{p.memberCount === 1 ? '' : 's'}</span>
                <span className="meta key-mono">{p.key}</span>
                <span className="open-link">Open <Icon name="arrowRight" size={14} /></span>
              </div>
            </Link>
          ))}
          {projects && projects.length > 0 && (
            <button className="project-card project-card-new" onClick={() => setCreating(true)}>
              <span className="new-icon"><Icon name="plus" size={20} /></span>
              New project
            </button>
          )}
        </div>
      </main>

      {creating && <CreateProjectModal onClose={() => setCreating(false)} onCreated={(p) => navigate(`/p/${p.id}/board`)} />}
    </div>
  );
}

function CreateProjectModal({ onClose, onCreated }: { onClose: () => void; onCreated: (p: Project) => void }) {
  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [keyTouched, setKeyTouched] = useState(false);
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      onCreated(await api.createProject(key, name, description));
    } catch (err) {
      if (err instanceof ApiError) { setError(err.message); setFieldErrors(err.fieldErrors); }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Create project" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label className="field">
          <span className="field-label">Project name</span>
          <input value={name} required maxLength={100} autoFocus placeholder="e.g. Mobile App"
                 onChange={(e) => { setName(e.target.value); if (!keyTouched) setKey(suggestKey(e.target.value)); }} />
        </label>
        <label className="field">
          <span className="field-label">Key</span>
          <input value={key} required maxLength={10} pattern="[A-Za-z][A-Za-z0-9]{1,9}" className="key-mono"
                 onChange={(e) => { setKeyTouched(true); setKey(e.target.value.toUpperCase()); }} />
          <span className="field-hint">Prefix for issue IDs, e.g. {key || 'APP'}-1. 2–10 letters or digits.</span>
          <ErrorText error={fieldErrors.key ?? null} />
        </label>
        <label className="field">
          <span className="field-label">Description <span className="optional">optional</span></span>
          <textarea value={description} rows={3} maxLength={2000} onChange={(e) => setDescription(e.target.value)}
                    placeholder="What is this project about?" />
        </label>
        <ErrorText error={error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create project'}</button>
        </div>
      </form>
    </Modal>
  );
}
