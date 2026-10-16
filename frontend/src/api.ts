import { clearToken, currentToken } from "./auth";
import { API_URL } from "./config";
import type {
  Application,
  ApplicationInput,
  Dashboard,
  EmailRecord,
  GmailStatus,
  JobSearchResponse,
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

  let response: Response;
  try {
    response = await fetch(API_URL + path, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError("Can't reach the server. If it was asleep, it can take up to a minute to wake up.", 0);
  }

  if (response.status === 401) {
    clearToken();
    window.location.hash = "#/login";
    throw new ApiError("Your session expired. Sign in again.", 401);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(data?.message ?? `Request failed (${response.status})`, response.status, data?.fieldErrors ?? {});
  }
  return data as T;
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

  searchJobs: (what: string, where: string, page: number) => {
    const params = new URLSearchParams({ what, page: String(page) });
    if (where.trim()) {
      params.set("where", where.trim());
    }
    return request<JobSearchResponse>(`/api/jobs/search?${params}`);
  },
};
