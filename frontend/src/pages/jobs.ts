import { api } from "../api";
import { errorBox, h, loading, safeUrl, setChildren } from "../dom";
import { timeAgo } from "../format";
import { onLive } from "../live";
import { toast, toastError } from "../toast";
import type { GmailStatus, JobResult, JobSite, Source } from "../types";

const PREFS_KEY = "jobtracker.jobPrefs";
const AUTO_CHECK_AFTER_MS = 2 * 60 * 1000;

const SITE_NAME: Record<JobSite, string> = { LINKEDIN: "LinkedIn", NAUKRI: "Naukri", INDEED: "Indeed" };
const SAVE_SOURCE: Record<JobSite, Source> = { LINKEDIN: "LINKEDIN", NAUKRI: "NAUKRI", INDEED: "INDEED" };

/**
 * Jobs from the LinkedIn, Naukri and Indeed alert emails in the user's Gmail.
 * The backend checks Gmail every few minutes (and right away when this page opens);
 * new jobs arrive as live events and appear without a reload.
 */
export function renderJobs(root: HTMLElement): () => void {
  let alive = true;
  let jobs: JobResult[] = [];
  let gmail: GmailStatus | null = null;
  const newIds = new Set<string>();
  const prefs = readPrefs();

  // ---- Filters ----
  let site: JobSite | "ALL" = "ALL";
  const filterText = h("input", { type: "search", placeholder: "Filter by title, company or city", "aria-label": "Filter jobs" });
  const fresherOnly = h("input", { type: "checkbox", checked: prefs.fresherOnly });
  const siteChips = h("div", { class: "chips filter-chips", role: "group", "aria-label": "Job site" });
  const status = h("span", { class: "muted small" });
  const checkBtn = h("button", { class: "btn", type: "button" }, "Check now");
  const list = h("div", { class: "job-results" });

  const visible = () => {
    const q = filterText.value.trim().toLowerCase();
    return jobs.filter((j) =>
      (site === "ALL" || j.source === site)
      && (!fresherOnly.checked || j.fresherFriendly)
      && (!q || [j.title, j.company, j.location].some((v) => v.toLowerCase().includes(q))));
  };

  const drawChips = () => {
    const count = (s: JobSite) => jobs.filter((j) => j.source === s).length;
    setChildren(
      siteChips,
      (["ALL", "LINKEDIN", "NAUKRI", "INDEED"] as const).map((s) =>
        h("button", {
          class: site === s ? "chip chip-on" : "chip",
          type: "button",
          "aria-pressed": String(site === s),
          onClick: () => {
            site = s;
            draw();
          },
        }, s === "ALL" ? `All (${jobs.length})` : `${SITE_NAME[s]} (${count(s)})`),
      ),
    );
  };

  const drawStatus = () => {
    status.textContent = gmail?.lastSyncedAt ? `Checked ${timeAgo(gmail.lastSyncedAt)} · checks every 5 minutes` : "";
  };

  const draw = () => {
    drawChips();
    drawStatus();
    savePrefs({ fresherOnly: fresherOnly.checked });
    if (!gmail?.connected) {
      setChildren(
        list,
        h("div", { class: "empty" },
          h("h2", null, "Connect Gmail to get jobs here"),
          h("p", { class: "muted" }, "Jobs from your LinkedIn, Naukri and Indeed job alerts appear here automatically, scored against your skills."),
          h("a", { class: "btn btn-primary", href: "#/settings" }, "Connect Gmail")),
      );
      return;
    }
    if (!jobs.length) {
      setChildren(list, setupHelp());
      return;
    }
    const shown = visible();
    setChildren(
      list,
      shown.length
        ? shown.map((job) => jobCard(job, newIds.has(job.id)))
        : h("p", { class: "muted" }, "No jobs match these filters. Clear the filter or untick \"Fresher-friendly only\"."),
    );
  };

  const load = async (fromLive = false) => {
    try {
      const before = new Set(jobs.map((j) => j.id));
      const [status, feed] = await Promise.all([api.gmailStatus(), api.alertJobs()]);
      if (!alive) {
        return;
      }
      gmail = status;
      if (fromLive) {
        feed.filter((j) => !before.has(j.id)).forEach((j) => newIds.add(j.id));
      }
      jobs = feed;
      draw();
    } catch (e) {
      if (alive) {
        list.replaceChildren(errorBox((e as Error).message, () => load()));
      }
    }
  };

  const checkNow = async (quiet: boolean) => {
    checkBtn.setAttribute("disabled", "");
    checkBtn.textContent = "Checking…";
    try {
      await api.syncGmail();
      await load(true);
    } catch (e) {
      if (!quiet) {
        toastError((e as Error).message);
      }
    } finally {
      checkBtn.removeAttribute("disabled");
      checkBtn.textContent = "Check now";
    }
  };
  checkBtn.addEventListener("click", () => checkNow(false));
  filterText.addEventListener("input", draw);
  fresherOnly.addEventListener("change", draw);

  // ---- Live search on the sites themselves ----
  const what = h("input", { value: prefs.what, placeholder: "java developer", "aria-label": "Job title" });
  const where = h("input", { value: prefs.where, placeholder: "Hyderabad", "aria-label": "City" });
  const siteLinks = h("div", { class: "site-links" });
  const drawSiteLinks = () => {
    savePrefs({ what: what.value, where: where.value });
    const search = { what: what.value.trim() || "java developer", where: where.value.trim(), fresher: fresherOnly.checked };
    setChildren(
      siteLinks,
      JOB_SITES.map((s) =>
        h("a", { class: `btn btn-site site-${s.id}`, href: s.url(search), target: "_blank", rel: "noopener noreferrer" }, s.name),
      ),
    );
  };
  [what, where].forEach((input) => input.addEventListener("input", drawSiteLinks));
  fresherOnly.addEventListener("change", drawSiteLinks);
  drawSiteLinks();

  root.replaceChildren(
    h(
      "div",
      { class: "page" },
      h("div", { class: "page-head" }, h("h1", null, "Find jobs"), checkBtn),
      h("p", { class: "muted" }, "Jobs from your LinkedIn, Naukri and Indeed alerts, best skill match first. New ones appear here as they arrive."),
      h("div", { class: "toolbar wrap" }, siteChips, h("label", { class: "check" }, fresherOnly, "Fresher-friendly only"), status),
      h("div", { class: "toolbar" }, filterText),
      list,
      h(
        "section",
        { class: "card live-search" },
        h("h2", null, "Search the sites directly"),
        h("p", { class: "muted small" }, "Opens each site's own live results for this title and city (posted in the last 7 days)."),
        h("div", { class: "search-form two" }, what, where),
        siteLinks,
      ),
    ),
  );
  list.replaceChildren(loading("Loading your jobs"));

  load().then(() => {
    // Check Gmail right away if the last check is a few minutes old, so the feed is fresh on open
    const last = gmail?.lastSyncedAt ? Date.parse(gmail.lastSyncedAt) : 0;
    if (alive && gmail?.connected && Date.now() - last > AUTO_CHECK_AFTER_MS) {
      checkNow(true);
    }
  });

  const unsubscribe = onLive((event) => {
    if (event.type === "ALERT_JOBS") {
      const added = (event.payload as { added?: number }).added ?? 0;
      if (added > 0) {
        toast({ title: "New jobs from your alerts", message: `${added} new job${added === 1 ? "" : "s"}`, level: "success" }, 4000);
      }
      load(true);
    } else if (event.type === "GMAIL_STATUS") {
      gmail = event.payload as GmailStatus;
      drawStatus();
    }
  });

  return () => {
    alive = false;
    unsubscribe();
  };
}

