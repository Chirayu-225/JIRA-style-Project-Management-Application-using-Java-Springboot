import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, type Role } from '../api';
import { Avatar, ErrorText } from '../components/bits';
import { Icon } from '../components/icons';
import { useProject } from './ProjectLayout';

const ASSIGNABLE: Role[] = ['VIEWER', 'MEMBER', 'ADMIN'];
const ROLE_HELP: Record<Role, string> = {
  VIEWER: 'Read-only access',
  MEMBER: 'Create and edit issues, comment',
  ADMIN: 'Manage sprints and members',
  OWNER: 'Full control, including deleting the project',
};
const cap = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

export default function SettingsPage() {
  const { project, members, me, isAdmin, refreshAll, toast } = useProject();
  const navigate = useNavigate();
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [confirmKey, setConfirmKey] = useState('');

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); await refreshAll(); toast(ok, 'success'); return true; } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Failed', 'error'); return false;
    }
  };

  async function saveDetails(e: FormEvent) {
    e.preventDefault();
    await run(() => api.updateProject(project.id, name, description), 'Project updated');
  }

  async function invite(e: FormEvent) {
    e.preventDefault();
    setInviteError(null);
    try {
      await api.addMember(project.id, email, role);
      setEmail('');
      await refreshAll();
      toast('Member added', 'success');
    } catch (err) {
      setInviteError(err instanceof ApiError ? err.message : 'Failed');
    }
  }

  async function leave() {
    if (!window.confirm(`Leave ${project.name}?`)) return;
    try { await api.removeMember(project.id, me.id); navigate('/'); } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }

  async function deleteProject() {
    try { await api.deleteProject(project.id); navigate('/'); } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Failed', 'error');
    }
  }

  return (
    <div className="narrow">
      <div className="page-head">
        <div>
          <div className="crumbs">{project.name} <Icon name="chevronRight" size={13} /> Settings</div>
          <h1>Project settings</h1>
          <p className="page-sub">Manage project details, people and permissions.</p>
        </div>
      </div>

      <section className="panel settings-section">
        <div className="settings-intro">
          <h2>Details</h2>
          <p className="muted small">Shown to everyone in the project.</p>
        </div>
        <form className="form" onSubmit={saveDetails}>
          <label className="field"><span className="field-label">Name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} disabled={!isAdmin} required maxLength={100} /></label>
          <label className="field"><span className="field-label">Description</span>
            <textarea rows={3} value={description} disabled={!isAdmin} onChange={(e) => setDescription(e.target.value)} /></label>
          <label className="field"><span className="field-label">Key</span>
            <input value={project.key} disabled className="key-mono" />
            <span className="field-hint">Issue IDs depend on the key, so it can’t be changed.</span></label>
          {isAdmin && <div className="form-actions"><button className="btn btn-primary">Save changes</button></div>}
        </form>
      </section>

      <section className="panel settings-section">
        <div className="settings-intro">
          <h2>Members <span className="count">{members.length}</span></h2>
          <p className="muted small">People who can see this project.</p>
        </div>
        <div>
          {isAdmin && (
            <form className="invite" onSubmit={invite}>
              <div className="input-icon grow">
                <Icon name="userPlus" size={15} />
                <input type="email" placeholder="Invite by email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </div>
              <select className="select-compact" value={role} onChange={(e) => setRole(e.target.value as Role)} aria-label="Role">
                {ASSIGNABLE.map((r) => <option key={r} value={r}>{cap(r)}</option>)}
              </select>
              <button className="btn btn-primary">Add</button>
            </form>
          )}
          <ErrorText error={inviteError} />
          <ul className="member-list">
            {members.map((m) => (
              <li key={m.user.id}>
                <Avatar user={m.user} size={34} />
                <div className="min-w-0 grow">
                  <div className="ellipsis"><strong>{m.user.fullName}</strong>{m.user.id === me.id && <span className="muted"> (you)</span>}</div>
                  <div className="muted small ellipsis">{m.user.email}</div>
                </div>
                {isAdmin && m.role !== 'OWNER' ? (
                  <select className="select-compact" value={m.role} aria-label={`Role for ${m.user.fullName}`}
                          onChange={(e) => run(() => api.changeRole(project.id, m.user.id, e.target.value as Role), 'Role updated')}>
                    {ASSIGNABLE.map((r) => <option key={r} value={r}>{cap(r)}</option>)}
                  </select>
                ) : <span className="role-badge">{m.role.toLowerCase()}</span>}
                {isAdmin && m.role !== 'OWNER' && m.user.id !== me.id ? (
                  <button className="icon-btn danger" title="Remove member" aria-label={`Remove ${m.user.fullName}`}
                          onClick={() => window.confirm(`Remove ${m.user.fullName}?`) && run(() => api.removeMember(project.id, m.user.id), 'Member removed')}>
                    <Icon name="x" size={16} />
                  </button>
                ) : <span className="icon-btn-spacer" />}
              </li>
            ))}
          </ul>
          <div className="role-legend">
            {(Object.keys(ROLE_HELP) as Role[]).map((r) => (
              <div key={r}><span className="role-badge">{r.toLowerCase()}</span><span className="muted small">{ROLE_HELP[r]}</span></div>
            ))}
          </div>
        </div>
      </section>

      {project.myRole !== 'OWNER' && (
        <section className="panel settings-section">
          <div className="settings-intro"><h2>Leave project</h2><p className="muted small">You’ll lose access until someone adds you again.</p></div>
          <div><button className="btn btn-secondary danger" onClick={leave}><Icon name="logout" size={15} /> Leave {project.name}</button></div>
        </section>
      )}

      {project.myRole === 'OWNER' && (
        <section className="panel settings-section panel-danger">
          <div className="settings-intro">
            <h2>Delete project</h2>
            <p className="muted small">Permanently removes all issues, sprints, comments and history.</p>
          </div>
          <div className="form">
            <label className="field"><span className="field-label">Type <strong className="key-mono">{project.key}</strong> to confirm</span>
              <input value={confirmKey} onChange={(e) => setConfirmKey(e.target.value)} placeholder={project.key} className="key-mono" /></label>
            <div><button className="btn btn-danger" disabled={confirmKey !== project.key} onClick={deleteProject}><Icon name="trash" size={15} /> Delete project</button></div>
          </div>
        </section>
      )}
    </div>
  );
}
