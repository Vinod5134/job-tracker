export type Status = "SAVED" | "APPLIED" | "INTERVIEW" | "OFFER" | "REJECTED";
export type Source = "NAUKRI" | "LINKEDIN" | "COMPANY_SITE" | "REFERRAL" | "JOB_SEARCH" | "EMAIL" | "OTHER";

export const STATUSES: Status[] = ["SAVED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED"];

export const STATUS_LABEL: Record<Status, string> = {
  SAVED: "Saved",
  APPLIED: "Applied",
  INTERVIEW: "Interview",
  OFFER: "Offer",
  REJECTED: "Rejected",
};

export const SOURCE_LABEL: Record<Source, string> = {
  NAUKRI: "Naukri",
  LINKEDIN: "LinkedIn",
  COMPANY_SITE: "Company site",
  REFERRAL: "Referral",
  JOB_SEARCH: "Job search",
  EMAIL: "From email",
  OTHER: "Other",
};

export interface Application {
  id: number;
  company: string;
  jobRole: string | null;
  location: string | null;
  source: Source;
  status: Status;
  jobUrl: string | null;
  appliedOn: string | null;
  interviewAt: string | null;
  salary: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  statusChangedAt: string;
}

export interface ApplicationInput {
  company: string;
  jobRole?: string | null;
  location?: string | null;
  source?: Source;
  status?: Status;
  jobUrl?: string | null;
  appliedOn?: string | null;
  interviewAt?: string | null;
  salary?: string | null;
  notes?: string | null;
}

export interface Activity {
  id: number;
  applicationId: number | null;
  message: string;
  origin: "MANUAL" | "EMAIL" | "SYSTEM";
  createdAt: string;
}

export interface Dashboard {
  total: number;
  counts: Record<Status, number>;
  responseRate: number;
  upcomingInterviews: Application[];
  followUps: Application[];
  weekly: { weekStart: string; count: number }[];
  recentActivity: Activity[];
}

export interface GmailStatus {
  connected: boolean;
  email: string | null;
  lastSyncedAt: string | null;
  lastError: string | null;
  needsReview: number;
}

export interface Me {
  id: number;
  name: string | null;
  email: string;
  pictureUrl: string | null;
  skills: string[];
  gmail: GmailStatus;
}

export interface EmailRecord {
  id: number;
  from: string | null;
  subject: string | null;
  receivedAt: string | null;
  detectedStatus: Status | null;
  detectedCompany: string | null;
  detectedRole: string | null;
  state: "AUTO_APPLIED" | "NO_CHANGE" | "NEEDS_REVIEW" | "ACCEPTED" | "DISMISSED";
  applicationId: number | null;
}

export interface SyncResult {
  scanned: number;
  detected: number;
  autoUpdated: number;
  needsReview: number;
  skipped: boolean;
}

export interface JobResult {
  id: string;
  /** ADZUNA, or LINKEDIN / NAUKRI for jobs from his alert emails */
  source: "ADZUNA" | "LINKEDIN" | "NAUKRI";
  title: string;
  company: string;
  location: string;
  description: string;
  url: string;
  postedAt: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  matchScore: number;
  matchedSkills: string[];
  fresherFriendly: boolean;
  saved: boolean;
}

export interface JobSearchResponse {
  total: number;
  page: number;
  results: JobResult[];
}

export type LiveEventType =
  | "APPLICATION_CREATED"
  | "APPLICATION_UPDATED"
  | "APPLICATION_DELETED"
  | "ACTIVITY"
  | "NOTIFICATION"
  | "EMAILS_CHANGED"
  | "GMAIL_STATUS"
  | "ALERT_JOBS";

export interface LiveEvent {
  type: LiveEventType;
  payload: unknown;
  at: string;
}

export interface Notice {
  title: string;
  message: string;
  level: "info" | "success" | "warning" | "error";
}
