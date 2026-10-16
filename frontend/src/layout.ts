import { api } from "./api";
import { clearToken } from "./auth";
import { h } from "./dom";
import { initials } from "./format";
import { onLive, onLiveState, type LiveState } from "./live";
import { toast } from "./toast";
import type { GmailStatus, Me, Notice } from "./types";

const NAV = [
  { path: "/dashboard", label: "Dashboard" },
  { path: "/board", label: "Board" },
  { path: "/jobs", label: "Find jobs" },
  { path: "/emails", label: "Emails" },
  { path: "/settings", label: "Settings" },
];

export interface Shell {
  content: HTMLElement;
  setActive(path: string): void;
  destroy(): void;
}

/** The signed-in frame: sidebar, top bar with live status, and the page area. */
export function createShell(root: HTMLElement): Shell {
  const links = new Map<string, HTMLAnchorElement>();
  const reviewBadge = h("span", { class: "nav-badge", hidden: true });

  const nav = h(
    "nav",
    { class: "nav", "aria-label": "Main" },
    NAV.map((item) => {
      const link = h("a", { href: `#${item.path}`, class: "nav-link" }, item.label, item.path === "/emails" ? reviewBadge : null);
      links.set(item.path, link);
      return link;
    }),
  );

  const liveBadge = h("span", { class: "live-badge" });
  const avatar = h("div", { class: "avatar", "aria-hidden": "true" });
  const userName = h("span", { class: "user-name" });
  const signOut = h(
    "button",
    {
      class: "btn btn-ghost",
      type: "button",
      onClick: () => {
        clearToken();
        window.location.hash = "#/login";
      },
    },
    "Sign out",
  );

  const content = h("main", { class: "content", id: "main", tabindex: "-1" });
  const frame = h(
    "div",
    { class: "shell" },
    h(
      "aside",
      { class: "sidebar" },
      h("a", { class: "brand", href: "#/dashboard" }, h("span", { class: "brand-mark", "aria-hidden": "true" }), "Job tracker"),
      nav,
    ),
    h(
      "div",
      { class: "main-col" },
      h("header", { class: "topbar" }, liveBadge, h("div", { class: "topbar-user" }, avatar, userName, signOut)),
      content,
    ),
  );
  root.replaceChildren(frame);

  const showLiveState = (state: LiveState) => {
    liveBadge.className = `live-badge live-${state}`;
    liveBadge.textContent = state === "live" ? "Live" : state === "connecting" ? "Connecting" : "Offline";
    liveBadge.title =
      state === "live" ? "Changes from other devices and Gmail appear instantly" : "Reconnecting to live updates";
  };

  const showReviewCount = (gmail: GmailStatus) => {
    reviewBadge.hidden = gmail.needsReview === 0;
    reviewBadge.textContent = String(gmail.needsReview);
    reviewBadge.setAttribute("aria-label", `${gmail.needsReview} emails need review`);
  };

  const showUser = (me: Me) => {
    avatar.textContent = initials(me.name, me.email);
    userName.textContent = me.name || me.email;
    showReviewCount(me.gmail);
  };

  api.me().then(showUser).catch(() => undefined);

  const unsubscribeState = onLiveState(showLiveState);
  const unsubscribeLive = onLive((event) => {
    if (event.type === "NOTIFICATION") {
      toast(event.payload as Notice);
    } else if (event.type === "GMAIL_STATUS" || event.type === "EMAILS_CHANGED") {
      showReviewCount(event.payload as GmailStatus);
    }
  });

  return {
    content,
    setActive(path: string) {
      links.forEach((link, p) => {
        if (p === path) {
          link.setAttribute("aria-current", "page");
        } else {
          link.removeAttribute("aria-current");
        }
      });
      content.focus({ preventScroll: true });
    },
    destroy() {
      unsubscribeState();
      unsubscribeLive();
    },
  };
}