function jobCard(job: JobResult, isNew: boolean): HTMLElement {
  const url = safeUrl(job.url);
  const saveBtn = h("button", { class: "btn", type: "button", disabled: job.saved }, job.saved ? "Saved" : "Save to tracker");
  saveBtn.addEventListener("click", async () => {
    saveBtn.setAttribute("disabled", "");
    try {
      await api.createApplication({
        company: (job.company || "Unknown company").slice(0, 150),
        jobRole: job.title.slice(0, 150),
        location: job.location.slice(0, 150) || null,
        source: SAVE_SOURCE[job.source],
        status: "SAVED",
        jobUrl: url,
      });
      saveBtn.textContent = "Saved";
      toast({ title: "Saved to your board", message: `${job.company || job.title} is in the Saved column`, level: "success" }, 3000);
    } catch (e) {
      saveBtn.removeAttribute("disabled");
      toastError((e as Error).message);
    }
  });

  const scoreClass = job.matchScore >= 50 ? "score score-high" : job.matchScore >= 25 ? "score score-mid" : "score";
  return h(
    "article",
    { class: isNew ? "card job-result flash" : "card job-result" },
    h(
      "div",
      { class: "job-result-head" },
      h("div", null, h("h2", null, job.title), h("p", { class: "muted" }, [job.company, job.location].filter(Boolean).join(" · "))),
      h("span", { class: scoreClass, title: "How many of your skills this job mentions" }, `${job.matchScore}% match`),
    ),
    h(
      "div",
      { class: "chips" },
      h("span", { class: `pill site-${job.source.toLowerCase()}` }, SITE_NAME[job.source]),
      isNew ? h("span", { class: "pill pill-accent" }, "New") : null,
      job.fresherFriendly ? h("span", { class: "pill pill-success" }, "Fresher-friendly") : null,
      h("span", { class: "pill" }, `Alert ${timeAgo(job.receivedAt)}`),
      job.matchedSkills.map((s) => h("span", { class: "pill pill-accent" }, s)),
    ),
    h(
      "div",
      { class: "job-actions" },
      url ? h("a", { class: "btn btn-primary", href: url, target: "_blank", rel: "noopener noreferrer" }, `Apply on ${SITE_NAME[job.source]}`) : null,
      saveBtn,
    ),
  );
}

