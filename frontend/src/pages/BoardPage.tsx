import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  DndContext, DragOverlay, PointerSensor, TouchSensor, useDraggable, useDroppable, useSensor, useSensors,
  type DragEndEvent, type DragStartEvent,
} from '@dnd-kit/core';
import { api, ApiError, STATUS_LABEL, STATUSES, TYPES, type Issue, type IssueStatus, type IssueType } from '../api';
import { Avatar, daysLeft, formatDate, PriorityIcon, Spinner, TypeIcon } from '../components/bits';
import { Icon } from '../components/icons';
import { useProject } from './ProjectLayout';

const byPosition = (a: Issue, b: Issue) => a.position - b.position || a.id - b.id;

export default function BoardPage() {
  const ctx = useProject();
  const { project, sprints, members, liveTick, canEdit, openIssue, toast, me } = ctx;
  const activeSprint = sprints.find((s) => s.status === 'ACTIVE') ?? null;

  const [issues, setIssues] = useState<Issue[] | null>(null);
  const [text, setText] = useState('');
  const [typeFilter, setTypeFilter] = useState<IssueType | ''>('');
  const [assigneeFilter, setAssigneeFilter] = useState<number | 'none' | null>(null);
  const [dragging, setDragging] = useState<Issue | null>(null);

  // Reload whenever the sprint changes or a live event arrives
  useEffect(() => {
    let cancelled = false;
    api.issues(project.id, activeSprint ? { sprintId: activeSprint.id } : {})
      .then((page) => { if (!cancelled) setIssues(page.content); })
      .catch((e: ApiError) => toast(e.message, 'error'));
    return () => { cancelled = true; };
  }, [project.id, activeSprint?.id, liveTick, toast]); // eslint-disable-line react-hooks/exhaustive-deps

  const visible = useMemo(() => {
    const t = text.trim().toLowerCase();
    return (issues ?? []).filter((i) =>
      (!t || i.title.toLowerCase().includes(t) || i.key.toLowerCase().includes(t)) &&
      (!typeFilter || i.type === typeFilter) &&
      (assigneeFilter === null || (assigneeFilter === 'none' ? !i.assignee : i.assignee?.id === assigneeFilter)));
  }, [issues, text, typeFilter, assigneeFilter]);

  const columns = useMemo(() => {
    const map = Object.fromEntries(STATUSES.map((s) => [s, [] as Issue[]])) as Record<IssueStatus, Issue[]>;
    visible.forEach((i) => map[i.status].push(i));
    STATUSES.forEach((s) => map[s].sort(byPosition));
    return map;
  }, [visible]);

  // PointerSensor needs a 6px move before dragging, so a normal click still opens the issue.
  // TouchSensor waits 200ms, so phones can still scroll the board.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
  );

  function onDragStart(e: DragStartEvent) {
    setDragging(issues?.find((i) => i.id === e.active.id) ?? null);
  }

  async function onDragEnd(e: DragEndEvent) {
    setDragging(null);
    const moved = issues?.find((i) => i.id === e.active.id);
    if (!moved || !e.over || !issues) return;

    // Work out the target column and the two neighbours the card lands between.
    const overId = String(e.over.id);
    let status: IssueStatus;
    let after: Issue | null = null;
    let before: Issue | null = null;
    if (overId.startsWith('col-')) {
      status = overId.slice(4) as IssueStatus;                  // dropped on empty space: go to bottom
      const col = columns[status].filter((i) => i.id !== moved.id);
      after = col[col.length - 1] ?? null;
    } else {
      const target = issues.find((i) => `card-${i.id}` === overId);
      if (!target || target.id === moved.id) return;
      status = target.status;                                    // dropped on a card: go just above it
      const col = columns[status].filter((i) => i.id !== moved.id);
      const idx = col.findIndex((i) => i.id === target.id);
      before = target;
      after = idx > 0 ? col[idx - 1] : null;
    }
    if (status === moved.status && after?.id === neighbour(moved, -1)?.id && before?.id === neighbour(moved, 1)?.id) return;

    // Optimistic UI: move the card now, confirm with the server in the background.
    const guess = after && before ? (after.position + before.position) / 2
      : after ? after.position + 1000 : before ? before.position - 1000 : moved.position;
    const previous = issues;
    setIssues(issues.map((i) => (i.id === moved.id ? { ...i, status, position: guess } : i)));
    try {
      const saved = await api.moveIssue(project.id, moved.id, status, after?.id ?? null, before?.id ?? null);
      setIssues((cur) => cur?.map((i) => (i.id === saved.id ? saved : i)) ?? cur);
    } catch (err) {
      setIssues(previous); // roll back
      toast(err instanceof ApiError ? err.message : 'Move failed', 'error');
    }
  }

  function neighbour(issue: Issue, offset: -1 | 1) {
    const col = columns[issue.status];
    const idx = col.findIndex((i) => i.id === issue.id);
    return col[idx + offset] ?? null;
  }

  const allPoints = (issues ?? []).reduce((s, i) => s + (i.storyPoints ?? 0), 0);
  const donePoints = (issues ?? []).filter((i) => i.status === 'DONE').reduce((s, i) => s + (i.storyPoints ?? 0), 0);
  const pct = allPoints ? Math.round((donePoints / allPoints) * 100) : 0;
  const left = daysLeft(activeSprint?.endDate ?? null);
  const filtersOn = Boolean(text || typeFilter || assigneeFilter !== null);

  return (
    <div className="board-page">
      <div className="page-head">
        <div className="min-w-0">
          <div className="crumbs">{project.name} <Icon name="chevronRight" size={13} /> Board</div>
          <h1 className="row gap-sm">
            {activeSprint ? activeSprint.name : 'All issues'}
            {activeSprint && <span className="badge badge-success">Active sprint</span>}
          </h1>
          <p className="page-sub">
            {activeSprint
              ? activeSprint.goal ?? 'No sprint goal set.'
              : <>No active sprint, so every issue is shown. <Link to="../backlog">Start a sprint</Link> to focus the board.</>}
          </p>
        </div>
        {activeSprint && (
          <div className="sprint-stats">
            <div className="stat">
              <span className="stat-label"><Icon name="calendar" size={13} /> {formatDate(activeSprint.startDate)} – {formatDate(activeSprint.endDate)}</span>
              <span className="stat-value">{left === null ? '—' : left < 0 ? 'Overdue' : left === 0 ? 'Ends today' : `${left} day${left === 1 ? '' : 's'} left`}</span>
            </div>
            <div className="stat stat-progress">
              <span className="stat-label">Progress</span>
              <span className="stat-value">{donePoints}<span className="muted"> / {allPoints} pts</span></span>
              <div className="progress" title={`${pct}% of story points done`}><div style={{ width: `${pct}%` }} /></div>
            </div>
          </div>
        )}
      </div>

      <div className="toolbar">
        <div className="input-icon">
          <Icon name="search" size={15} />
          <input placeholder="Filter board" value={text} onChange={(e) => setText(e.target.value)} />
        </div>
        <div className="avatar-stack" role="group" aria-label="Filter by assignee">
          {members.map((m) => (
            <button key={m.user.id} title={m.user.fullName}
                    className={`avatar-btn ${assigneeFilter === m.user.id ? 'active' : ''}`}
                    onClick={() => setAssigneeFilter(assigneeFilter === m.user.id ? null : m.user.id)}>
              <Avatar user={m.user} size={30} />
            </button>
          ))}
          <button className={`avatar-btn ${assigneeFilter === 'none' ? 'active' : ''}`} title="Unassigned"
                  onClick={() => setAssigneeFilter(assigneeFilter === 'none' ? null : 'none')}>
            <Avatar user={null} size={30} />
          </button>
        </div>
        <select className="select-compact" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as IssueType | '')} aria-label="Filter by type">
          <option value="">All types</option>
          {TYPES.map((t) => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}
        </select>
        <button className={`chip ${assigneeFilter === me.id ? 'chip-active' : ''}`}
                onClick={() => setAssigneeFilter(assigneeFilter === me.id ? null : me.id)}>Only my issues</button>
        {filtersOn && (
          <button className="btn btn-ghost btn-sm" onClick={() => { setText(''); setTypeFilter(''); setAssigneeFilter(null); }}>
            <Icon name="x" size={14} /> Clear filters
          </button>
        )}
      </div>

      {issues === null ? <Spinner label="Loading board…" /> : (
        <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd} onDragCancel={() => setDragging(null)}>
          <div className="board">
            {STATUSES.map((status) => (
              <Column key={status} status={status} count={columns[status].length}
                      points={columns[status].reduce((s, i) => s + (i.storyPoints ?? 0), 0)}>
                {columns[status].map((issue) => (
                  <DraggableCard key={issue.id} issue={issue} disabled={!canEdit} onOpen={() => openIssue(issue.id)} />
                ))}
                {columns[status].length === 0 && (
                  <div className="column-empty">{filtersOn ? 'No matching issues' : 'Drop issues here'}</div>
                )}
                {canEdit && status === 'TODO' && (
                  <button className="add-card" onClick={() => ctx.openCreate({ sprintId: activeSprint?.id ?? null })}>
                    <Icon name="plus" size={15} /> Create issue
                  </button>
                )}
              </Column>
            ))}
          </div>
          <DragOverlay dropAnimation={{ duration: 180, easing: 'cubic-bezier(.2,.8,.2,1)' }}>
            {dragging ? <CardBody issue={dragging} overlay /> : null}
          </DragOverlay>
        </DndContext>
      )}
    </div>
  );
}

