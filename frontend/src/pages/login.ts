import { API_URL, LOGIN_URL } from "../config";
import { h } from "../dom";

export function renderLogin(root: HTMLElement, params: URLSearchParams): () => void {
  const error = params.get("error");
  // Start waking the free server now, so sign-in is quick when the button is clicked
  fetch(`${API_URL}/actuator/health`, { mode: "no-cors" }).catch(() => undefined);
  root.replaceChildren(
    h(
      "div",
      { class: "login-page" },
      h(
        "div",
        { class: "login-card" },
        h("div", { class: "login-mark", "aria-hidden": "true" }),
        h("h1", null, "Job tracker"),
        h("p", { class: "muted" }, "Track every application in one place, with updates from Gmail as they arrive."),
        error ? h("p", { class: "form-error", role: "alert" }, "Google sign-in didn't complete. Try again.") : null,
        h(
          "a",
          { class: "btn btn-google", href: LOGIN_URL },
          h("span", { class: "google-g", "aria-hidden": "true" }, "G"),
          "Continue with Google",
        ),
        h("p", { class: "fine-print" }, "Secured with Google OAuth2 and JWT. The first load can take up to a minute while the free server wakes up."),
      ),
    ),
  );
  return () => undefined;
}
