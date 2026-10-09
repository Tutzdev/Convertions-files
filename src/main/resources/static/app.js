"use strict";

const API = "/api/v1";
const STORAGE_KEY = "boostconvert.jobs";
const MAX_REMEMBERED = 20;
const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const POLL_MS = 900;
const STATS_MS = 4000;
const CATEGORY_LABELS = { IMAGE: "Images", DOCUMENT: "Documents", DATA: "Data" };

const state = {
  catalog: [],
  jobs: new Map(),
  stats: null,
  file: null,
  preferredConverter: null,
  view: "convert",
};

// ---------- API

class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || `Request failed (${status}).`);
    this.status = status;
    this.code = problem?.code;
  }
}

async function api(path, options = {}) {
  const response = await fetch(API + path, options);
  if (response.status === 204) return null;
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, body);
  return body;
}

// ---------- DOM helpers (user data only ever goes through textContent)

const $ = (selector) => document.querySelector(selector);

function h(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "text") node.textContent = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? "" : value);
  }
  for (const child of children.flat()) {
    if (child != null && child !== false) node.append(child);
  }
  return node;
}

function icon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("class", "icon");
  svg.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#i-${name}`);
  svg.append(use);
  return svg;
}

function badge(format) {
  return h("span", { class: `badge ${categoryOf(format)}`, text: format });
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10240 ? 1 : 0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatDuration(ms) {
  if (ms == null) return "—";
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

function extensionOf(name) {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : "";
}

/** A format's category comes from the converters that read or write it. */
function categoryOf(format) {
  const match = state.catalog.find((c) => c.from.includes(format) || c.accepts.includes(`.${format}`))
    || state.catalog.find((c) => c.to === format);
  return match ? match.category : "neutral";
}

let toastTimer;
function toast(message) {
  const node = $("#toast");
  node.textContent = message;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 3500);
}

// ---------- Views

function showView(name) {
  const view = document.getElementById(`view-${name}`) ? name : "convert";
  state.view = view;
  document.querySelectorAll(".view").forEach((section) => { section.hidden = section.id !== `view-${view}`; });
  document.querySelectorAll(".nav-item[data-view]").forEach((link) => {
    const active = link.dataset.view === view;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page"); else link.removeAttribute("aria-current");
  });
  const section = document.getElementById(`view-${view}`);
  $("#view-title").textContent = section.dataset.title;
  $("#view-subtitle").textContent = section.dataset.subtitle;
  if (view === "activity") refreshStats();
}

window.addEventListener("hashchange", () => showView(location.hash.slice(1)));

// ---------- Catalog

async function loadCatalog() {
  state.catalog = await api("/converters");
  const sources = [...new Set(state.catalog.flatMap((c) => c.from))].map((f) => f.toUpperCase());
  $("#drop-formats").textContent = `${sources.join(" · ")}  —  up to 20 MB`;
  $("#file-input").accept = [...new Set(state.catalog.flatMap((c) => c.accepts))].join(",");
  renderCatalog();
}

function renderCatalog() {
  const sections = Object.entries(CATEGORY_LABELS).map(([category, label]) => {
    const converters = state.catalog.filter((c) => c.category === category);
    if (converters.length === 0) return null;
    return h("section", { class: "catalog-section" },
      h("h2", {}, label, h("span", { text: `${converters.length} converters` })),
      h("div", { class: "catalog-grid" }, converters.map(converterCard)));
  });
  $("#catalog").replaceChildren(...sections.filter(Boolean));
}

function converterCard(converter) {
  return h("article", { class: "converter" },
    h("div", { class: "converter-formats" },
      converter.from.map((format) => badge(format)), icon("arrow"), badge(converter.to)),
    h("h3", { text: converter.title }),
    h("p", { text: converter.description }),
    h("div", { class: "converter-foot" },
      h("code", { text: converter.id }),
      h("button", { class: "btn btn-ghost btn-sm", type: "button", onclick: () => useConverter(converter) }, "Use")));
}

function useConverter(converter) {
  state.preferredConverter = converter.id;
  location.hash = "convert";
  const input = $("#file-input");
  input.accept = converter.accepts.join(",");
  input.click();
}

// ---------- File selection

function chooseFile(file) {
  if (!file) return;
  state.file = file;
  const extension = extensionOf(file.name);
  // Dedicated converters (PNG to JPG) before shared ones (any image to PDF).
  const converters = state.catalog
    .filter((c) => c.accepts.includes(`.${extension}`))
    .sort((a, b) => a.from.length - b.from.length);
  const label = extension ? extension.toUpperCase() : "FILE";

  const fileBadge = $("#selected-badge");
  fileBadge.textContent = label;
  fileBadge.className = `badge ${categoryOf(extension)}`;
  $("#selected-name").textContent = file.name;
  $("#selected-size").textContent = formatBytes(file.size);
  $("#convert-error").textContent = "";

  const options = $("#target-options");
  if (converters.length === 0) {
    const supported = [...new Set(state.catalog.flatMap((c) => c.from))].join(", ");
    options.replaceChildren(h("p", { class: "no-targets", text: `No conversion for this file type. Supported: ${supported}.` }));
  } else {
    const preferred = converters.find((c) => c.id === state.preferredConverter) || converters[0];
    options.replaceChildren(...converters.map((converter) =>
      h("label", { class: "target" },
        h("input", { type: "radio", name: "target", value: converter.to, checked: converter === preferred }),
        h("strong", { text: converter.to.toUpperCase() }),
        h("span", { text: converter.title }))));
  }

  if (file.size > MAX_UPLOAD_BYTES) {
    $("#convert-error").textContent = `This file has ${formatBytes(file.size)}; files are limited to 20 MB.`;
  }
  $("#dropzone").hidden = true;
  $("#selection").hidden = false;
  updateConvertButton();
}

function selectedTarget() {
  return document.querySelector("input[name=target]:checked")?.value;
}

function updateConvertButton() {
  const target = selectedTarget();
  const button = $("#convert-button");
  button.textContent = target ? `Convert to ${target.toUpperCase()}` : "Convert";
  button.disabled = !target || state.file.size > MAX_UPLOAD_BYTES;
}

function clearSelection() {
  state.file = null;
  state.preferredConverter = null;
  $("#convert-form").reset();
  $("#file-input").accept = [...new Set(state.catalog.flatMap((c) => c.accepts))].join(",");
  $("#selection").hidden = true;
  $("#dropzone").hidden = false;
}

async function submitConversion(event) {
  event.preventDefault();
  const target = selectedTarget();
  if (!state.file || !target) return;

  const button = $("#convert-button");
  button.disabled = true;
  button.textContent = "Uploading…";
  $("#convert-error").textContent = "";

  const form = new FormData();
  form.append("file", state.file);
  form.append("target", target);
  try {
    const job = await api("/conversions", { method: "POST", body: form });
    remember(job);
    clearSelection();
    refreshStats();
  } catch (error) {
    $("#convert-error").textContent = error.message;
    updateConvertButton();
  }
}

function wireDropzone() {
  const dropzone = $("#dropzone");
  ["dragenter", "dragover"].forEach((type) => dropzone.addEventListener(type, (event) => {
    event.preventDefault();
    dropzone.classList.add("dragging");
  }));
  ["dragleave", "drop"].forEach((type) => dropzone.addEventListener(type, () => dropzone.classList.remove("dragging")));
  dropzone.addEventListener("drop", (event) => {
    event.preventDefault();
    chooseFile(event.dataTransfer.files[0]);
  });
  $("#file-input").addEventListener("change", (event) => chooseFile(event.target.files[0]));
  $("#target-options").addEventListener("change", updateConvertButton);
  $("#clear-file").addEventListener("click", clearSelection);
  $("#convert-form").addEventListener("submit", submitConversion);
}

// ---------- Jobs (ids live in this browser only; the API has no "list everyone's jobs" endpoint)

function rememberedIds() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
  } catch {
    return [];
  }
}

