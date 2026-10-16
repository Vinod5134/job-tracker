import "./style.css";
import { isLoggedIn, setToken } from "./auth";
import { createShell, type Shell } from "./layout";
import { startLive, stopLive } from "./live";
import { renderBoard } from "./pages/board";
import { renderDashboard } from "./pages/dashboard";
import { renderEmails } from "./pages/emails";
import { renderJobs } from "./pages/jobs";
import { renderLogin } from "./pages/login";
import { renderSettings } from "./pages/settings";

/** A page draws into the content area and returns a cleanup function. */
type Page = (root: HTMLElement, params: URLSearchParams) => () => void;

const pages: Record<string, Page> = {
  "/dashboard": renderDashboard,
  "/board": renderBoard,
  "/jobs": renderJobs,
  "/emails": renderEmails,
  "/settings": renderSettings,
};

const app = document.getElementById("app") as HTMLElement;
let cleanup: (() => void) | null = null;
let shell: Shell | null = null;

function parseHash(): { path: string; params: URLSearchParams } {
  const raw = window.location.hash.replace(/^#/, "");
  const [path, query] = raw.split("?");
  return { path: path || "/dashboard", params: new URLSearchParams(query ?? "") };
}

function route(): void {
  cleanup?.();
  cleanup = null;
  const { path, params } = parseHash();

  // Google sign-in lands here with the JWT; store it and drop it from the URL and history
  if (path === "/auth") {
    const token = params.get("token");
    if (token) {
      setToken(token);
    }
    history.replaceState(null, "", `${window.location.pathname}#/dashboard`);
    route();
    return;
  }

  if (path === "/login" || !isLoggedIn()) {
    if (path !== "/login") {
      window.location.hash = "#/login";
      return;
    }
    shell?.destroy();
    shell = null;
    void stopLive();
    cleanup = renderLogin(app, params);
    return;
  }

  if (!shell) {
    shell = createShell(app);
    startLive();
  }
  const page = pages[path] ?? pages["/dashboard"];
  shell.setActive(pages[path] ? path : "/dashboard");
  cleanup = page(shell.content, params);
}

window.addEventListener("hashchange", route);
route();
