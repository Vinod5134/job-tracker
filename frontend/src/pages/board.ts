import { api } from "../api";
import { errorBox, h, loading, safeUrl } from "../dom";
import { formatDateTime, timeAgo } from "../format";
import { onLive } from "../live";
import { toastError } from "../toast";
import { SOURCE_LABEL, STATUSES, STATUS_LABEL, type Application, type Status } from "../types";
import { openApplicationForm } from "./applicationForm";

/**
 * Kanban board. Drag a card to another column to change its status.
 * Changes from other devices or from Gmail arrive as live events and update the board in place.
 */
export function renderBoard(root: HTMLElement, params: URLSearchParams): () => void {
  let alive = true;
  let apps: Application[] = [];
  let query = "";
  const flashIds = new Set<number>();

  const columnsEl = h("div", { class: "board" });
  const search = h("input", {
    type: "search",
    placeholder: "Search company, role or city",
    "aria-label": "Search applications",
    onInput: (e: Event) => {
      query = (e.target as HTMLInputElement).value.trim().toLowerCase();
      draw();
    },
  });

  const matches = (a: Application) =>
    !query || [a.company, a.jobRole, a.location].some((v) => v?.toLowerCase().includes(query));

  const emptyEl = h(
    "div",
    { class: "empty" },
    h("h2", null, "Start tracking your applications"),
    h("p", { class: "muted" }, "Add the jobs you've applied to, or connect Gmail in Settings to pick them up from your inbox."),
  );

  const draw = () => {
    emptyEl.hidden = apps.length > 0;
    columnsEl.replaceChildren(...STATUSES.map((status) => column(status, apps.filter((a) => a.status === status && matches(a)))));
    flashIds.clear();
  };

  const column = (status: Status, items: Application[]) => {
    const col = h(
      "section",
      { class: `column column-${status.toLowerCase()}`, "aria-label": STATUS_LABEL[status] },
      h("header", { class: "column-head" }, h("span", null, STATUS_LABEL[status]), h("span", { class: "count" }, String(items.length))),
      h("div", { class: "column-cards" }, items.length ? items.map(card) : h("p", { class: "column-empty" }, "Drop cards here")),
    );
    col.addEventListener("dragover", (e) => {
      e.preventDefault();
      col.classList.add("drop-target");
    });
    col.addEventListener("dragleave", (e) => {
      if (!col.contains(e.relatedTarget as Node)) {
        col.classList.remove("drop-target");
      }
    });
    col.addEventListener("drop", (e) => {
      e.preventDefault();
      col.classList.remove("drop-target");
      const id = Number(e.dataTransfer?.getData("text/plain"));
      if (id) {
        move(id, status);
      }
    });
    return col;
  };

  const card = (a: Application) => {
    const url = safeUrl(a.jobUrl);
    const el = h(
      "article",
      { class: flashIds.has(a.id) ? "job-card flash" : "job-card", draggable: "true", dataset: { id: String(a.id) } },
      h("button", { class: "job-card-main", type: "button", onClick: () => openApplicationForm(a), "aria-label": `Edit ${a.company}` },
        h("strong", { class: "job-company" }, a.company),
        a.jobRole ? h("span", { class: "job-role" }, a.jobRole) : null,
        h("span", { class: "job-meta" }, [SOURCE_LABEL[a.source], a.location].filter(Boolean).join(" · ")),
      ),
      h(
        "div",
        { class: "job-card-foot" },
        a.status === "INTERVIEW" && a.interviewAt ? h("span", { class: "pill pill-accent" }, formatDateTime(a.interviewAt)) : null,
        a.status === "OFFER" && a.salary ? h("span", { class: "pill pill-success" }, a.salary) : null,
        h("span", { class: "muted small" }, timeAgo(a.updatedAt)),
        url ? h("a", { class: "job-link", href: url, target: "_blank", rel: "noopener noreferrer" }, "Open job") : null,
      ),
    );
    el.addEventListener("dragstart", (e) => {
      e.dataTransfer?.setData("text/plain", String(a.id));
      el.classList.add("dragging");
    });
    el.addEventListener("dragend", () => el.classList.remove("dragging"));
    return el;
  };

  const move = async (id: number, status: Status) => {
    const app = apps.find((a) => a.id === id);
    if (!app || app.status === status) {
      return;
    }
    const previous = app.status;
    app.status = status; // optimistic: move it right away
    draw();
    try {
      await api.changeStatus(id, status);
    } catch (e) {
      app.status = previous;
      draw();
      toastError((e as Error).message);
    }
  };

  const upsert = (incoming: Application) => {
    const index = apps.findIndex((a) => a.id === incoming.id);
    if (index >= 0) {
      apps[index] = incoming;
    } else {
      apps.unshift(incoming);
    }
    flashIds.add(incoming.id);
    draw();
  };

  const load = async () => {
    try {
      apps = await api.applications();
      if (!alive) {
        return;
      }
      root.replaceChildren(
        h(
          "div",
          { class: "page page-wide" },
          h("div", { class: "page-head" }, h("h1", null, "Board"), h("button", { class: "btn btn-primary", type: "button", onClick: () => openApplicationForm() }, "Add application")),
          h("div", { class: "toolbar" }, search, h("span", { class: "muted small hide-mobile" }, "Drag cards between columns to update their status")),
          emptyEl,
          columnsEl,
        ),
      );
      draw();
    } catch (e) {
      if (alive) {
        root.replaceChildren(errorBox((e as Error).message, load));
      }
    }
  };

  root.replaceChildren(loading());
  load().then(() => {
    if (alive && params.get("add") === "1") {
      history.replaceState(null, "", "#/board");
      openApplicationForm();
    }
  });

  const unsubscribe = onLive((event) => {
    if (event.type === "APPLICATION_CREATED" || event.type === "APPLICATION_UPDATED") {
      upsert(event.payload as Application);
    } else if (event.type === "APPLICATION_DELETED") {
      const { id } = event.payload as { id: number };
      apps = apps.filter((a) => a.id !== id);
      draw();
    }
  });

  return () => {
    alive = false;
    unsubscribe();
  };
}