function saveIds() {
  try {
    const ids = [...state.jobs.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, MAX_REMEMBERED)
      .map((job) => job.id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
  } catch {
    // private mode: the list simply resets on reload
  }
}

function remember(job) {
  state.jobs.set(job.id, job);
  saveIds();
  renderJobs();
}

function forget(id) {
  state.jobs.delete(id);
  saveIds();
  renderJobs();
}

async function loadJobs() {
  await Promise.all(rememberedIds().map(async (id) => {
    try {
      state.jobs.set(id, await api(`/conversions/${encodeURIComponent(id)}`));
    } catch {
      // expired or unknown: drop it
    }
  }));
  saveIds();
  renderJobs();
}

const STATUS_LABELS = { QUEUED: "Queued", RUNNING: "Converting", SUCCEEDED: "Ready", FAILED: "Failed" };

function targetOf(job) {
  if (job.result) return job.result.format;
  return state.catalog.find((c) => c.id === job.converter)?.to || "?";
}

function minutesLeft(job) {
  if (!job.expiresAt) return null;
  return Math.max(0, Math.round((new Date(job.expiresAt) - Date.now()) / 60000));
}

function jobItem(job) {
  const meta = [h("span", { class: `status ${job.status}`, text: STATUS_LABELS[job.status] })];
  meta.push(h("span", { text: job.result
    ? `${formatBytes(job.source.sizeBytes)} → ${formatBytes(job.result.sizeBytes)}`
    : formatBytes(job.source.sizeBytes) }));
  if (job.durationMs != null) meta.push(h("span", { text: formatDuration(job.durationMs) }));
  const left = minutesLeft(job);
  if (job.status === "SUCCEEDED" && left != null) meta.push(h("span", { text: `deleted in ${left} min` }));

  const actions = h("div", { class: "job-actions" });
  if (job.status === "SUCCEEDED") {
    actions.append(h("a", { class: "btn btn-primary btn-sm", href: job.result.downloadUrl, download: job.result.name },
      icon("download"), "Download"));
  }
  actions.append(h("button", {
    class: "icon-btn", type: "button", "aria-label": `Delete ${job.source.name}`, title: "Delete now",
    onclick: () => deleteJob(job),
  }, icon("trash")));

  return h("li", { class: `job ${job.status}` },
    h("div", { class: "job-formats" }, badge(job.source.format), icon("arrow"), badge(targetOf(job))),
    h("div", { class: "job-main" },
      h("p", { class: "job-name", text: job.result ? job.result.name : job.source.name, title: job.source.name }),
      h("div", { class: "job-meta" }, meta),
      job.error ? h("p", { class: "job-error", text: job.error.message }) : null),
    actions);
}

function renderJobs() {
  const jobs = [...state.jobs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const list = $("#jobs");
  if (jobs.length === 0) {
    list.replaceChildren(h("li", { class: "empty" },
      h("strong", { text: "Nothing converted yet" }),
      "Converted files show up here, ready to download."));
    return;
  }
  list.replaceChildren(...jobs.map(jobItem));
}

async function deleteJob(job) {
  try {
    await api(`/conversions/${encodeURIComponent(job.id)}`, { method: "DELETE" });
    toast(`${job.source.name} was deleted from the server.`);
  } catch (error) {
    if (error.status !== 404) {
      toast(error.message);
      return;
    }
  }
  forget(job.id);
  refreshStats();
}

async function pollActiveJobs() {
  const active = [...state.jobs.values()].filter((job) => job.status === "QUEUED" || job.status === "RUNNING");
  if (active.length === 0) return;
  let finished = false;
  await Promise.all(active.map(async (job) => {
    try {
      const fresh = await api(`/conversions/${encodeURIComponent(job.id)}`);
      state.jobs.set(job.id, fresh);
      if (fresh.status === "SUCCEEDED" || fresh.status === "FAILED") finished = true;
    } catch (error) {
      if (error.status === 404) state.jobs.delete(job.id);
    }
  }));
  renderJobs();
  if (finished) refreshStats();
}

// ---------- Stats

async function refreshStats() {
  try {
    state.stats = await api("/stats");
  } catch {
    renderQueuePill(null);
    return;
  }
  renderKpis();
  renderQueuePill(state.stats.queue);
  renderActivity();
  const minutes = state.stats.retentionMinutes;
  $("#retention-label").textContent = minutes === 1 ? "1 minute" : `${minutes} minutes`;
}

function kpi(label, value, sub) {
  return h("div", { class: "kpi" },
    h("p", { class: "kpi-label", text: label }),
    h("p", { class: "kpi-value", text: value }),
    h("p", { class: "kpi-sub", text: sub }));
}

function renderKpis() {
  const { totals, queue } = state.stats;
  const finished = totals.succeeded + totals.failed;
  const rate = finished === 0 ? "—" : `${Math.round((totals.succeeded / finished) * 100)}%`;
  $("#kpis").replaceChildren(
    kpi("Converted", totals.succeeded.toLocaleString("en-US"), `${totals.failed} failed`),
    kpi("Success rate", rate, "of finished conversions"),
    kpi("Average time", finished === 0 ? "—" : formatDuration(totals.averageMs), "per conversion"),
    kpi("Workers busy", `${queue.busy}/${queue.workers}`, `${queue.waiting} waiting · queue of ${queue.capacity}`));
}

function renderQueuePill(queue) {
  const pill = $("#queue-status");
  const text = pill.lastElementChild;
  if (!queue) {
    pill.className = "queue-pill";
    text.textContent = "API unreachable";
    return;
  }
  if (queue.waiting >= queue.capacity) {
    pill.className = "queue-pill full";
    text.textContent = "Queue full, try again shortly";
  } else if (queue.busy > 0) {
    pill.className = "queue-pill busy";
    text.textContent = queue.waiting > 0 ? `Converting · ${queue.waiting} waiting` : "Converting";
  } else {
    pill.className = "queue-pill ok";
    text.textContent = `Ready · ${queue.workers} workers`;
  }
}

function bar(fraction) {
  const width = `${Math.round(Math.min(1, fraction) * 100)}%`;
  return h("div", { class: "bar" }, h("span", { style: `width: ${width}` }));
}

function renderActivity() {
  const { converters, queue, totals } = state.stats;
  const busiest = Math.max(1, ...converters.map((c) => c.succeeded + c.failed));
  $("#usage-table").replaceChildren(
    h("thead", {}, h("tr", {},
      h("th", { text: "Converter" }), h("th", { class: "num", text: "Succeeded" }),
      h("th", { class: "num", text: "Failed" }), h("th", { class: "num", text: "Avg time" }),
      h("th", { class: "share", text: "Share" }))),
    h("tbody", {}, converters.map((c) => h("tr", {},
      h("td", {}, h("strong", { text: c.title }), h("div", { class: "muted small", text: c.id })),
      h("td", { class: "num", text: String(c.succeeded) }),
      h("td", { class: "num", text: String(c.failed) }),
      h("td", { class: "num", text: c.succeeded + c.failed === 0 ? "—" : formatDuration(c.averageMs) }),
      h("td", { class: "share" }, bar((c.succeeded + c.failed) / busiest))))));

  const row = (label, value, fraction) => h("div", { class: "queue-row" },
    h("header", {}, h("span", { text: label }), h("strong", { text: value })), bar(fraction));
  const fact = (label, value) => h("div", { class: "fact" }, h("p", { text: label }), h("p", { text: value }));
  $("#queue-detail").replaceChildren(
    row("Workers busy", `${queue.busy} of ${queue.workers}`, queue.busy / queue.workers),
    row("Waiting in queue", `${queue.waiting} of ${queue.capacity}`, queue.waiting / queue.capacity),
    h("div", { class: "queue-facts" },
      fact("Rejected with 429", String(totals.rejected)),
      fact("Jobs in memory", String(state.stats.jobsStored)),
      fact("Retention", `${state.stats.retentionMinutes} min`),
      fact("Upload limit", "20 MB")));
}

// ---------- Start

async function start() {
  wireDropzone();
  showView(location.hash.slice(1) || "convert");
  try {
    await loadCatalog();
  } catch {
    $("#drop-formats").textContent = "The API is not reachable.";
  }
  await Promise.all([loadJobs(), refreshStats()]);
  setInterval(pollActiveJobs, POLL_MS);
  setInterval(refreshStats, STATS_MS);
}

start();
