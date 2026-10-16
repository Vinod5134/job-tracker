import { api } from "../api";
import { errorBox, h, loading, safeUrl, setChildren } from "../dom";
import { salaryRange, timeAgo } from "../format";
import { onLive } from "../live";
import { toast, toastError } from "../toast";
import type { JobResult, Source } from "../types";

const SAVED_KEY = "jobtracker.lastSearch";

/** Job search (Adzuna API), sorted by how well each job matches the user's skills. */
export function renderJobs(root: HTMLElement): () => void {
  let alive = true;
  let page = 1;
  const last = readLastSearch();

  const what = h("input", { name: "what", value: last.what, placeholder: "java developer fresher", "aria-label": "Job title or skills" });
  const where = h("input", { name: "where", value: last.where, placeholder: "Hyderabad", "aria-label": "City" });
  const fresherOnly = h("input", { type: "checkbox", checked: last.fresherOnly });
  const results = h("div", { class: "job-results" });
  const pager = h("div", { class: "pager" });

  // Buttons that open the same search on LinkedIn, Naukri, etc. (their own sites, no scraping)
  const siteLinks = h("div", { class: "site-links" });
  const drawSiteLinks = () => {
    const search = { what: what.value.trim() || "java developer", where: where.value.trim(), fresher: fresherOnly.checked };
    setChildren(
      siteLinks,
      h("span", { class: "muted small" }, "Also search on:"),
      JOB_SITES.map((site) =>
        h("a", { class: `btn btn-site site-${site.id}`, href: site.url(search), target: "_blank", rel: "noopener noreferrer" }, site.name),
      ),
    );
  };
  [what, where].forEach((input) => input.addEventListener("input", drawSiteLinks));
  fresherOnly.addEventListener("change", drawSiteLinks);
  drawSiteLinks();

  const run = async (newPage = 1) => {
    page = newPage;
    saveLastSearch({ what: what.value, where: where.value, fresherOnly: fresherOnly.checked });
    results.replaceChildren(loading("Searching jobs"));
    pager.replaceChildren();
    try {
      const response = await api.searchJobs(what.value.trim() || "java developer fresher", where.value, page);
      if (!alive) {
        return;
      }
      const list = fresherOnly.checked ? response.results.filter((j) => j.fresherFriendly) : response.results;
      setChildren(
        results,
        h("p", { class: "muted small" }, `${response.total.toLocaleString("en-IN")} jobs found · page ${response.page} · best skill match first`),
        list.length
          ? list.map(jobCard)
          : h("p", { class: "muted" },
              fresherOnly.checked && response.results.length
                ? `${response.results.length} jobs on this page, but none look fresher-friendly. Untick "Fresher-friendly only" to see them.`
                : "No matching jobs. Try a shorter title (like \"java developer\") or another city."),
      );
      pager.replaceChildren(
        h("button", { class: "btn", type: "button", disabled: page <= 1, onClick: () => run(page - 1) }, "Previous"),
        h("button", { class: "btn", type: "button", disabled: response.results.length < 20, onClick: () => run(page + 1) }, "Next"),
      );
    } catch (e) {
      if (alive) {
        results.replaceChildren(errorBox((e as Error).message, () => run(page)));
      }
    }
  };

  // ---- "From your alerts" tab: jobs read from his LinkedIn / Naukri alert emails ----
  const alertList = h("div", { class: "job-results" });
  let alertsLoaded = false;
  const loadAlerts = async () => {
    alertsLoaded = true;
    alertList.replaceChildren(loading("Loading jobs from your alerts"));
    try {
      const [gmail, jobs] = await Promise.all([api.gmailStatus(), api.alertJobs()]);
      if (!alive) {
        return;
      }
      if (!gmail.connected) {
        setChildren(
          alertList,
          h("div", { class: "empty" },
            h("h2", null, "Connect Gmail to see your alert jobs"),
            h("p", { class: "muted" }, "Jobs from the LinkedIn and Naukri job alerts in your inbox show up here, scored against your skills."),
            h("a", { class: "btn btn-primary", href: "#/settings" }, "Go to settings")),
        );
        return;
      }
      const syncBtn = h("button", { class: "btn", type: "button" }, "Check Gmail now");
      syncBtn.addEventListener("click", async () => {
        syncBtn.setAttribute("disabled", "");
        syncBtn.textContent = "Checking…";
        try {
          await api.syncGmail();
          await loadAlerts();
        } catch (e) {
          toastError((e as Error).message);
          syncBtn.removeAttribute("disabled");
          syncBtn.textContent = "Check Gmail now";
        }
      });
      const shown = jobs;
      setChildren(
        alertList,
        h("div", { class: "toolbar" },
          h("p", { class: "muted small" }, `${jobs.length} jobs from your alerts in the last 14 days · best skill match first`),
          syncBtn),
        shown.length
          ? shown.map(jobCard)
          : h("div", { class: "empty" },
              h("h2", null, "No alert jobs yet"),
              h("p", { class: "muted" }, "Turn on job alerts on LinkedIn (Jobs → Set alert) and Naukri (Jobs → Job alerts) for \"Java Developer\". New alerts appear here after the next Gmail check.")),
      );
    } catch (e) {
      if (alive) {
        alertList.replaceChildren(errorBox((e as Error).message, loadAlerts));
      }
    }
  };

  const searchView = h("div", null);
  const alertsView = h("div", { hidden: true }, alertList);
  let tab: "search" | "alerts" = "search";
  const tabs = h("div", { class: "tabs", role: "tablist" });
  const drawTabs = () => {
    setChildren(
      tabs,
      [
        { id: "search" as const, label: "Search" },
        { id: "alerts" as const, label: "From your LinkedIn and Naukri alerts" },
      ].map((t) =>
        h("button", {
          class: "tab", type: "button", role: "tab", "aria-selected": String(tab === t.id),
          onClick: () => {
            tab = t.id;
            searchView.hidden = tab !== "search";
            alertsView.hidden = tab !== "alerts";
            drawTabs();
            if (tab === "alerts" && !alertsLoaded) {
              loadAlerts();
            }
          },
        }, t.label),
      ),
    );
  };
  drawTabs();

  root.replaceChildren(
    h(
      "div",
      { class: "page" },
      h("div", { class: "page-head" }, h("h1", null, "Find jobs")),
      tabs,
      alertsView,
      searchView,
    ),
  );

  setChildren(
    searchView,
      h("p", { class: "muted" }, "Live listings from Adzuna, scored against your skills. The buttons below open the same search on LinkedIn, Naukri, Indeed and Foundit. Apply there, then add the job to your board."),
      h(
        "form",
        {
          class: "search-form",
          onSubmit: (e: Event) => {
            e.preventDefault();
            run(1);
          },
        },
        what,
        where,
        h("label", { class: "check" }, fresherOnly, "Fresher-friendly only"),
        h("button", { class: "btn btn-primary", type: "submit" }, "Search"),
      ),
      siteLinks,
      results,
      pager,
  );
  run(1);

  const unsubscribe = onLive((event) => {
    if (event.type === "ALERT_JOBS" && alertsLoaded) {
      loadAlerts();
    }
  });

  return () => {
    alive = false;
    unsubscribe();
  };
}

