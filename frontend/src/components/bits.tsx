import { useEffect, type ReactNode } from 'react';
import { STATUS_LABEL, type IssueStatus, type IssueType, type Priority, type UserSummary } from '../api';
import { Icon, type IconName } from './icons';

// ---------------------------------------------------------------- avatars

const AVATAR_COLORS = ['#6366f1', '#0ea5e9', '#14b8a6', '#f59e0b', '#ec4899', '#8b5cf6', '#ef4444', '#22c55e'];

export const colorFor = (id: number) => AVATAR_COLORS[Math.abs(id) % AVATAR_COLORS.length];

export const initialsOf = (name: string) =>
  name.split(/\s+/).filter(Boolean).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

export function Avatar({ user, size = 26 }: { user: UserSummary | null; size?: number }) {
  if (!user) {
    return (
      <span className="avatar avatar-empty" style={{ width: size, height: size }} title="Unassigned">
        <Icon name="users" size={Math.round(size * 0.5)} />
      </span>
    );
  }
  return (
    <span className="avatar" title={user.fullName}
          style={{ width: size, height: size, fontSize: Math.max(9, size * 0.4), background: colorFor(user.id) }}>
      {initialsOf(user.fullName)}
    </span>
  );
}

// ---------------------------------------------------------------- issue type / priority / status

const TYPE_META: Record<IssueType, { label: string; glyph: ReactNode }> = {
  STORY: { label: 'Story', glyph: <path d="M8 5h8v14l-4-3-4 3z" fill="currentColor" stroke="none" /> },
  TASK: { label: 'Task', glyph: <path d="m7.5 12.5 3 3 6-7" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" /> },
  BUG: { label: 'Bug', glyph: <circle cx="12" cy="12" r="4.2" fill="currentColor" /> },
};

export function TypeIcon({ type, size = 16 }: { type: IssueType; size?: number }) {
  const meta = TYPE_META[type];
  return (
    <span className={`type-badge type-${type.toLowerCase()}`} title={meta.label} aria-label={meta.label}
          style={{ width: size, height: size }}>
      <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">{meta.glyph}</svg>
    </span>
  );
}

const PRIORITY_ICON: Record<Priority, IconName> = {
  HIGHEST: 'prioHighest', HIGH: 'prioHigh', MEDIUM: 'prioMedium', LOW: 'prioLow', LOWEST: 'prioLowest',
};

export function PriorityIcon({ priority, size = 16 }: { priority: Priority; size?: number }) {
  return (
    <span className={`prio prio-${priority.toLowerCase()}`} title={`Priority: ${priority.toLowerCase()}`}>
      <Icon name={PRIORITY_ICON[priority]} size={size} strokeWidth={2.2} />
    </span>
  );
}

export function StatusPill({ status }: { status: IssueStatus }) {
  return (
    <span className={`status-pill status-${status.toLowerCase()}`}>
      <span className="dot" />{STATUS_LABEL[status]}
    </span>
  );
}

// ---------------------------------------------------------------- layout helpers

export function Modal({ title, onClose, children, size = 'md', actions }: {
  title: ReactNode; onClose: () => void; children: ReactNode; size?: 'md' | 'lg'; actions?: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = prev; };
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`modal modal-${size}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div className="modal-title">{title}</div>
          <div className="row gap-xs">
            {actions}
            <button className="icon-btn" onClick={onClose} aria-label="Close"><Icon name="x" size={18} /></button>
          </div>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

export function EmptyState({ icon = 'inbox', title, children }: { icon?: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon"><Icon name={icon} size={22} /></span>
      <div className="empty-title">{title}</div>
      {children && <div className="empty-body">{children}</div>}
    </div>
  );
}

export function ErrorText({ error }: { error: string | null }) {
  return error ? <div className="error-text" role="alert"><Icon name="alert" size={14} />{error}</div> : null;
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return <div className="loading"><span className="spinner" />{label}</div>;
}

// ---------------------------------------------------------------- time

export function timeAgo(iso: string): string {
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function daysLeft(endDate: string | null): number | null {
  if (!endDate) return null;
  const end = new Date(endDate + 'T23:59:59').getTime();
  return Math.ceil((end - Date.now()) / 86_400_000);
}
