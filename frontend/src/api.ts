import { clearToken, currentToken } from "./auth";
import { API_URL } from "./config";
import type {
  AlertSourceStatus,
  Application,
  ApplicationInput,
  Dashboard,
  EmailRecord,
  GmailStatus,
  JobResult,
  Me,
  Status,
  SyncResult,
} from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = currentToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const init: RequestInit = {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  };

  // The free server sleeps when idle and takes 1-3 minutes to start. While it starts, requests
  // fail outright or get Render's 502/503 page, so wait and retry instead of showing an error.
  let response: Response | null = null;
  let data: unknown = null;
  for (let attempt = 0; ; attempt++) {
    try {
      response = await fetch(API_URL + path, init);
      data = response.status === 204 ? null : await response.json().catch(() => null);
      // Our own errors are JSON; a non-JSON 502/503/504 comes from Render while the app starts
      const starting = [502, 503, 504].includes(response.status) && data === null;
      if (!starting) {
        break;
      }
    } catch {
      response = null; // network error: the server isn't accepting connections yet
    }
    if (attempt >= WAKE_MAX_ATTEMPTS) {
      setWaking(false);
      throw new ApiError("The server didn't start in time. Try again in a minute.", 0);
    }
    setWaking(true);
    await new Promise((resolve) => window.setTimeout(resolve, WAKE_RETRY_MS));
  }
  setWaking(false);

  if (response.status === 401) {
    clearToken();
    window.location.hash = "#/login";
    throw new ApiError("Your session expired. Sign in again.", 401);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    const error = (data ?? {}) as { message?: string; fieldErrors?: Record<string, string> };
    throw new ApiError(error.message ?? `Request failed (${response.status})`, response.status, error.fieldErrors ?? {});
  }
  return data as T;
}

const WAKE_RETRY_MS = 5000;
const WAKE_MAX_ATTEMPTS = 36; // about 3 minutes

let waking = false;

/** Tells the layout to show or hide the "Starting the server…" banner. */
function setWaking(value: boolean): void {
  if (waking !== value) {
    waking = value;
    window.dispatchEvent(new CustomEvent("server-waking", { detail: value }));
  }
}

export const api = {
  me: () => request<Me>("/api/me"),
  updateSkills: (skills: string[]) => request<Me>("/api/me/skills", { method: "PUT", body: { skills } }),

  dashboard: () => request<Dashboard>("/api/dashboard"),

  applications: () => request<Application[]>("/api/applications"),
  createApplication: (input: ApplicationInput) =>
    request<Application>("/api/applications", { method: "POST", body: input }),
  updateApplication: (id: number, input: ApplicationInput) =>
    request<Application>(`/api/applications/${id}`, { method: "PUT", body: input }),
  changeStatus: (id: number, status: Status) =>
    request<Application>(`/api/applications/${id}/status`, { method: "PATCH", body: { status } }),
  deleteApplication: (id: number) => request<void>(`/api/applications/${id}`, { method: "DELETE" }),

  gmailStatus: () => request<GmailStatus>("/api/gmail/status"),
  syncGmail: () => request<SyncResult>("/api/gmail/sync", { method: "POST" }),
  disconnectGmail: () => request<void>("/api/gmail", { method: "DELETE" }),
  emails: (view: "review" | "all") => request<EmailRecord[]>(`/api/gmail/emails?view=${view}`),
  acceptEmail: (id: number, body: { company: string; status: Status; role: string | null }) =>
    request<EmailRecord>(`/api/gmail/emails/${id}/accept`, { method: "POST", body }),
  dismissEmail: (id: number) => request<void>(`/api/gmail/emails/${id}/dismiss`, { method: "POST" }),
  undoEmail: (id: number) => request<EmailRecord>(`/api/gmail/emails/${id}/undo`, { method: "POST" }),

  alertJobs: () => request<JobResult[]>("/api/jobs/alerts"),
  alertStatus: () => request<AlertSourceStatus[]>("/api/jobs/alert-status"),
};
