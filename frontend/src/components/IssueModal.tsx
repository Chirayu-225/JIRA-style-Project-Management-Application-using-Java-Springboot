import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import {
  api, ApiError, PRIORITIES, STATUS_LABEL, STATUSES, TYPES, toInput,
  type Activity, type Comment, type Issue, type IssueInput,
} from '../api';
import type { ProjectCtx } from '../pages/ProjectLayout';
import { Avatar, ErrorText, Modal, PriorityIcon, Spinner, timeAgo, TypeIcon } from './bits';
import { Icon } from './icons';

const EMPTY: IssueInput = {
  title: '', description: '', type: 'TASK', priority: 'MEDIUM', status: 'TODO',
  storyPoints: null, assigneeId: null, sprintId: null,
};

const cap = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

interface Props {
  ctx: ProjectCtx;
  issueId: number | null;              // null = create mode
  defaults?: Partial<IssueInput>;
  onClose: () => void;
  onCreated: (issue: Issue) => void;
}

export default function IssueModal({ ctx, issueId, defaults, onClose, onCreated }: Props) {
  const pid = ctx.project.id;
  const creating = issueId === null;

  const [issue, setIssue] = useState<Issue | null>(null);
  const [form, setForm] = useState<IssueInput>({ ...EMPTY, ...defaults });
  const [comments, setComments] = useState<Comment[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<'comments' | 'history'>('comments');
  const [newComment, setNewComment] = useState('');

  const load = useCallback(async () => {
    if (issueId === null) return;
    try {
      const [i, c, a] = await Promise.all([
        api.issue(pid, issueId), api.comments(pid, issueId), api.issueActivity(pid, issueId),
      ]);
      setIssue(i); setForm(toInput(i)); setComments(c); setActivity(a);
      setConflict(false); setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load issue');
    }
  }, [pid, issueId]);

  useEffect(() => { void load(); }, [load]);

  // Someone commented while the modal is open: refresh comments only (don't clobber the form)
  useEffect(() => {
    if (issueId === null || ctx.liveTick === 0) return;
    api.comments(pid, issueId).then(setComments).catch(() => undefined);
  }, [ctx.liveTick, pid, issueId]);

  const set = <K extends keyof IssueInput>(k: K, v: IssueInput[K]) => setForm((f) => ({ ...f, [k]: v }));
  const readOnly = !ctx.canEdit;
  const openSprints = ctx.sprints.filter((s) => s.status !== 'COMPLETED');
  const dirty = issue !== null && JSON.stringify(toInput(issue)) !== JSON.stringify({ ...form, version: issue.version });

  async function save(e?: FormEvent) {
    e?.preventDefault();
    if (!form.title.trim()) { setError('Summary is required'); return; }
    setBusy(true); setError(null);
    const payload = { ...form, description: form.description?.trim() ? form.description : null };
    try {
      if (creating) {
        const created = await api.createIssue(pid, payload);
        ctx.bump();
        ctx.toast(`Created ${created.key}`, 'success');
        onCreated(created);
      } else {
        const updated = await api.updateIssue(pid, issueId, payload);
        setIssue(updated); setForm(toInput(updated));
        api.issueActivity(pid, issueId).then(setActivity).catch(() => undefined);
        ctx.bump();
        ctx.toast(`Saved ${updated.key}`, 'success');
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setConflict(true);
      setError(err instanceof ApiError ? err.message : 'Save failed');
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!issue || !window.confirm(`Delete ${issue.key}? This cannot be undone.`)) return;
    try {
      await api.deleteIssue(pid, issue.id);
      ctx.bump();
      ctx.toast(`Deleted ${issue.key}`, 'success');
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Delete failed');
    }
  }

  async function addComment(e: FormEvent) {
    e.preventDefault();
    if (!issue || !newComment.trim()) return;
    try {
      const c = await api.addComment(pid, issue.id, newComment);
      setComments((cs) => [...cs, c]);
      setNewComment('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add comment');
    }
  }

  async function deleteComment(c: Comment) {
    if (!issue) return;
    try {
      await api.deleteComment(pid, issue.id, c.id);
      setComments((cs) => cs.filter((x) => x.id !== c.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete comment');
    }
  }

  const canDelete = issue && (ctx.isAdmin || issue.reporter.id === ctx.me.id);

  // ------------------------------------------------------------------ field controls (shared by both modes)
  const fields: { label: string; control: ReactNode }[] = [
    {
      label: 'Status', control: (
        <select className={`select-status status-${form.status.toLowerCase()}`} value={form.status} disabled={readOnly}
                onChange={(e) => set('status', e.target.value as IssueInput['status'])}>
          {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
      ),
    },
    {
      label: 'Assignee', control: (
        <div className="with-adornment">
          <Avatar user={ctx.members.find((m) => m.user.id === form.assigneeId)?.user ?? null} size={22} />
          <select value={form.assigneeId ?? ''} disabled={readOnly}
                  onChange={(e) => set('assigneeId', e.target.value ? Number(e.target.value) : null)}>
            <option value="">Unassigned</option>
            {ctx.members.map((m) => <option key={m.user.id} value={m.user.id}>{m.user.fullName}</option>)}
          </select>
        </div>
      ),
    },
    {
      label: 'Priority', control: (
        <div className="with-adornment">
          <PriorityIcon priority={form.priority} />
          <select value={form.priority} disabled={readOnly} onChange={(e) => set('priority', e.target.value as IssueInput['priority'])}>
            {PRIORITIES.map((p) => <option key={p} value={p}>{cap(p)}</option>)}
          </select>
        </div>
      ),
    },
    {
      label: 'Type', control: (
        <div className="with-adornment">
          <TypeIcon type={form.type} />
          <select value={form.type} disabled={readOnly} onChange={(e) => set('type', e.target.value as IssueInput['type'])}>
            {TYPES.map((t) => <option key={t} value={t}>{cap(t)}</option>)}
          </select>
        </div>
      ),
    },
    {
      label: 'Sprint', control: (
        <select value={form.sprintId ?? ''} disabled={readOnly}
                onChange={(e) => set('sprintId', e.target.value ? Number(e.target.value) : null)}>
          <option value="">Backlog</option>
          {openSprints.map((s) => <option key={s.id} value={s.id}>{s.name}{s.status === 'ACTIVE' ? ' (active)' : ''}</option>)}
          {issue?.sprintId && !openSprints.some((s) => s.id === issue.sprintId) && (
            <option value={issue.sprintId}>{issue.sprintName} (completed)</option>
          )}
        </select>
      ),
    },
    {
      label: 'Story points', control: (
        <input type="number" min={0} max={100} disabled={readOnly} value={form.storyPoints ?? ''} placeholder="—"
               onChange={(e) => set('storyPoints', e.target.value === '' ? null : Number(e.target.value))} />
      ),
    },
  ];

  // ------------------------------------------------------------------ create mode
  if (creating) {
    return (
      <Modal title="Create issue" onClose={onClose}>
        <form className="form" onSubmit={save}>
          <label className="field">
            <span className="field-label">Summary</span>
            <input value={form.title} required maxLength={200} autoFocus placeholder="What needs to be done?"
                   onChange={(e) => set('title', e.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">Description <span className="optional">optional</span></span>
            <textarea rows={4} value={form.description ?? ''} placeholder="Add context, steps to reproduce, acceptance criteria…"
                      onChange={(e) => set('description', e.target.value)} />
          </label>
          <div className="field-grid">
            {fields.map((f) => (
              <label key={f.label} className="field"><span className="field-label">{f.label}</span>{f.control}</label>
            ))}
          </div>
          <ErrorText error={error} />
          <div className="form-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create issue'}</button>
          </div>
        </form>
      </Modal>
    );
  }

  // ------------------------------------------------------------------ view / edit mode
  const title = issue
    ? <span className="crumbs crumbs-modal">{ctx.project.name} <Icon name="chevronRight" size={13} /> <TypeIcon type={issue.type} /> <span className="issue-key">{issue.key}</span></span>
    : 'Loading…';

  return (
    <Modal title={title} onClose={onClose} size="lg">
      {!issue && !error && <Spinner />}
      {!issue && error && <ErrorText error={error} />}
      {issue && (
        <div className="issue-layout">
          <div className="issue-main">
            <form id="issue-form" onSubmit={save}>
              <textarea className="title-input" rows={1} value={form.title} required maxLength={200} disabled={readOnly}
                        onChange={(e) => set('title', e.target.value.replace(/\n/g, ' '))} aria-label="Summary" />
              <div className="section-label">Description</div>
              <textarea className="desc-input" rows={6} value={form.description ?? ''} disabled={readOnly}
                        placeholder={readOnly ? 'No description' : 'Add a description…'}
                        onChange={(e) => set('description', e.target.value)} aria-label="Description" />
            </form>

            <div className="tabs">
              <button className={tab === 'comments' ? 'active' : ''} onClick={() => setTab('comments')}>
                <Icon name="message" size={15} /> Comments <span className="tab-count">{comments.length}</span>
              </button>
              <button className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>
                <Icon name="clock" size={15} /> History
              </button>
            </div>

            {tab === 'comments' && (
              <div className="comments">
                {ctx.canEdit && (
                  <form onSubmit={addComment} className="comment-compose">
                    <Avatar user={ctx.me} size={30} />
                    <div className="grow">
                      <textarea rows={2} placeholder="Add a comment…" value={newComment} maxLength={5000}
                                onChange={(e) => setNewComment(e.target.value)}
                                onKeyDown={(e) => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) void addComment(e); }} />
                      {newComment.trim() && (
                        <div className="row gap-xs end">
                          <span className="muted small hide-sm">Ctrl + Enter to send</span>
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setNewComment('')}>Cancel</button>
                          <button className="btn btn-primary btn-sm">Comment</button>
                        </div>
                      )}
                    </div>
                  </form>
                )}
                {comments.length === 0 && <p className="muted small">No comments yet.</p>}
                {comments.map((c) => (
                  <div key={c.id} className="comment">
                    <Avatar user={c.author} size={30} />
                    <div className="comment-body">
                      <div className="comment-meta">
                        <strong>{c.author.fullName}</strong>
                        <span className="muted">{timeAgo(c.createdAt)}</span>
                        {(c.author.id === ctx.me.id || ctx.isAdmin) && (
                          <button className="link-btn danger" onClick={() => deleteComment(c)}>Delete</button>
                        )}
                      </div>
                      <div className="pre-wrap">{c.body}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {tab === 'history' && (
              <ul className="timeline">
                {activity.map((a) => (
                  <li key={a.id}>
                    <Avatar user={a.actor} size={26} />
                    <div>
                      <div><strong>{a.actor.fullName}</strong> <span className="muted">{a.action.replace('.', ' ')}</span></div>
                      <div className="small">{a.details}</div>
                      <div className="muted small">{timeAgo(a.createdAt)}</div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <aside className="issue-side">
            <div className="details-card">
              <div className="details-head">Details</div>
              {fields.map((f) => (
                <label key={f.label} className="detail-row">
                  <span className="detail-label">{f.label}</span>
                  <span className="detail-value">{f.control}</span>
                </label>
              ))}
              <div className="detail-row">
                <span className="detail-label">Reporter</span>
                <span className="detail-value row gap-xs"><Avatar user={issue.reporter} size={22} /> {issue.reporter.fullName}</span>
              </div>
            </div>

            <ErrorText error={error} />
            {conflict && (
              <button type="button" className="btn btn-secondary btn-sm btn-block" onClick={() => void load()}>
                <Icon name="refresh" size={14} /> Load latest version
              </button>
            )}

            {!readOnly && (
              <div className="side-actions">
                {dirty || busy ? (
                  <button type="submit" form="issue-form" className="btn btn-primary btn-block" disabled={busy}>
                    {busy ? 'Saving…' : 'Save changes'}
                  </button>
                ) : (
                  <div className="saved-state"><Icon name="checkCircle" size={15} /> All changes saved</div>
                )}
                {canDelete && (
                  <button type="button" className="btn btn-ghost btn-sm btn-block danger" onClick={remove}>
                    <Icon name="trash" size={14} /> Delete issue
                  </button>
                )}
              </div>
            )}
            <div className="side-meta">
              <div>Created {timeAgo(issue.createdAt)}</div>
              <div>Updated {timeAgo(issue.updatedAt)}</div>
            </div>
          </aside>
        </div>
      )}
    </Modal>
  );
}
