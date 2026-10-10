// ---------------------------------------------------------------------------
// Typed API client. Every call to the Spring Boot backend goes through here.
// ---------------------------------------------------------------------------

export type Role = 'VIEWER' | 'MEMBER' | 'ADMIN' | 'OWNER';
export type IssueStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';
export type IssueType = 'STORY' | 'TASK' | 'BUG';
export type Priority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST';
export type SprintStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED';

export const STATUSES: IssueStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
export const STATUS_LABEL: Record<IssueStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  IN_REVIEW: 'In Review',
  DONE: 'Done',
};
export const TYPES: IssueType[] = ['STORY', 'TASK', 'BUG'];
export const PRIORITIES: Priority[] = ['HIGHEST', 'HIGH', 'MEDIUM', 'LOW', 'LOWEST'];
export const ROLES: Role[] = ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER'];

export interface UserSummary { id: number; fullName: string; email: string }
export interface AuthResponse { token: string; user: UserSummary }
export interface Project {
  id: number; key: string; name: string; description: string | null;
  owner: UserSummary; myRole: Role; memberCount: number; createdAt: string;
}
export interface Member { user: UserSummary; role: Role; joinedAt: string }
export interface Sprint {
  id: number; name: string; goal: string | null; status: SprintStatus;
  startDate: string | null; endDate: string | null; createdAt: string;
}
export interface Issue {
  id: number; key: string; number: number; title: string; description: string | null;
  type: IssueType; status: IssueStatus; priority: Priority; storyPoints: number | null;
  assignee: UserSummary | null; reporter: UserSummary; sprintId: number | null; sprintName: string | null;
  position: number; version: number; createdAt: string; updatedAt: string;
}
export interface Comment { id: number; body: string; author: UserSummary; createdAt: string }
export interface Activity {
  id: number; issueId: number | null; action: string; details: string | null;
  actor: UserSummary; createdAt: string;
}
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface ProjectEvent { projectId: number; type: string; issueId: number | null; actorId: number; message: string }

export interface IssueInput {
  title: string; description: string | null; type: IssueType; priority: Priority; status: IssueStatus;
  storyPoints: number | null; assigneeId: number | null; sprintId: number | null; version?: number;
}

export const API_URL: string = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');

const TOKEN_KEY = 'it_token';
export const tokenStore = {
  get: (): string | null => { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } },
  set: (t: string) => { try { localStorage.setItem(TOKEN_KEY, t); } catch { /* private mode */ } },
  clear: () => { try { localStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ } },
};

/** Error with the HTTP status and per-field validation messages from the backend. */
export class ApiError extends Error {
  constructor(public status: number, message: string, public fieldErrors: Record<string, string> = {}) {
    super(message);
  }
}

/** Free-tier servers sleep; tell the UI when a request is slow so it can explain why. */
type SlowListener = (slow: boolean) => void;
const slowListeners = new Set<SlowListener>();
export function onSlowRequest(fn: SlowListener) { slowListeners.add(fn); return () => { slowListeners.delete(fn); }; }
let inFlightSlow = 0;

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let markedSlow = false;
  const timer = setTimeout(() => {
    markedSlow = true;
    inFlightSlow += 1;
    slowListeners.forEach((l) => l(true));
  }, 4000);

  let res: Response;
  try {
    res = await fetch(API_URL + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection or try again in a minute.');
  } finally {
    clearTimeout(timer);
    if (markedSlow) {
      inFlightSlow -= 1;
      if (inFlightSlow === 0) slowListeners.forEach((l) => l(false));
    }
  }

  if (res.status === 401 && token) {
    tokenStore.clear();
    window.dispatchEvent(new Event('app:logout'));
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    let fieldErrors: Record<string, string> = {};
    try {
      const problem = await res.json();
      message = problem.detail || problem.title || message;
      fieldErrors = problem.errors || {};
    } catch { /* non-JSON error body */ }
    throw new ApiError(res.status, message, fieldErrors);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

const qs = (params: Record<string, string | number | boolean | null | undefined>) => {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)); });
  const str = s.toString();
  return str ? `?${str}` : '';
};

