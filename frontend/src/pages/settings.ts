import { api } from "../api";
import { GMAIL_CONNECT_URL } from "../config";
import { errorBox, h, loading, setChildren } from "../dom";
import { timeAgo } from "../format";
import { onLive } from "../live";
import { toast, toastError } from "../toast";
import type { GmailStatus, Me } from "../types";

export function renderSettings(root: HTMLElement, params: URLSearchParams): () => void {
  let alive = true;
  const gmailCard = h("section", { class: "card" });

  const gmailResult = params.get("gmail");
  if (gmailResult) {
    history.replaceState(null, "", "#/settings");
    if (gmailResult === "connected") {
      toast({ title: "Gmail connected", message: "Checking your inbox for job emails now.", level: "success" });
      api.syncGmail().catch((e) => toastError((e as Error).message));
    } else {
      toast({ title: "Gmail wasn't connected", message: "Google didn't grant access. Try again and allow Gmail access.", level: "error" });
    }
  }

  const drawGmail = (status: GmailStatus, me: Me) => {
    if (!status.connected) {
      setChildren(
        gmailCard,
        h("h2", null, "Gmail"),
        h("p", { class: "muted" }, "Connect Gmail so interview invites, rejections and offers update your board automatically. Access is read-only, and only job-related emails are checked."),
        status.lastError ? h("p", { class: "form-error" }, status.lastError) : null,
        h("a", { class: "btn btn-primary", href: GMAIL_CONNECT_URL }, "Connect Gmail"),
        h("p", { class: "fine-print" }, `Sign in with the same Google account (${me.email}) so updates land on this board.`),
      );
      return;
    }
    const syncBtn = h("button", { class: "btn btn-primary", type: "button" }, "Sync now");
    syncBtn.addEventListener("click", async () => {
      syncBtn.setAttribute("disabled", "");
      syncBtn.textContent = "Syncing…";
      try {
        const result = await api.syncGmail();
        toast({
          title: result.skipped ? "A sync is already running" : "Gmail synced",
          message: result.skipped ? "Results will appear in a moment." : `${result.detected} job emails found, ${result.autoUpdated} board updates, ${result.needsReview} to review`,
          level: "success",
        });
      } catch (e) {
        toastError((e as Error).message);
      } finally {
        syncBtn.removeAttribute("disabled");
        syncBtn.textContent = "Sync now";
      }
    });
    const disconnectBtn = h("button", { class: "btn btn-danger-ghost", type: "button" }, "Disconnect");
    disconnectBtn.addEventListener("click", async () => {
      if (!window.confirm("Disconnect Gmail? Detected email history will be removed. Your board stays as it is.")) {
        return;
      }
      try {
        await api.disconnectGmail();
      } catch (e) {
        toastError((e as Error).message);
      }
    });
    setChildren(
      gmailCard,
      h("h2", null, "Gmail"),
      h("p", null, h("span", { class: "pill pill-success" }, "Connected"), " ", status.email ?? ""),
      h("p", { class: "muted small" }, status.lastSyncedAt ? `Last checked ${timeAgo(status.lastSyncedAt)}. Checks again every 10 minutes while the server is awake.` : "Not checked yet."),
      status.lastError ? h("p", { class: "form-error" }, status.lastError) : null,
      status.needsReview ? h("p", null, h("a", { href: "#/emails" }, `${status.needsReview} email(s) need review`)) : null,
      h("div", { class: "job-actions" }, syncBtn, disconnectBtn),
    );
  };

  const skillsCard = (me: Me) => {
    const input = h("textarea", { rows: "4", "aria-label": "Skills, separated by commas" }, me.skills.join(", "));
    const error = h("p", { class: "field-error" });
    const save = h("button", { class: "btn btn-primary", type: "button" }, "Save skills");
    input.addEventListener("input", () => (error.textContent = ""));
    save.addEventListener("click", async () => {
      const skills = input.value.split(",").map((s) => s.trim()).filter(Boolean);
      if (!skills.length) {
        error.textContent = "Add at least one skill";
        return;
      }
      if (skills.length > 40) {
        error.textContent = "Keep it to 40 skills or fewer";
        return;
      }
      try {
        await api.updateSkills(skills);
        toast({ title: "Skills saved", message: "Job search scores now use these skills.", level: "success" }, 3000);
      } catch (e) {
        error.textContent = (e as Error).message;
      }
    });
    return h(
      "section",
      { class: "card" },
      h("h2", null, "Skills"),
      h("p", { class: "muted" }, "Used to score jobs in Find jobs. Separate skills with commas."),
      input,
      error,
      save,
    );
  };

  let me: Me | null = null;
  const load = async () => {
    root.replaceChildren(loading());
    try {
      me = await api.me();
      if (!alive) {
        return;
      }
      drawGmail(me.gmail, me);
      root.replaceChildren(
        h(
          "div",
          { class: "page" },
          h("div", { class: "page-head" }, h("h1", null, "Settings")),
          h("section", { class: "card" }, h("h2", null, "Account"), h("p", null, h("strong", null, me.name ?? ""), " · ", me.email)),
          gmailCard,
          skillsCard(me),
        ),
      );
    } catch (e) {
      if (alive) {
        root.replaceChildren(errorBox((e as Error).message, load));
      }
    }
  };
  load();

  const unsubscribe = onLive((event) => {
    if ((event.type === "GMAIL_STATUS" || event.type === "EMAILS_CHANGED") && me) {
      drawGmail(event.payload as GmailStatus, me);
    }
  });

  return () => {
    alive = false;
    unsubscribe();
  };
}