/** Which board "source" a saved job gets. */
const SAVE_SOURCE: Record<string, Source> = { ADZUNA: "JOB_SEARCH", LINKEDIN: "LINKEDIN", NAUKRI: "NAUKRI" };
const SOURCE_NAME: Record<string, string> = { ADZUNA: "Adzuna", LINKEDIN: "LinkedIn alert", NAUKRI: "Naukri alert" };

function jobCard(job: JobResult): HTMLElement {
  const url = safeUrl(job.url);
  const salary = salaryRange(job.salaryMin, job.salaryMax);
  const saveBtn = h("button", { class: "btn", type: "button", disabled: job.saved }, job.saved ? "Saved" : "Save to tracker");
  saveBtn.addEventListener("click", async () => {
    saveBtn.setAttribute("disabled", "");
    try {
      await api.createApplication({
        company: job.company.slice(0, 150),
        jobRole: job.title.slice(0, 150),
        location: job.location.slice(0, 150) || null,
        source: SAVE_SOURCE[job.source] ?? "JOB_SEARCH",
        status: "SAVED",
        jobUrl: url,
        salary: salary,
      });
      saveBtn.textContent = "Saved";
      toast({ title: "Saved to your board", message: `${job.company} is in the Saved column`, level: "success" }, 3000);
    } catch (e) {
      saveBtn.removeAttribute("disabled");
      toastError((e as Error).message);
    }
  });

  const scoreClass = job.matchScore >= 50 ? "score score-high" : job.matchScore >= 25 ? "score score-mid" : "score";
  return h(
    "article",
    { class: "card job-result" },
    h(
      "div",
      { class: "job-result-head" },
      h("div", null, h("h2", null, job.title), h("p", { class: "muted" }, [job.company, job.location].filter(Boolean).join(" · "))),
      h("span", { class: scoreClass, title: "How many of your skills this job mentions" }, `${job.matchScore}% match`),
    ),
    h(
      "div",
      { class: "chips" },
      job.source !== "ADZUNA" ? h("span", { class: `pill site-${job.source.toLowerCase()}` }, SOURCE_NAME[job.source] ?? job.source) : null,
      job.fresherFriendly ? h("span", { class: "pill pill-success" }, "Fresher-friendly") : null,
      salary ? h("span", { class: "pill" }, salary) : null,
      job.postedAt ? h("span", { class: "pill" }, `${job.source === "ADZUNA" ? "Posted" : "Alert"} ${timeAgo(job.postedAt)}`) : null,
      job.matchedSkills.map((s) => h("span", { class: "pill pill-accent" }, s)),
    ),
    job.description ? h("p", { class: "job-desc" }, job.description) : null,
    h(
      "div",
      { class: "job-actions" },
      url ? h("a", { class: "btn btn-primary", href: url, target: "_blank", rel: "noopener noreferrer" }, "View and apply") : null,
      saveBtn,
    ),
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

interface LastSearch {
  what: string;
  where: string;
  fresherOnly: boolean;
}

function readLastSearch(): LastSearch {
  try {
    const parsed = JSON.parse(localStorage.getItem(SAVED_KEY) ?? "null");
    if (parsed && typeof parsed.what === "string") {
      return { what: parsed.what, where: String(parsed.where ?? ""), fresherOnly: Boolean(parsed.fresherOnly) };
    }
  } catch {
    // ignore
  }
  return { what: "java developer", where: "Hyderabad", fresherOnly: true };
}

function saveLastSearch(search: LastSearch): void {
  try {
    localStorage.setItem(SAVED_KEY, JSON.stringify(search));
  } catch {
    // ignore
  }
}
