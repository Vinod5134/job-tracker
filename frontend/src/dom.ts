/**
 * Tiny DOM helper instead of a framework.
 * Text is always inserted as text nodes, never as HTML, so data from emails or
 * job ads can't inject markup (XSS-safe by construction).
 */

export type Child = Node | string | number | null | undefined | false | Child[];
type Props = Record<string, unknown>;

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Props | null = null,
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value === undefined || value === null || value === false) {
        continue;
      }
      if (key.startsWith("on") && typeof value === "function") {
        el.addEventListener(key.slice(2).toLowerCase(), value as EventListener);
      } else if (key === "class") {
        el.className = String(value);
      } else if (key === "dataset" && typeof value === "object") {
        Object.assign(el.dataset, value);
      } else {
        el.setAttribute(key, value === true ? "" : String(value));
      }
    }
  }
  append(el, children);
  return el;
}

export function append(parent: Node, children: Child[]): void {
  for (const child of children) {
    if (child === null || child === undefined || child === false) {
      continue;
    }
    if (Array.isArray(child)) {
      append(parent, child);
    } else if (child instanceof Node) {
      parent.appendChild(child);
    } else {
      parent.appendChild(document.createTextNode(String(child)));
    }
  }
}

/** Like el.replaceChildren(), but accepts the same flexible children as h(). */
export function setChildren(el: Element, ...children: Child[]): void {
  el.replaceChildren();
  append(el, children);
}

/** Only allow http(s) links, so nothing like "javascript:" can become a link. */
export function safeUrl(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : null;
  } catch {
    return null;
  }
}

export function loading(text = "Loading"): HTMLElement {
  return h("div", { class: "loading", role: "status" }, h("span", { class: "spinner", "aria-hidden": "true" }), text);
}

export function errorBox(message: string, onRetry?: () => void): HTMLElement {
  return h(
    "div",
    { class: "error-box", role: "alert" },
    h("p", null, message),
    onRetry ? h("button", { class: "btn", type: "button", onClick: onRetry }, "Try again") : null,
  );
}