export const api = {
  // auth
  register: (email: string, password: string, fullName: string) =>
    request<AuthResponse>('POST', '/api/auth/register', { email, password, fullName }),
  login: (email: string, password: string) => request<AuthResponse>('POST', '/api/auth/login', { email, password }),
  me: () => request<UserSummary>('GET', '/api/users/me'),

  // projects
  projects: () => request<Project[]>('GET', '/api/projects'),
  project: (id: number) => request<Project>('GET', `/api/projects/${id}`),
  createProject: (key: string, name: string, description: string) =>
    request<Project>('POST', '/api/projects', { key, name, description }),
  updateProject: (id: number, name: string, description: string) =>
    request<Project>('PUT', `/api/projects/${id}`, { name, description }),
  deleteProject: (id: number) => request<void>('DELETE', `/api/projects/${id}`),

  // members
  members: (pid: number) => request<Member[]>('GET', `/api/projects/${pid}/members`),
  addMember: (pid: number, email: string, role: Role) =>
    request<Member>('POST', `/api/projects/${pid}/members`, { email, role }),
  changeRole: (pid: number, userId: number, role: Role) =>
    request<Member>('PUT', `/api/projects/${pid}/members/${userId}`, { role }),
  removeMember: (pid: number, userId: number) => request<void>('DELETE', `/api/projects/${pid}/members/${userId}`),

  // sprints
  sprints: (pid: number) => request<Sprint[]>('GET', `/api/projects/${pid}/sprints`),
  createSprint: (pid: number, name: string, goal: string) =>
    request<Sprint>('POST', `/api/projects/${pid}/sprints`, { name, goal }),
  startSprint: (pid: number, sid: number) => request<Sprint>('POST', `/api/projects/${pid}/sprints/${sid}/start`),
  completeSprint: (pid: number, sid: number) => request<Sprint>('POST', `/api/projects/${pid}/sprints/${sid}/complete`),
  deleteSprint: (pid: number, sid: number) => request<void>('DELETE', `/api/projects/${pid}/sprints/${sid}`),

  // issues
  issues: (pid: number, f: { sprintId?: number; backlog?: boolean; q?: string; size?: number } = {}) =>
    request<Page<Issue>>('GET', `/api/projects/${pid}/issues${qs({ size: 500, ...f })}`),
  search: (pid: number, q: string) => request<Issue[]>('GET', `/api/projects/${pid}/issues/search${qs({ q })}`),
  issue: (pid: number, id: number) => request<Issue>('GET', `/api/projects/${pid}/issues/${id}`),
  createIssue: (pid: number, input: IssueInput) => request<Issue>('POST', `/api/projects/${pid}/issues`, input),
  updateIssue: (pid: number, id: number, input: IssueInput) =>
    request<Issue>('PUT', `/api/projects/${pid}/issues/${id}`, input),
  moveIssue: (pid: number, id: number, status: IssueStatus, afterIssueId: number | null, beforeIssueId: number | null) =>
    request<Issue>('POST', `/api/projects/${pid}/issues/${id}/move`, { status, afterIssueId, beforeIssueId }),
  deleteIssue: (pid: number, id: number) => request<void>('DELETE', `/api/projects/${pid}/issues/${id}`),

  // comments + activity
  comments: (pid: number, id: number) => request<Comment[]>('GET', `/api/projects/${pid}/issues/${id}/comments`),
  addComment: (pid: number, id: number, body: string) =>
    request<Comment>('POST', `/api/projects/${pid}/issues/${id}/comments`, { body }),
  deleteComment: (pid: number, id: number, cid: number) =>
    request<void>('DELETE', `/api/projects/${pid}/issues/${id}/comments/${cid}`),
  issueActivity: (pid: number, id: number) => request<Activity[]>('GET', `/api/projects/${pid}/issues/${id}/activity`),
  activity: (pid: number, page = 0) => request<Page<Activity>>('GET', `/api/projects/${pid}/activity${qs({ page, size: 30 })}`),

  /** URL for the live-update stream. EventSource can't send headers, so the token goes in the query. */
  eventsUrl: (pid: number) => `${API_URL}/api/projects/${pid}/events${qs({ access_token: tokenStore.get() })}`,
};

/** Turn an issue into the shape PUT /issues/{id} expects. */
export const toInput = (i: Issue): IssueInput => ({
  title: i.title, description: i.description, type: i.type, priority: i.priority, status: i.status,
  storyPoints: i.storyPoints, assigneeId: i.assignee?.id ?? null, sprintId: i.sprintId, version: i.version,
});

export const roleAtLeast = (role: Role | undefined, needed: Role) =>
  role !== undefined && ROLES.indexOf(role) >= ROLES.indexOf(needed);
