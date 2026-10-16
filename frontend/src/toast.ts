import { h } from "./dom";
import type { Notice } from "./types";

/** Small pop-up messages in the corner, used for live notifications and errors. */
export function toast(notice: Notice, durationMs = 6000): void {
  const container = document.getElementById("toasts");
  if (!container) {
    return;
  }
  const close = () => {
    el.classList.add("leaving");
    window.setTimeout(() => el.remove(), 200);
  };
  const el = h(
    "div",
    { class: `toast toast-${notice.level}`, role: notice.level === "error" ? "alert" : "status" },
    h("div", { class: "toast-body" }, h("strong", null, notice.title), notice.message ? h("p", null, notice.message) : null),
    h("button", { class: "icon-btn", type: "button", "aria-label": "Dismiss", onClick: close }, "×"),
  );
  container.appendChild(el);
  // Keep at most 4 on screen
  while (container.children.length > 4) {
    container.firstElementChild?.remove();
  }
  window.setTimeout(close, durationMs);
}

export function toastError(message: string): void {
  toast({ title: "Something went wrong", message, level: "error" });
}
