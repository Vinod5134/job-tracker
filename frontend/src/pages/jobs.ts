import { api } from "../api";
import { errorBox, h, loading, safeUrl, setChildren } from "../dom";
import { salaryRange, timeAgo } from "../format";
import { toast, toastError } from "../toast";
import type { JobResult } from "../types";

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
        list.length ? list.map(jobCard) : h("p", { class: "muted" }, "No matching jobs on this page. Try a broader title or another city."),
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

  root.replaceChildren(
    h(
      "div",
      { class: "page" },
      h("div", { class: "page-head" }, h("h1", null, "Find jobs")),
      h("p", { class: "muted" }, "Live listings from Adzuna, scored against the skills in Settings. Apply on the job's site, then save it here to track it."),
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
      results,
      pager,
    ),
  );
  run(1);

  return () => {
    alive = false;
  };
}

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
        source: "JOB_SEARCH",
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
      job.fresherFriendly ? h("span", { class: "pill pill-success" }, "Fresher-friendly") : null,
      salary ? h("span", { class: "pill" }, salary) : null,
      job.postedAt ? h("span", { class: "pill" }, `Posted ${timeAgo(job.postedAt)}`) : null,
      job.matchedSkills.map((s) => h("span", { class: "pill pill-accent" }, s)),
    ),
    h("p", { class: "job-desc" }, job.description),
    h(
      "div",
      { class: "job-actions" },
      url ? h("a", { class: "btn btn-primary", href: url, target: "_blank", rel: "noopener noreferrer" }, "View and apply") : null,
      saveBtn,
    ),
  );
}

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
  return { what: "java developer fresher", where: "Hyderabad", fresherOnly: false };
}

function saveLastSearch(search: LastSearch): void {
  try {
    localStorage.setItem(SAVED_KEY, JSON.stringify(search));
  } catch {
    // ignore
  }
}
