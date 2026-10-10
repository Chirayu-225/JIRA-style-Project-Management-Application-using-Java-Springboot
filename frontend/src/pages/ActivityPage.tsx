import { useEffect, useState } from 'react';
import { api, ApiError, type Activity } from '../api';
import { Avatar, EmptyState, Spinner, timeAgo } from '../components/bits';
import { Icon, type IconName } from '../components/icons';
import { useProject } from './ProjectLayout';

const ACTION_ICON: Record<string, IconName> = {
  issue: 'board', comment: 'message', sprint: 'target', member: 'users', project: 'folder',
};

export default function ActivityPage() {
  const { project, liveTick, openIssue, toast } = useProject();
  const [items, setItems] = useState<Activity[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);

  // First page (and refresh on live events)
  useEffect(() => {
    api.activity(project.id, 0)
      .then((p) => { setItems(p.content); setPage(0); setHasMore(p.totalPages > 1); })
      .catch((e: ApiError) => toast(e.message, 'error'))
      .finally(() => setLoading(false));
  }, [project.id, liveTick, toast]);

  async function loadMore() {
    const next = page + 1;
    const p = await api.activity(project.id, next);
    setItems((cur) => [...cur, ...p.content]);
    setPage(next);
    setHasMore(next + 1 < p.totalPages);
  }

  return (
    <div className="narrow">
      <div className="page-head">
        <div>
          <div className="crumbs">{project.name} <Icon name="chevronRight" size={13} /> Activity</div>
          <h1>Activity</h1>
          <p className="page-sub">An audit trail of every change in this project.</p>
        </div>
      </div>
      {loading && <Spinner />}
      {!loading && items.length === 0 && <EmptyState icon="activity" title="No activity yet" />}
      <ol className="feed">
        {items.map((a) => (
          <li key={a.id} className="feed-item">
            <span className="feed-icon"><Icon name={ACTION_ICON[a.action.split('.')[0]] ?? 'activity'} size={14} /></span>
            <div className="feed-card">
              <div className="feed-line">
                <Avatar user={a.actor} size={22} />
                <strong>{a.actor.fullName}</strong>
                <span className="muted">{a.action.replace('.', ' ')}</span>
                <span className="grow" />
                <span className="muted small" title={new Date(a.createdAt).toLocaleString()}>{timeAgo(a.createdAt)}</span>
              </div>
              {a.details && (
                <div className="feed-detail">
                  {a.issueId
                    ? <button className="link-btn" onClick={() => openIssue(a.issueId!)}>{a.details}</button>
                    : a.details}
                </div>
              )}
            </div>
          </li>
        ))}
      </ol>
      {hasMore && <div className="center"><button className="btn btn-secondary" onClick={loadMore}>Load more</button></div>}
    </div>
  );
}