/** Shown until the first alert emails arrive. */
function setupHelp(): HTMLElement {
  return h(
    "div",
    { class: "empty setup" },
    h("h2", null, "Turn on job alerts to fill this page"),
    h("p", { class: "muted" }, "This page lists jobs from the alert emails these sites send you. Create an alert once on each:"),
    h(
      "ol",
      { class: "setup-steps" },
      h("li", null, h("strong", null, "LinkedIn: "), "search \"Java Developer\" in your city, filter Experience level → Entry level, then switch on ", h("em", null, "Set alert"), "."),
      h("li", null, h("strong", null, "Naukri: "), "search \"Java Developer\", set Experience to 0 years, then click ", h("em", null, "Create job alert"), "."),
      h("li", null, h("strong", null, "Indeed: "), "search \"Java Developer fresher\" in your city, then click ", h("em", null, "Get new jobs for this search by email"), ". Choose the most frequent option for the fastest updates."),
    ),
    h("p", { class: "muted small" }, "Use the same Gmail you connected here. Alerts are checked every 5 minutes."),
  );
}

interface SiteSearch {
  what: string;
  where: string;
  fresher: boolean;
}

/** "Java Developer" -> "java-developer" (Naukri's URL style). */
function slug(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

/** Common spellings people type that the job sites don't recognise. */
function city(where: string): string {
  return /^(banglore|bengaluru|bengalore)$/i.test(where) ? "Bangalore" : where;
}

/** Search URLs on each site's own website, filtered to recent posts (and entry level for freshers). */
const JOB_SITES: { id: string; name: string; url: (s: SiteSearch) => string }[] = [
  {
    id: "linkedin",
    name: "LinkedIn",
    url: (s) => {
      const p = new URLSearchParams({ keywords: s.what, f_TPR: "r604800", sortBy: "DD" });
      if (s.where) {
        p.set("location", city(s.where));
      }
      if (s.fresher) {
        p.set("f_E", "1,2"); // internship + entry level
      }
      return `https://www.linkedin.com/jobs/search/?${p}`;
    },
  },
  {
    id: "naukri",
    name: "Naukri",
    url: (s) => {
      const path = s.where ? `${slug(s.what)}-jobs-in-${slug(city(s.where))}` : `${slug(s.what)}-jobs`;
      const p = new URLSearchParams({ jobAge: "7" });
      if (s.fresher) {
        p.set("experience", "0");
      }
      return `https://www.naukri.com/${path}?${p}`;
    },
  },
  {
    id: "indeed",
    name: "Indeed",
    url: (s) => {
      const p = new URLSearchParams({ q: s.fresher ? `${s.what} fresher` : s.what, fromage: "7", sort: "date" });
      if (s.where) {
        p.set("l", city(s.where));
      }
      return `https://in.indeed.com/jobs?${p}`;
    },
  },
  {
    id: "foundit",
    name: "Foundit",
    url: (s) => {
      const p = new URLSearchParams({ query: s.what });
      if (s.where) {
        p.set("locations", city(s.where));
      }
      if (s.fresher) {
        p.set("experienceRanges", "0~1");
      }
      return `https://www.foundit.in/srp/results?${p}`;
    },
  },
];

interface Prefs {
  what: string;
  where: string;
  fresherOnly: boolean;
}

function readPrefs(): Prefs {
  const defaults: Prefs = { what: "java developer", where: "Hyderabad", fresherOnly: false };
  try {
    const parsed = JSON.parse(localStorage.getItem(PREFS_KEY) ?? "null");
    return parsed && typeof parsed === "object" ? { ...defaults, ...parsed } : defaults;
  } catch {
    return defaults;
  }
}

function savePrefs(update: Partial<Prefs>): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ ...readPrefs(), ...update }));
  } catch {
    // ignore
  }
}