function Column({ status, count, points, children }: { status: IssueStatus; count: number; points: number; children: ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: `col-${status}` });
  return (
    <section ref={setNodeRef} className={`column ${isOver ? 'column-over' : ''}`}>
      <header className="column-head">
        <span className={`dot dot-${status.toLowerCase()}`} />
        <span className="column-title">{STATUS_LABEL[status]}</span>
        <span className="count">{count}</span>
        {points > 0 && <span className="column-points" title="Story points">{points} pts</span>}
      </header>
      <div className="column-body">{children}</div>
    </section>
  );
}

function DraggableCard({ issue, disabled, onOpen }: { issue: Issue; disabled: boolean; onOpen: () => void }) {
  const drag = useDraggable({ id: issue.id, disabled });
  const drop = useDroppable({ id: `card-${issue.id}` });
  return (
    <div
      ref={(node) => { drag.setNodeRef(node); drop.setNodeRef(node); }}
      {...drag.listeners}
      {...drag.attributes}
      className={`card-wrap ${drag.isDragging ? 'card-ghost' : ''} ${drop.isOver && !drag.isDragging ? 'card-drop-above' : ''}`}
      onClick={onOpen}
      onKeyDown={(e) => { if (e.key === 'Enter') onOpen(); }}
    >
      <CardBody issue={issue} />
    </div>
  );
}

function CardBody({ issue, overlay }: { issue: Issue; overlay?: boolean }) {
  const done = issue.status === 'DONE';
  return (
    <article className={`card ${overlay ? 'card-overlay' : ''} ${done ? 'card-done' : ''}`}>
      <div className="card-title">{issue.title}</div>
      <div className="card-foot">
        <TypeIcon type={issue.type} />
        <span className={`issue-key ${done ? 'strike' : ''}`}>{issue.key}</span>
        <span className="grow" />
        <PriorityIcon priority={issue.priority} />
        {issue.storyPoints !== null && <span className="points" title="Story points">{issue.storyPoints}</span>}
        <Avatar user={issue.assignee} size={24} />
      </div>
    </article>
  );
}
