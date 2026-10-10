import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { api, ApiError, STATUSES, toInput, type Issue, type Sprint } from '../api';
import { Avatar, EmptyState, ErrorText, formatDate, Modal, PriorityIcon, Spinner, StatusPill, TypeIcon } from '../components/bits';
import { Icon } from '../components/icons';
import { useProject } from './ProjectLayout';

export default function BacklogPage() {
  const ctx = useProject();
  const { project, sprints, liveTick, canEdit, isAdmin, toast, refreshAll, bump } = ctx;
  const [issues, setIssues] = useState<Issue[] | null>(null);
  const [creatingSprint, setCreatingSprint] = useState(false);
  const [showCompleted, setShowCompleted] = useState(false);

  useEffect(() => {
    api.issues(project.id).then((p) => setIssues(p.content)).catch((e: ApiError) => toast(e.message, 'error'));
  }, [project.id, liveTick, toast]);

  const openSprints = sprints.filter((s) => s.status !== 'COMPLETED')
    .sort((a, b) => (a.status === 'ACTIVE' ? -1 : b.status === 'ACTIVE' ? 1 : 0));
  const completed = sprints.filter((s) => s.status === 'COMPLETED');
  const grouped = useMemo(() => {
    const map = new Map<number | null, Issue[]>();
    (issues ?? []).forEach((i) => { const k = i.sprintId; map.set(k, [...(map.get(k) ?? []), i]); });
    map.forEach((list) => list.sort((a, b) => a.number - b.number));
    return map;
  }, [issues]);

  async function sprintAction(label: string, fn: () => Promise<unknown>) {
    try {
      await fn();
      await refreshAll();
      bump();
      toast(label, 'success');
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Action failed', 'error');
    }
  }

  async function moveToSprint(issue: Issue, sprintId: number | null) {
    try {
      const updated = await api.updateIssue(project.id, issue.id, { ...toInput(issue), sprintId });
      setIssues((cur) => cur?.map((i) => (i.id === updated.id ? updated : i)) ?? cur);
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'Move failed', 'error');
      bump();
    }
  }

  const hasActive = sprints.some((s) => s.status === 'ACTIVE');

  return (
    <div className="narrow-xl">
      <div className="page-head">
        <div>
          <div className="crumbs">{project.name} <Icon name="chevronRight" size={13} /> Backlog</div>
          <h1>Backlog</h1>
          <p className="page-sub">Plan upcoming sprints and prioritise the work that isn’t scheduled yet.</p>
        </div>
        <div className="row gap-sm">
          {canEdit && <button className="btn btn-secondary" onClick={() => setCreatingSprint(true)}><Icon name="target" size={16} /> New sprint</button>}
          {canEdit && <button className="btn btn-primary" onClick={() => ctx.openCreate({ sprintId: null })}><Icon name="plus" size={16} /> Create issue</button>}
        </div>
      </div>

      {issues === null && <Spinner />}

      {issues && openSprints.map((s) => (
        <SprintSection key={s.id} sprint={s} issues={grouped.get(s.id) ?? []} canEdit={canEdit}
                       sprints={openSprints} onOpen={ctx.openIssue} onMove={moveToSprint}
                       actions={isAdmin && (
                         <>
                           {s.status === 'PLANNED' && (
                             <button className="btn btn-sm btn-primary" disabled={hasActive}
                                     title={hasActive ? 'Complete the active sprint first' : 'Start this sprint'}
                                     onClick={() => sprintAction(`${s.name} started`, () => api.startSprint(project.id, s.id))}>
                               <Icon name="play" size={13} /> Start
                             </button>
                           )}
                           {s.status === 'ACTIVE' && (
                             <button className="btn btn-sm btn-secondary"
                                     onClick={() => window.confirm(`Complete ${s.name}? Unfinished issues return to the backlog.`)
                                       && sprintAction(`${s.name} completed`, () => api.completeSprint(project.id, s.id))}>
                               <Icon name="checkCircle" size={14} /> Complete sprint
                             </button>
                           )}
                           {s.status === 'PLANNED' && (
                             <button className="icon-btn danger" title="Delete sprint" aria-label="Delete sprint"
                                     onClick={() => window.confirm(`Delete ${s.name}? Its issues move to the backlog.`)
                                       && sprintAction(`${s.name} deleted`, () => api.deleteSprint(project.id, s.id))}>
                               <Icon name="trash" size={15} />
                             </button>
                           )}
                         </>
                       )} />
      ))}

      {issues && (
        <SprintSection sprint={null} issues={grouped.get(null) ?? []} canEdit={canEdit} sprints={openSprints}
                       onOpen={ctx.openIssue} onMove={moveToSprint} />
      )}

      {completed.length > 0 && (
        <section className="panel">
          <button className="disclosure" onClick={() => setShowCompleted((v) => !v)} aria-expanded={showCompleted}>
            <Icon name={showCompleted ? 'chevronDown' : 'chevronRight'} size={15} /> Completed sprints
            <span className="count">{completed.length}</span>
          </button>
          {showCompleted && (
            <ul className="completed-list">
              {completed.map((s) => (
                <li key={s.id}>
                  <Icon name="checkCircle" size={16} className="text-success" />
                  <strong>{s.name}</strong>
                  <span className="muted small">{formatDate(s.startDate)} – {formatDate(s.endDate)}</span>
                  <span className="grow" />
                  <span className="muted small">{(grouped.get(s.id) ?? []).length} issues done</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {creatingSprint && (
        <CreateSprintModal projectId={project.id} count={sprints.length}
                           onClose={() => setCreatingSprint(false)}
                           onCreated={async () => { setCreatingSprint(false); await refreshAll(); toast('Sprint created', 'success'); }} />
      )}
    </div>
  );
}

function SprintSection({ sprint, issues, canEdit, sprints, onOpen, onMove, actions }: {
  sprint: Sprint | null; issues: Issue[]; canEdit: boolean; sprints: Sprint[];
  onOpen: (id: number) => void; onMove: (issue: Issue, sprintId: number | null) => void; actions?: ReactNode;
}) {
  const points = issues.reduce((s, i) => s + (i.storyPoints ?? 0), 0);
  const counts = STATUSES.map((st) => issues.filter((i) => i.status === st).length);
  return (
    <section className={`sprint-card ${sprint?.status === 'ACTIVE' ? 'sprint-active' : ''}`}>
      <header className="sprint-head">
        <div className="min-w-0">
          <div className="row gap-sm wrap">
            <h2 className="sprint-name">{sprint ? sprint.name : 'Backlog'}</h2>
            {sprint && <span className={`badge ${sprint.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}`}>{sprint.status === 'ACTIVE' ? 'Active' : 'Planned'}</span>}
            {sprint?.startDate && <span className="muted small row gap-xs"><Icon name="calendar" size={13} />{formatDate(sprint.startDate)} – {formatDate(sprint.endDate)}</span>}
          </div>
          <div className="muted small">
            {sprint?.goal ? <>{sprint.goal} · </> : null}{issues.length} issue{issues.length === 1 ? '' : 's'} · {points} pts
          </div>
        </div>
        <div className="row gap-xs">{actions}</div>
      </header>
      {sprint && issues.length > 0 && (
        <div className="segbar" title="To do / In progress / In review / Done">
          {counts.map((c, idx) => c > 0 && (
            <span key={idx} className={`seg seg-${STATUSES[idx].toLowerCase()}`} style={{ flexGrow: c }} />
          ))}
        </div>
      )}
      <div className="issue-rows">
        {issues.length === 0 && (
          <EmptyState icon={sprint ? 'target' : 'inbox'} title={sprint ? 'No issues in this sprint yet' : 'Your backlog is empty'}>
            {sprint ? 'Use the “Move to” menu on an issue to plan it into this sprint.' : 'New issues land here until you plan them into a sprint.'}
          </EmptyState>
        )}
        {issues.map((i) => (
          <div key={i.id} className="issue-row">
            <button className="issue-row-main" onClick={() => onOpen(i.id)}>
              <TypeIcon type={i.type} />
              <span className="issue-key">{i.key}</span>
              <span className={`ellipsis ${i.status === 'DONE' ? 'strike' : ''}`}>{i.title}</span>
            </button>
            <div className="issue-row-meta">
              <StatusPill status={i.status} />
              <PriorityIcon priority={i.priority} />
              <span className="points">{i.storyPoints ?? '–'}</span>
              <Avatar user={i.assignee} size={24} />
              {canEdit && (
                <select className="select-compact select-xs" value={i.sprintId ?? ''} aria-label="Move to sprint"
                        onChange={(e) => onMove(i, e.target.value ? Number(e.target.value) : null)}>
                  <option value="">Backlog</option>
                  {sprints.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function CreateSprintModal({ projectId, count, onClose, onCreated }: {
  projectId: number; count: number; onClose: () => void; onCreated: () => void;
}) {
  const [name, setName] = useState(`Sprint ${count + 1}`);
  const [goal, setGoal] = useState('');
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await api.createSprint(projectId, name, goal);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed');
    }
  }
  return (
    <Modal title="Create sprint" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <label className="field"><span className="field-label">Sprint name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} autoFocus /></label>
        <label className="field"><span className="field-label">Sprint goal <span className="optional">optional</span></span>
          <textarea rows={2} value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="What should this sprint achieve?" /></label>
        <ErrorText error={error} />
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary">Create sprint</button>
        </div>
      </form>
    </Modal>
  );
}
