const dateFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });
const dateTimeFmt = new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});
const weekFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });

export function formatDate(value: string | null | undefined): string {
  return value ? dateFmt.format(new Date(value)) : "";
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTimeFmt.format(new Date(value)) : "";
}

export function formatWeek(value: string): string {
  return weekFmt.format(new Date(`${value}T00:00:00`));
}

export function timeAgo(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const seconds = Math.round((Date.now() - new Date(value).getTime()) / 1000);
  if (seconds < 45) {
    return "just now";
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours}h ago`;
  }
  const days = Math.round(hours / 24);
  return days < 30 ? `${days}d ago` : formatDate(value);
}

export function initials(name: string | null | undefined, email: string): string {
  const source = (name || email).trim();
  const parts = source.split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : source.slice(0, 2);
  return letters.toUpperCase();
}

/** "2026-10-16T04:30:00Z" -> "2026-10-16T10:00" for <input type="datetime-local"> (local time). */
export function toLocalInput(value: string | null): string {
  if (!value) {
    return "";
  }
  const d = new Date(value);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string): string | null {
  return value ? new Date(value).toISOString() : null;
}

export function salaryRange(min: number | null, max: number | null): string | null {
  if (!min && !max) {
    return null;
  }
  const lpa = (n: number) => `${(n / 100000).toFixed(1)} LPA`;
  if (min && max && Math.round(min) !== Math.round(max)) {
    return `${lpa(min)} – ${lpa(max)}`;
  }
  return lpa((min || max) as number);
}
