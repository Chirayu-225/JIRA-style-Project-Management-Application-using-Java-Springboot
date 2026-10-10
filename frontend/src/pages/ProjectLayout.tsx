import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate, useOutletContext, useParams, useSearchParams } from 'react-router-dom';
import {
  api, ApiError, roleAtLeast, type Issue, type IssueInput, type Member, type Project,
  type ProjectEvent, type Sprint, type UserSummary,
} from '../api';
import { useAuth } from '../auth';
import { Avatar, colorFor, Spinner, StatusPill, TypeIcon } from '../components/bits';
import { Brand, Icon, type IconName } from '../components/icons';
import IssueModal from '../components/IssueModal';
import { ThemeToggle } from '../theme';

export interface ProjectCtx {
  project: Project;
  members: Member[];
  sprints: Sprint[];
  me: UserSummary;
  canEdit: boolean;   // MEMBER+
  isAdmin: boolean;   // ADMIN+
  liveTick: number;   // increments whenever the server says something changed
  bump: () => void;
  refreshAll: () => Promise<void>;
  openIssue: (id: number) => void;
  openCreate: (defaults?: Partial<IssueInput>) => void;
  toast: (message: string, kind?: 'info' | 'success' | 'error') => void;
}

export const useProject = () => useOutletContext<ProjectCtx>();

interface Toast { id: number; message: string; kind: 'info' | 'success' | 'error' }

const NAV: { to: string; label: string; icon: IconName }[] = [
  { to: 'board', label: 'Board', icon: 'board' },
  { to: 'backlog', label: 'Backlog', icon: 'backlog' },
  { to: 'activity', label: 'Activity', icon: 'activity' },
  { to: 'settings', label: 'Settings', icon: 'settings' },
];

