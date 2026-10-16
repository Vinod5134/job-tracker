import { api } from "../api";
import { errorBox, h, loading } from "../dom";
import { timeAgo } from "../format";
import { onLive } from "../live";
import { toastError } from "../toast";
import { STATUSES, STATUS_LABEL, type EmailRecord, type Status } from "../types";

const STATE_LABEL: Record<EmailRecord["state"], string> = {
  AUTO_APPLIED: "Board updated",
  NO_CHANGE: "Already up to date",
  NEEDS_REVIEW: "Needs review",
  ACCEPTED: "Confirmed",
  DISMISSED: "Dismissed",
};

/**
 * Job emails found in Gmail. Ones the classifier wasn't sure about wait here for a
 * one-click confirm (with the company/status editable) instead of silently changing the board.
 */
export function renderEmails(root: HTMLElement, params: URLSearchParams): () => void {
  let alive = true;
  let view: "review" | "all" = params.get("view") === "all" ? "all" : "review";
  const list = h("div", { class: "email-list" });

  const tabs = h(
    "div",
    { class: "tabs", role: "tablist" },
    tab("review", "Needs review"),
    tab("all", "All detected"),
  );

  function tab(value: "review" | "all", label: string) {
    return h(
      "button",
      {
        class: "tab",
        type: "button",
        role: "tab",
        "aria-selected": String(view === value),
        onClick: () => {
          view = value;
          tabs.querySelectorAll(".tab").forEach((t, i) => t.setAttribute("aria-selected", String((i === 0) === (view === "review"))));
          history.replaceState(null, "", value === "all" ? "#/emails?view=all" : "#/emails");
          load();
        },
      },
      label,
    );
  }

  const load = async () => {
    list.replaceChildren(loading());
    try {
      const [status, emails] = await Promise.all([api.gmailStatus(), api.emails(view)]);
      if (!alive) {
        return;
      }
      if (!status.connected) {
        list.replaceChildren(
          h("div", { class: "empty" }, h("h2", null, "Connect Gmail to detect updates"), h("p", { class: "muted" }, "Interview invites, rejections and offers are picked up from your inbox automatically."), h("a", { class: "btn btn-primary", href: "#/settings" }, "Go to settings")),
        );
        return;
      }
      if (!emails.length) {
        list.replaceChildren(
          h("p", { class: "muted" }, view === "review" ? "Nothing to review. Emails the app is unsure about will show up here." : "No job emails detected yet. Try Sync now in Settings."),
        );
        return;
      }
      list.replaceChildren(...emails.map((e) => (view === "review" ? reviewCard(e) : logRow(e))));
    } catch (e) {
      if (alive) {
        list.replaceChildren(errorBox((e as Error).message, load));
      }
    }
  };

  const reviewCard = (email: EmailRecord) => {
    const company = h("input", { value: email.detectedCompany ?? "", placeholder: "Company name", "aria-label": "Company" });
    const role = h("input", { value: email.detectedRole ?? "", placeholder: "Role (optional)", "aria-label": "Role" });
    const status = h("select", { "aria-label": "Status" }, STATUSES.map((s) => h("option", { value: s, selected: s === email.detectedStatus }, STATUS_LABEL[s])));
    const error = h("p", { class: "field-error" });
    company.addEventListener("input", () => (error.textContent = ""));

    const card = h(
      "article",
      { class: "card email-card" },
      h("p", { class: "email-subject" }, email.subject || "(no subject)"),
      h("p", { class: "muted small" }, `${email.from ?? ""} · ${timeAgo(email.receivedAt)}`),
      h("div", { class: "email-fields" }, company, status, role),
      error,
      h(
        "div",
        { class: "job-actions" },
        h(
          "button",
          {
            class: "btn btn-primary",
            type: "button",
            onClick: async () => {
              if (!company.value.trim()) {
                error.textContent = "Enter the company name first";
                company.focus();
                return;
              }
              try {
                await api.acceptEmail(email.id, { company: company.value.trim(), status: status.value as Status, role: role.value.trim() || null });
                card.remove();
              } catch (e) {
                error.textContent = (e as Error).message;
              }
            },
          },
          "Confirm and update board",
        ),
        h(
          "button",
          {
            class: "btn btn-ghost",
            type: "button",
            onClick: async () => {
              try {
                await api.dismissEmail(email.id);
                card.remove();
              } catch (e) {
                toastError((e as Error).message);
              }
            },
          },
          "Not a job update",
        ),
      ),
    );
    return card;
  };

  const logRow = (email: EmailRecord) =>
    h(
      "div",
      { class: "email-row" },
      h("div", null, h("p", { class: "email-subject" }, email.subject || "(no subject)"), h("p", { class: "muted small" }, `${email.from ?? ""} · ${timeAgo(email.receivedAt)}`)),
      h(
        "div",
        { class: "chips" },
        email.detectedCompany ? h("span", { class: "pill" }, email.detectedCompany) : null,
        email.detectedStatus ? h("span", { class: "pill pill-accent" }, STATUS_LABEL[email.detectedStatus]) : null,
        h("span", { class: `pill state-${email.state.toLowerCase()}` }, STATE_LABEL[email.state]),
      ),
    );

  root.replaceChildren(
    h(
      "div",
      { class: "page" },
      h("div", { class: "page-head" }, h("h1", null, "Emails")),
      h("p", { class: "muted" }, "Only the sender, subject and what was detected are saved, never the email body."),
      tabs,
      list,
    ),
  );
  load();

  const unsubscribe = onLive((event) => {
    if (event.type === "EMAILS_CHANGED") {
      load();
    }
  });

  return () => {
    alive = false;
    unsubscribe();
  };
}
