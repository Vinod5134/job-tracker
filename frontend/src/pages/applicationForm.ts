import { ApiError, api } from "../api";
import { h } from "../dom";
import { fromLocalInput, toLocalInput } from "../format";
import { toast } from "../toast";
import { SOURCE_LABEL, STATUSES, STATUS_LABEL, type Application, type ApplicationInput, type Source } from "../types";

const SOURCES = Object.keys(SOURCE_LABEL) as Source[];

/**
 * Add / edit form in a native <dialog>. Saving goes to the API; the board and dashboard
 * update from the live event the backend pushes back, so every open device stays in sync.
 */
export function openApplicationForm(existing?: Application): void {
  const isEdit = Boolean(existing);
  const today = new Date().toISOString().slice(0, 10);

  const field = (label: string, input: HTMLElement, name: string, wide = false) =>
    h("label", { class: wide ? "field field-wide" : "field" }, h("span", null, label), input, h("span", { class: "field-error", "data-error": name }));

  const company = h("input", { name: "company", required: true, maxlength: "150", value: existing?.company ?? "", placeholder: "Infosys", autocomplete: "off" });
  const role = h("input", { name: "jobRole", maxlength: "150", value: existing?.jobRole ?? "", placeholder: "Systems Engineer" });
  const location = h("input", { name: "location", maxlength: "150", value: existing?.location ?? "", placeholder: "Hyderabad" });
  const source = h("select", { name: "source" }, SOURCES.map((s) => h("option", { value: s, selected: (existing?.source ?? "NAUKRI") === s }, SOURCE_LABEL[s])));
  const status = h("select", { name: "status" }, STATUSES.map((s) => h("option", { value: s, selected: (existing?.status ?? "APPLIED") === s }, STATUS_LABEL[s])));
  const appliedOn = h("input", { name: "appliedOn", type: "date", value: existing?.appliedOn ?? (isEdit ? "" : today) });
  const interviewAt = h("input", { name: "interviewAt", type: "datetime-local", value: toLocalInput(existing?.interviewAt ?? null) });
  const jobUrl = h("input", { name: "jobUrl", type: "url", maxlength: "1000", value: existing?.jobUrl ?? "", placeholder: "https://www.naukri.com/job-listings-..." });
  const salary = h("input", { name: "salary", maxlength: "50", value: existing?.salary ?? "", placeholder: "3.6 LPA" });
  const notes = h("textarea", { name: "notes", maxlength: "2000", rows: "3", placeholder: "Referral from a college senior" }, existing?.notes ?? "");

  const formError = h("p", { class: "form-error", role: "alert", hidden: true });
  const saveBtn = h("button", { class: "btn btn-primary", type: "submit" }, isEdit ? "Save changes" : "Save");

  const dialog = h("dialog", { class: "modal", "aria-labelledby": "form-title" });
  const close = () => dialog.close();

  const deleteBtn = existing
    ? h(
        "button",
        {
          class: "btn btn-danger-ghost",
          type: "button",
          onClick: async () => {
            if (!window.confirm(`Remove ${existing.company} from your tracker?`)) {
              return;
            }
            try {
              await api.deleteApplication(existing.id);
              close();
            } catch (e) {
              showError((e as Error).message);
            }
          },
        },
        "Delete",
      )
    : null;

  const showError = (message: string) => {
    formError.textContent = message;
    formError.hidden = false;
  };

  const form = h(
    "form",
    {
      novalidate: true,
      onSubmit: async (event: Event) => {
        event.preventDefault();
        form.querySelectorAll<HTMLElement>("[data-error]").forEach((el) => (el.textContent = ""));
        formError.hidden = true;
        if (!company.value.trim()) {
          setFieldError("company", "Enter the company name");
          company.focus();
          return;
        }
        const input: ApplicationInput = {
          company: company.value.trim(),
          jobRole: role.value.trim() || null,
          location: location.value.trim() || null,
          source: source.value as Source,
          status: status.value as Application["status"],
          appliedOn: appliedOn.value || null,
          interviewAt: fromLocalInput(interviewAt.value),
          jobUrl: jobUrl.value.trim() || null,
          salary: salary.value.trim() || null,
          notes: notes.value.trim() || null,
        };
        saveBtn.setAttribute("disabled", "");
        saveBtn.textContent = "Saving…";
        try {
          if (existing) {
            await api.updateApplication(existing.id, input);
          } else {
            await api.createApplication(input);
            toast({ title: "Application added", message: input.company, level: "success" }, 3000);
          }
          close();
        } catch (e) {
          if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
            Object.entries(e.fieldErrors).forEach(([name, msg]) => setFieldError(name, msg));
          } else {
            showError((e as Error).message);
          }
        } finally {
          saveBtn.removeAttribute("disabled");
          saveBtn.textContent = isEdit ? "Save changes" : "Save";
        }
      },
    },
    h("div", { class: "modal-head" }, h("h2", { id: "form-title" }, isEdit ? "Edit application" : "Add application"), h("button", { class: "icon-btn", type: "button", "aria-label": "Close", onClick: close }, "×")),
    h(
      "div",
      { class: "form-grid" },
      field("Company", company, "company"),
      field("Role", role, "jobRole"),
      field("Source", source, "source"),
      field("Status", status, "status"),
      field("Applied on", appliedOn, "appliedOn"),
      field("Interview date", interviewAt, "interviewAt"),
      field("Location", location, "location"),
      field("Salary", salary, "salary"),
      field("Job link", jobUrl, "jobUrl", true),
      field("Notes", notes, "notes", true),
    ),
    formError,
    h("div", { class: "modal-actions" }, deleteBtn, h("span", { class: "spacer" }), h("button", { class: "btn", type: "button", onClick: close }, "Cancel"), saveBtn),
  );

  const setFieldError = (name: string, message: string) => {
    const el = form.querySelector<HTMLElement>(`[data-error="${name}"]`);
    if (el) {
      el.textContent = message;
    } else {
      showError(message);
    }
  };

  // Clear a field's error as soon as it's edited
  form.addEventListener("input", (event) => {
    const name = (event.target as HTMLInputElement).name;
    const el = form.querySelector<HTMLElement>(`[data-error="${name}"]`);
    if (el) {
      el.textContent = "";
    }
  });

  dialog.appendChild(form);
  dialog.addEventListener("close", () => dialog.remove());
  // Clicking the dark backdrop closes the dialog
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      close();
    }
  });
  document.body.appendChild(dialog);
  dialog.showModal();
  company.focus();
}
