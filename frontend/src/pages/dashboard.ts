import { api } from "../api";
import { errorBox, h, loading } from "../dom";
import { formatDateTime, formatWeek, timeAgo } from "../format";
import { onLive } from "../live";
import type { Activity, Application, Dashboard } from "../types";

export function renderDashboard(root: HTMLElement): () => void {
  let alive = true;
  let refreshTimer: number | undefined;
  let activityList: HTMLUListElement | null = null;

  const load = async () => {
    try {
      const data = await api.dashboard();
      if (alive) {
        root.replaceChildren(view(data));
      }
    } catch (e) {
      if (alive) {
        root.replaceChildren(errorBox((e as Error).message, load));
      }
    }
  };

  // Several events can arrive together (e.g. after a Gmail sync), so refresh once
  const scheduleRefresh = () => {
    window.clearTimeout(refreshTimer);
    refreshTimer = window.setTimeout(load, 400);
  };

  const view = (d: Dashboard): HTMLElement => {
    const applied = d.total - d.counts.SAVED;
    const maxWeek = Math.max(1, ...d.weekly.map((w) => w.count));
    activityList = h("ul", { class: "activity-list" }, d.recentActivity.map(activityItem));

    return h(
      "div",
      { class: "page" },
      h("div", { class: "page-head" }, h("h1", null, "Dashboard"), h("a", { class: "btn btn-primary", href: "#/board?add=1" }, "Add application")),
      h(
        "div",
        { class: "metrics" },
        metric("Applied", applied),
        metric("Interviews", d.counts.INTERVIEW),
        metric("Offers", d.counts.OFFER),
        metric("Response rate", `${d.responseRate}%`),
      ),
      h(
        "div",
        { class: "grid-2" },
        h(
          "section",
          { class: "card" },
          h("h2", null, "Applications per week"),
          h(
            "div",
            { class: "bars", role: "img", "aria-label": d.weekly.map((w) => `${formatWeek(w.weekStart)}: ${w.count}`).join(", ") },
            d.weekly.map((w) =>
              h(
                "div",
                { class: "bar-col" },
                h("span", { class: "bar-value" }, w.count ? String(w.count) : ""),
                h("div", { class: "bar", style: `height:${Math.round((w.count / maxWeek) * 100)}%` }),
                h("span", { class: "bar-label" }, formatWeek(w.weekStart)),
              ),
            ),
          ),
        ),
        h(
          "section",
          { class: "card" },
          h("h2", null, "Upcoming interviews"),
          d.upcomingInterviews.length
            ? h("ul", { class: "simple-list" }, d.upcomingInterviews.map(interviewItem))
            : h("p", { class: "muted" }, "No interviews scheduled. Set an interview date on a card to see it here."),
          d.followUps.length ? h("h2", { class: "mt" }, "Time to follow up") : null,
          d.followUps.length
            ? h(
                "ul",
                { class: "simple-list" },
                d.followUps.map((a) => h("li", null, h("strong", null, a.company), h("span", { class: "muted" }, ` · applied ${timeAgo(a.statusChangedAt)}`))),
              )
            : null,
        ),
      ),
      h(
        "section",
        { class: "card" },
        h("h2", null, "Live activity"),
        d.recentActivity.length ? null : h("p", { class: "muted", id: "activity-empty" }, "Activity shows up here as you add and update applications."),
        activityList,
      ),
    );
  };

  root.replaceChildren(loading());
  load();

  const unsubscribe = onLive((event) => {
    if (event.type === "ACTIVITY" && activityList) {
      root.querySelector("#activity-empty")?.remove();
      const item = activityItem(event.payload as Activity);
      item.classList.add("flash");
      activityList.prepend(item);
      while (activityList.children.length > 20) {
        activityList.lastElementChild?.remove();
      }
    }
    if (event.type.startsWith("APPLICATION_")) {
      scheduleRefresh();
    }
  });

  return () => {
    alive = false;
    window.clearTimeout(refreshTimer);
    unsubscribe();
  };
}

function metric(label: string, value: number | string): HTMLElement {
  return h("div", { class: "metric" }, h("span", { class: "metric-label" }, label), h("span", { class: "metric-value" }, String(value)));
}

function interviewItem(a: Application): HTMLElement {
  return h(
    "li",
    null,
    h("strong", null, a.company),
    a.jobRole ? h("span", { class: "muted" }, ` · ${a.jobRole}`) : null,
    h("div", { class: "muted small" }, formatDateTime(a.interviewAt)),
  );
}

function activityItem(a: Activity): HTMLLIElement {
  const originLabel = a.origin === "EMAIL" ? "Gmail" : a.origin === "SYSTEM" ? "Reminder" : null;
  return h(
    "li",
    { class: `activity activity-${a.origin.toLowerCase()}` },
    h("span", { class: "activity-dot", "aria-hidden": "true" }),
    h("span", { class: "activity-text" }, a.message),
    originLabel ? h("span", { class: "pill" }, originLabel) : null,
    h("time", { class: "muted small", datetime: a.createdAt }, timeAgo(a.createdAt)),
  );
}