export default function ProjectLayout() {
  const { projectId } = useParams();
  const pid = Number(projectId);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const [project, setProject] = useState<Project | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [liveTick, setLiveTick] = useState(0);
  const [live, setLive] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [createDefaults, setCreateDefaults] = useState<Partial<IssueInput> | null>(null);
  const [navOpen, setNavOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  const toast = useCallback((message: string, kind: Toast['kind'] = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-3), { id, message, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500);
  }, []);

  const refreshAll = useCallback(async () => {
    try {
      const [p, m, s] = await Promise.all([api.project(pid), api.members(pid), api.sprints(pid)]);
      setProject(p); setMembers(m); setSprints(s);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load project');
    }
  }, [pid]);

  useEffect(() => { void refreshAll(); }, [refreshAll]);

  // ---- Live updates over Server-Sent Events -------------------------------------------
  const meId = user?.id;
  const refreshRef = useRef(refreshAll);
  refreshRef.current = refreshAll;
  useEffect(() => {
    if (!pid) return;
    const source = new EventSource(api.eventsUrl(pid));
    source.addEventListener('connected', () => setLive(true));
    source.addEventListener('change', (msg) => {
      const event = JSON.parse((msg as MessageEvent).data) as ProjectEvent;
      setLiveTick((t) => t + 1);
      if (event.type.startsWith('sprint') || event.type.startsWith('members') || event.type.startsWith('project')) {
        void refreshRef.current();
      }
      if (event.actorId !== meId) toast(event.message);
    });
    source.onerror = () => setLive(false); // EventSource reconnects by itself
    return () => source.close();
  }, [pid, meId, toast]);

  // ---- Issue modal is driven by ?issue=123 so links are shareable ----------------------
  const openIssueId = params.get('issue') ? Number(params.get('issue')) : null;
  const openIssue = useCallback((id: number) => {
    setParams((p) => { const n = new URLSearchParams(p); n.set('issue', String(id)); return n; });
  }, [setParams]);
  const closeIssue = useCallback(() => {
    setParams((p) => { const n = new URLSearchParams(p); n.delete('issue'); return n; });
  }, [setParams]);
  const openCreate = useCallback((defaults?: Partial<IssueInput>) => setCreateDefaults(defaults ?? {}), []);

  const canEdit = roleAtLeast(project?.myRole, 'MEMBER');
  const modalOpen = openIssueId !== null || createDefaults !== null;

  // ---- Keyboard shortcuts: "/" search, "c" create --------------------------------------
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (modalOpen || e.metaKey || e.ctrlKey || e.altKey || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable) return;
      if (e.key === '/') { e.preventDefault(); searchRef.current?.focus(); }
      if (e.key.toLowerCase() === 'c' && canEdit) { e.preventDefault(); openCreate(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [modalOpen, canEdit, openCreate]);

  if (error) {
    return (
      <div className="center-screen">
        <p>{error}</p>
        <button className="btn btn-secondary" onClick={() => navigate('/')}>Back to projects</button>
      </div>
    );
  }
  if (!project || !user) return <div className="center-screen"><Spinner label="Loading project…" /></div>;

  const ctx: ProjectCtx = {
    project, members, sprints, me: user, canEdit,
    isAdmin: roleAtLeast(project.myRole, 'ADMIN'),
    liveTick, bump: () => setLiveTick((t) => t + 1), refreshAll, openIssue, openCreate, toast,
  };

  return (
    <div className={`app-shell ${navOpen ? 'nav-open' : ''}`}>
      <div className="nav-scrim" onClick={() => setNavOpen(false)} />
      <aside className="sidebar">
        <NavLink to="/" className="sidebar-brand"><Brand compact /></NavLink>

        <div className="project-switch">
          <span className="project-tile project-tile-sm" style={{ background: colorFor(project.id) }}>{project.key.slice(0, 3)}</span>
          <div className="min-w-0">
            <div className="project-switch-name ellipsis">{project.name}</div>
            <div className="project-switch-meta">Software project</div>
          </div>
        </div>

        <div className="nav-label">Planning</div>
        <nav className="nav" onClick={() => setNavOpen(false)}>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to}><Icon name={n.icon} size={17} />{n.label}</NavLink>
          ))}
        </nav>
        <div className="nav-label">Workspace</div>
        <nav className="nav" onClick={() => setNavOpen(false)}>
          <NavLink to="/" end><Icon name="grid" size={17} />All projects</NavLink>
        </nav>

        <div className="sidebar-foot">
          <div className={`live-status ${live ? 'on' : ''}`}>
            <span className="live-dot" />{live ? 'Live updates on' : 'Connecting…'}
          </div>
          <div className="sidebar-user">
            <Avatar user={user} size={30} />
            <div className="min-w-0">
              <div className="ellipsis sidebar-user-name">{user.fullName}</div>
              <div className="ellipsis sidebar-user-role">{project.myRole.toLowerCase()}</div>
            </div>
            <button className="icon-btn" onClick={logout} title="Sign out" aria-label="Sign out"><Icon name="logout" size={16} /></button>
          </div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn show-sm" onClick={() => setNavOpen((o) => !o)} aria-label="Open menu"><Icon name="menu" size={18} /></button>
          <GlobalSearch inputRef={searchRef} projectId={pid} projectKey={project.key} onPick={openIssue} />
          <div className="topbar-right">
            <ThemeToggle />
            {canEdit && (
              <button className="btn btn-primary" onClick={() => openCreate()} title="Create issue (C)">
                <Icon name="plus" size={16} /><span className="hide-sm">Create</span>
              </button>
            )}
          </div>
        </header>
        <div className="content">
          <Outlet context={ctx} />
        </div>
      </div>

      {modalOpen && (
        <IssueModal
          ctx={ctx}
          issueId={openIssueId}
          defaults={createDefaults ?? undefined}
          onClose={() => { closeIssue(); setCreateDefaults(null); }}
          onCreated={(issue) => { setCreateDefaults(null); openIssue(issue.id); }}
        />
      )}

      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`}>
            <Icon name={t.kind === 'error' ? 'alert' : t.kind === 'success' ? 'checkCircle' : 'info'} size={16} />
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function GlobalSearch({ projectId, projectKey, onPick, inputRef }: {
  projectId: number; projectKey: string; onPick: (id: number) => void; inputRef: React.RefObject<HTMLInputElement>;
}) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Issue[] | null>(null);
  const [open, setOpen] = useState(false);

  // Debounce: wait 250ms after the last keystroke before calling the API
  useEffect(() => {
    if (q.trim().length < 2) { setResults(null); return; }
    const timer = setTimeout(() => {
      api.search(projectId, q).then((r) => { setResults(r); setOpen(true); }).catch(() => setResults([]));
    }, 250);
    return () => clearTimeout(timer);
  }, [q, projectId]);

  return (
    <div className="search">
      <Icon name="search" size={16} className="search-icon" />
      <input
        ref={inputRef}
        placeholder={`Search issues or jump to ${projectKey}-1…`}
        value={q}
        onChange={(e) => setQ(e.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(e) => { if (e.key === 'Escape') { setQ(''); (e.target as HTMLInputElement).blur(); } }}
        aria-label="Search issues"
      />
      <kbd className="kbd hide-sm">/</kbd>
      {open && results && (
        <div className="search-results">
          <div className="search-results-head">{results.length ? `${results.length} result${results.length === 1 ? '' : 's'}` : 'No matching issues'}</div>
          {results.map((i) => (
            <button key={i.id} className="search-row" onMouseDown={() => { onPick(i.id); setQ(''); setResults(null); }}>
              <TypeIcon type={i.type} />
              <span className="issue-key">{i.key}</span>
              <span className="ellipsis grow">{i.title}</span>
              <StatusPill status={i.status} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
