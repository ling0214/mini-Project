import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import mermaid from "mermaid";
import "./styles.css";

mermaid.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
const ANALYST_PROFILE = "software-analyst";

const ROLE_LABELS = {
  "project-analyst": "Project Analyst",
  "business-analyst": "Business Analyst",
  tester: "Software Tester",
};

const ROLE_CARDS = [
  {
    id: "project-analyst",
    title: "Project Analyst",
    caption: "Scope change requests, identify risk hotspots, and prepare delivery handoff.",
  },
  {
    id: "business-analyst",
    title: "Business Analyst",
    caption: "Validate business change intent against real code behavior and dependencies.",
  },
  {
    id: "tester",
    title: "Software Tester",
    caption: "Build regression scope and test plans grounded in project history.",
  },
];

const SKILLS_BY_ROLE = {
  "project-analyst": ["code-qa", "impact-analysis"],
  "business-analyst": ["requirement-analysis", "impact-analysis", "code-qa"],
  tester: ["code-qa", "test-case-gen"],
};

const SKILL_META = {
  "requirement-analysis": {
    title: "Requirement Analysis",
    desc: "Find ambiguity and missing information",
  },
  "code-qa": {
    title: "Code Q&A",
    desc: "Ask a grounded codebase question",
  },
  "impact-analysis": {
    title: "Impact Analysis",
    desc: "Scope a requirement or PR",
  },
  "test-case-gen": {
    title: "Test Case Gen",
    desc: "Generate a regression test plan",
  },
  "handoff-summary": {
    title: "Handoff Summary",
    desc: "Compile analyst findings for handoff",
  },
};

const CHIPS = {
  "requirement-analysis": [
    "Customer should be able to change payment_method after checkout is submitted.",
    "Maybe let users update card details later.",
    "The customer must be able to apply promo_code before payment confirmation.",
  ],
  "code-qa": [
    "What does checkout_endpoint depend on, and are there known issues with it?",
    "Has payments code broken before?",
    "What would change if I touch calculate_total?",
  ],
  "impact-analysis": [
    "Add automatic retry to charge_card when the payment gateway times out",
    "Change checkout confirmation to include payment reference",
    "Update calculate_total to support promo codes",
  ],
  "test-case-gen": ["checkout_endpoint", "charge_card", "calculate_total"],
};

const EMPTY_TICKET = {
  sourceType: "Manual",
  sourceName: "Manual entry",
  sourceUrl: "",
  receivedAt: "",
  ticketKey: "",
  ticketTitle: "",
  priority: "Medium",
  reporter: "",
  description: "",
  acceptanceCriteria: "",
  comments: "",
  codeSnippet: "",
  codeEvidenceUrl: "",
};

const SAMPLE_TICKET = {
  sourceType: "Jira",
  sourceName: "Jira sample import",
  sourceUrl: "https://jira.example.local/browse/MBC-204",
  receivedAt: "Today 09:10",
  ticketKey: "MBC-204",
  ticketTitle: "Allow donors to filter available aid requests by city and urgency",
  priority: "High",
  reporter: " Supervisor",
  description:
    "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.",
  acceptanceCriteria:
    "Given a donor is browsing available aid requests, when the donor selects city, category, or urgency filters, then the page only shows matching approved aid requests.",
  comments:
    "Need to confirm whether filters should update through page reload or AJAX, and whether the same filter should apply to admin monitoring later.",
};

const EMAIL_TICKET = {
  sourceType: "Email",
  sourceName: "Stakeholder email",
  sourceUrl: "",
  receivedAt: "Today 10:35",
  ticketKey: "MBC-211",
  ticketTitle: "Notify collection center admins when urgent aid requests are approved",
  priority: "Medium",
  reporter: "Collection Center Admin",
  description:
    "Collection center admin should receive a notification when an urgent aid request is approved and ready for assignment.",
  acceptanceCriteria:
    "Given an urgent aid request is approved, when the approval is saved, then relevant collection center admins are notified.",
  comments:
    "Stakeholder is not sure whether notification should be email, in-app, or both. Need to confirm target admins by city or collection center.",
};

const MEETING_NOTE_TICKET = {
  sourceType: "Meeting Notes",
  sourceName: "Sprint planning notes",
  sourceUrl: "",
  receivedAt: "Yesterday 16:20",
  ticketKey: "MBC-218",
  ticketTitle: "Improve flood report verification before public display",
  priority: "High",
  reporter: "Product Owner",
  description:
    "Reports submitted by victims should be verified before they appear on the public flood map.",
  acceptanceCriteria:
    "Given a victim submits a flood report, when the report is not verified, then it should not be visible on the public map.",
  comments:
    "Need to clarify who can verify reports and whether existing public reports require migration or review.",
};

const ANALYST_INBOX_ITEMS = [
  {
    id: "jira-mbc-204",
    source: "Jira",
    status: "Ready for triage",
    age: "Today",
    ticket: SAMPLE_TICKET,
  },
  {
    id: "email-mbc-211",
    source: "Email",
    status: "Needs clarification",
    age: "Today",
    ticket: EMAIL_TICKET,
  },
  {
    id: "meeting-mbc-218",
    source: "Meeting Notes",
    status: "New requirement",
    age: "Yesterday",
    ticket: MEETING_NOTE_TICKET,
  },
];

const TARGET_PROJECT = {
  name: "MyBanjirCare",
  framework: "Laravel 10 / PHP 8.1",
  source: "github.com/ling0214/MyBanjirCare",
  modules: ["Aid Request", "Donation", "Flood Report", "Collection Center", "Auth / OTP"],
};

const QUICK_ACTION_EVENT = "analyst-workbench:quick-action";

// Still dummy/prototype data (see each component's own "Dummy view" subtitle) --
// ordered by how close each is to being wired to real data + workflow value,
// highest first. Hermes Incident Tracker used to be here too; it moved up to
// Workspace once it was wired to real data.
const ENHANCEMENT_TOOLS = [
  // ["memory-center", "Memory", "Similar past changes"],
  // ["testing-sync", "Testing Sync", "Pass/fail and Jira updates"],
  // ["evidence-gate", "Evidence Gate", "RCA readiness checklist"],
  // ["db-diagnostics", "DB Checks", "Evidence request flow"],
];

const TESTING_SYNC_COLUMNS = [
  {
    id: "pending",
    title: "Pending",
    items: [
      {
        key: "MBC-204",
        title: "Aid request filtering UAT",
        owner: "QA",
        action: "Waiting tester execution",
        status: "PENDING",
      },
    ],
  },
  {
    id: "not-pass",
    title: "Not Pass",
    items: [
      {
        key: "MBC-211",
        title: "Urgent approval notification",
        owner: "Developer",
        action: "AI drafts fail reason and Jira comment",
        status: "NOT PASS",
      },
    ],
  },
  {
    id: "pass",
    title: "Pass",
    items: [
      {
        key: "MBC-218",
        title: "Flood report verification",
        owner: "Analyst",
        action: "Auto-sync pass note to Jira",
        status: "PASS",
      },
    ],
  },
];

const DB_DIAGNOSTIC_REQUESTS = [
  {
    id: "DBQ-104",
    claim: "Approved aid request is not visible to donor filter.",
    query: "Check aid_requests.status, city_id, category_id, urgency.",
    owner: "Developer / DBA",
    state: "Needs result",
  },
  {
    id: "DBQ-105",
    claim: "Notification may not be linked to collection center role.",
    query: "Check users.role and notification_preferences mapping.",
    owner: "Backend owner",
    state: "Drafted",
  },
];

const EVIDENCE_GATE_ITEMS = [
  ["Requirement", "Ready", "Ticket summary and acceptance note are available."],
  ["Code", "Ready", "Impacted controller/model/view paths found from project graph."],
  ["Log", "Missing", "No runtime log or error timeline attached yet."],
  ["DB", "Needed", "Potential status/config claim needs read-only query result."],
  ["Stakeholder", "Ready", "Clarification note confirms filter behavior."],
];

/** Fixed vocabulary — must match HermesStatusService.VALID_STATUSES on the backend. */
const HERMES_STATUS_ORDER = [
  "Sent to Hermes",
  "Hermes accepted",
  "Developer update",
  "Testing decision",
  "Close summary",
];

const MEMORY_MATCHES = [
  {
    score: 82,
    title: "Donation status filter change",
    outcome: "Affected donor browse page, aid request model, and notification tests.",
    reuse: "Reuse regression cas es for approved/pending status visibility.",
  },
  {
    score: 68,
    title: "Collection center assignment notification",
    outcome: "Required role-access clarification before developer handoff.",
    reuse: "Ask stakeholder to confirm owner role before scope approval.",
  },
  {
    score: 54,
    title: "Flood report public display review",
    outcome: "Needed verification gate and manual QA signoff.",
    reuse: "Add negative case for unverified records.",
  },
];

const KANBAN_COLUMN_META = [
  { id: "todo", title: "To Do", summary: "Accepted, not yet actively worked" },
  { id: "progress", title: "In Progress", summary: "Analysis, development, or testing active" },
  { id: "review", title: "In Review", summary: "Waiting for handoff or sync signoff" },
  { id: "done", title: "Done", summary: "Closed and synced" },
];

// mini-Project tickets track 6 phases with exactly one "active" cursor at a
// time (see TicketTrackerService.buildTicketView) -- a ticket only earns a
// kanban slot once Requirement Review is done (reviewed=true), matching the
// same review-gate the rest of the workbench enforces before work is
// considered "real". Impact Analysis / Development / Testing active all read
// as "In Progress" per the agreed column mapping.
function kanbanColumnForTicket(ticket) {
  const phases = ticket.phases || [];
  const byName = Object.fromEntries(phases.map((phase) => [phase.name, phase.state]));
  if (byName["Requirement Review (ticket raised)"] !== "done") return null;
  if (byName["Jira / UI Sync"] === "done") return "done";
  const activeName = phases.find((phase) => phase.state === "active")?.name;
  if (activeName === "Review / Handoff" || activeName === "Jira / UI Sync") return "review";
  if (activeName === "Impact Analysis" || activeName === "Development / Fixing" || activeName === "Testing") {
    return "progress";
  }
  return "todo";
}

// Hermes-originated incidents only earn a kanban slot once Hermes has
// actually accepted the task -- "Sent to Hermes" alone just means the
// package left mini-Project, not that any real work has started yet.
function kanbanColumnForHermesTask(task) {
  if (task.status === "Hermes accepted") return "todo";
  if (task.status === "Developer update") return "progress";
  if (task.status === "Testing decision") return "review";
  if (task.status === "Close summary") return "done";
  return null;
}

function App() {
  const [backendStatus, setBackendStatus] = useState("checking");
  const [view, setView] = useState("home");
  const [workspace, setWorkspace] = useState(undefined);

  useEffect(() => {
    checkBackend(setBackendStatus);
    loadCurrentWorkspace();
  }, []);

  useEffect(() => {
    if (workspace?.index_status !== "indexing" && workspace?.graphify_index_status !== "indexing") {
      return;
    }
    const interval = setInterval(loadCurrentWorkspace, 4000);
    return () => clearInterval(interval);
  }, [workspace?.index_status, workspace?.graphify_index_status]);

  async function loadCurrentWorkspace() {
    try {
      const current = await api("/api/workspace/current");
      setWorkspace(current || null);
    } catch {
      setWorkspace(null);
    }
  }

  function enterWorkbench() {
    setView(workspace ? "workbench" : "connect-project");
  }

  if (view === "home") {
    return <HomePage status={backendStatus} onEnter={enterWorkbench} />;
  }

  if (view === "connect-project") {
    return (
      <ConnectProjectScreen
        onConnected={(next) => {
          setWorkspace(next);
          setView("workbench");
        }}
        onCancel={() => setView(workspace ? "workbench" : "home")}
        onActiveRemoved={() => setWorkspace(null)}
      />
    );
  }

  if (view === "diagram") {
    return (
      <ProjectOverviewScreen
        workspace={workspace}
        onWorkspaceUpdated={setWorkspace}
        onBack={() => setView("workbench")}
        onSwitchProject={() => setView("connect-project")}
      />
    );
  }

  return (
    <div className="app-shell">
      <TopBar
        status={backendStatus}
        onHome={() => setView("home")}
        workspace={workspace}
        onSwitchProject={() => setView("connect-project")}
        onViewDiagram={() => setView("diagram")}
      />
      <AnalystWorkflow
        workspace={workspace}
        onWorkspaceUpdated={setWorkspace}
        onViewProjectOverview={() => setView("diagram")}
        onSwitchProject={() => setView("connect-project")}
      />
      <footer className="app-footer">
        API: <code>{API_BASE}</code> · Artifacts, review gate, Jira issue creation, and Bitbucket PR comments are backed by the Spring Boot service.
      </footer>
    </div>
  );
}

function ConnectProjectScreen({ onConnected, onCancel, onActiveRemoved }) {
  const [projects, setProjects] = useState([]);
  const [name, setName] = useState("");
  const [repoUrl, setRepoUrl] = useState("");
  const [localPath, setLocalPath] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [removingId, setRemovingId] = useState(null);
  const [browserOpen, setBrowserOpen] = useState(false);
  const [hermesMatchCount, setHermesMatchCount] = useState(null);

  const [subpathsByProject, setSubpathsByProject] = useState({});
  const [expandedSubpathsProjectId, setExpandedSubpathsProjectId] = useState(null);
  const [newSubpathLabel, setNewSubpathLabel] = useState("");
  const [newSubpathPath, setNewSubpathPath] = useState("");
  const [subpathBrowserOpenFor, setSubpathBrowserOpenFor] = useState(null);
  const [addingSubpath, setAddingSubpath] = useState(false);
  const [removingSubpathId, setRemovingSubpathId] = useState(null);
  const [subpathError, setSubpathError] = useState("");

  useEffect(() => {
    loadProjects();
  }, []);

  function loadSubpaths(projectId) {
    api(`/api/workspace/${projectId}/subpaths`)
      .then((rows) => setSubpathsByProject((prev) => ({ ...prev, [projectId]: rows })))
      .catch(() => setSubpathsByProject((prev) => ({ ...prev, [projectId]: [] })));
  }

  function toggleSubpaths(projectId) {
    if (expandedSubpathsProjectId === projectId) {
      setExpandedSubpathsProjectId(null);
      return;
    }
    setExpandedSubpathsProjectId(projectId);
    setNewSubpathLabel("");
    setNewSubpathPath("");
    setSubpathError("");
    loadSubpaths(projectId);
  }

  async function addSubpath(projectId) {
    if (!newSubpathLabel.trim() || !newSubpathPath.trim()) {
      setSubpathError("Label and path are required.");
      return;
    }
    setSubpathError("");
    setAddingSubpath(true);
    try {
      await api(`/api/workspace/${projectId}/subpaths`, {
        method: "POST",
        body: { label: newSubpathLabel.trim(), path: newSubpathPath.trim() },
      });
      setNewSubpathLabel("");
      setNewSubpathPath("");
      loadSubpaths(projectId);
    } catch (err) {
      setSubpathError(err.message);
    } finally {
      setAddingSubpath(false);
    }
  }

  async function removeSubpath(projectId, subpathId) {
    setSubpathError("");
    setRemovingSubpathId(subpathId);
    try {
      await api(`/api/workspace/${projectId}/subpaths/${subpathId}`, { method: "DELETE" });
      loadSubpaths(projectId);
    } catch (err) {
      setSubpathError(err.message);
    } finally {
      setRemovingSubpathId(null);
    }
  }

  // Debounced: as the analyst types/browses to a path, check whether Hermes
  // already has tracked activity under it (or an ancestor/descendant of it —
  // see HermesStatusService.pathsRelated), so they don't have to guess which
  // folder "unlocks" the Hermes Incident Tracker page before ever connecting it.
  useEffect(() => {
    const trimmed = localPath.trim();
    if (!trimmed) {
      setHermesMatchCount(null);
      return;
    }
    const timeout = setTimeout(() => {
      api(`/api/hermes/status/current?project=${encodeURIComponent(trimmed)}`)
        .then((rows) => setHermesMatchCount(rows.length))
        .catch(() => setHermesMatchCount(null));
    }, 400);
    return () => clearTimeout(timeout);
  }, [localPath]);

  function loadProjects() {
    api("/api/workspace")
      .then(setProjects)
      .catch(() => setProjects([]));
  }

  async function declare() {
    setError("");
    if (!name.trim() || !localPath.trim()) {
      setError("Project name and local repo path are required.");
      return;
    }
    setLoading(true);
    try {
      const saved = await api("/api/workspace", {
        method: "POST",
        body: { name, repo_url: repoUrl, local_path: localPath },
      });
      onConnected(saved);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function activate(id) {
    setError("");
    setLoading(true);
    try {
      const saved = await api(`/api/workspace/${id}/activate`, { method: "POST" });
      onConnected(saved);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function remove(project) {
    if (!window.confirm(`Remove "${project.name}" from declared projects? This does not delete any files.`)) {
      return;
    }
    setError("");
    setRemovingId(project.id);
    try {
      await api(`/api/workspace/${project.id}`, { method: "DELETE" });
      loadProjects();
      if (project.active && onActiveRemoved) {
        onActiveRemoved();
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setRemovingId(null);
    }
  }

  const activeProject = projects.find((project) => project.active);
  const readyCount = projects.filter((project) => project.index_status === "ready" && project.graphify_index_status === "ready").length;
  const indexingCount = projects.filter((project) => project.index_status === "indexing" || project.graphify_index_status === "indexing").length;

  return (
    <main className="connect-project-shell command-center-shell">
      <section className="connect-project-panel command-center-panel">
        <div className="connect-project-head command-center-head">
          <span className="brand-mark">
            <LogoMark />
          </span>
          <div>
            <strong>Project Control Center</strong>
            <span>Connect repo intelligence now; keep the workflow ready for Hermes handoff later.</span>
          </div>
        </div>

        <div className="project-command-grid">
          <section className="project-connect-card">
            <div className={`active-project-banner ${activeProject ? "" : "none"}`}>
              <span className="active-project-banner-dot" />
              {activeProject ? (
                <span>
                  Currently connected: <strong>{activeProject.name}</strong>
                  <span className="active-project-banner-path"> — {activeProject.local_path}</span>
                </span>
              ) : (
                <span>No project is currently connected.</span>
              )}
            </div>

            <label className="field-label">Project name</label>
            <input type="text" value={name} onChange={(event) => setName(event.target.value)} placeholder="MyBanjirCare" />

            <label className="field-label">Local repo path</label>
            <div className="path-input-row">
              <input
                type="text"
                value={localPath}
                onChange={(event) => setLocalPath(event.target.value)}
                placeholder="C:/tmp/MyBanjirCare"
              />
              <button className="btn ghost compact" type="button" onClick={() => setBrowserOpen(true)}>
                Browse…
              </button>
            </div>
            {hermesMatchCount !== null && (
              <p className={`hermes-match-hint ${hermesMatchCount > 0 ? "found" : "none"}`}>
                {hermesMatchCount > 0
                  ? `This path matches ${hermesMatchCount} tracked Hermes task${hermesMatchCount === 1 ? "" : "s"} — connecting here will show its progress on the Hermes Incident Tracker page.`
                  : "No Hermes activity tracked under this path yet."}
              </p>
            )}
            {browserOpen && (
              <FolderBrowserModal
                initialPath={localPath}
                onSelect={(path) => {
                  setLocalPath(path);
                  setBrowserOpen(false);
                }}
                onClose={() => setBrowserOpen(false)}
              />
            )}

            <label className="field-label">Repo URL (optional, for reference)</label>
            <input
              type="text"
              value={repoUrl}
              onChange={(event) => setRepoUrl(event.target.value)}
              placeholder="https://github.com/org/repo"
            />

            {error && <ErrorBox message={error} />}
            <div className="action-row">
              <button className="btn primary" type="button" disabled={loading} onClick={declare}>
                {loading ? "Connecting..." : "Connect project"}
              </button>
              {onCancel && (
                <button className="btn ghost" type="button" onClick={onCancel}>
                  Cancel
                </button>
              )}
            </div>
          </section>
      {projects.length > 0 && (
          <section className="project-control-switchboard">
            <div className="project-process-board-head">
              <div>
                <label className="field-label">Project switchboard</label>
                <p>Switch the active repo used by Repo AI, project overview, impact analysis, and ticket status tracking.</p>
              </div>
              <span>{projects.length} project{projects.length === 1 ? "" : "s"}</span>
            </div>
            <div className="project-switchboard-grid">
              {projects.map((project) => (
                <article key={project.id} className={project.active ? "active" : ""}>
                  <div>
                    <div className="project-switchboard-title">
                      <strong>{project.name}</strong>
                      {project.active && <span className="tag good">Active</span>}
                    </div>
                    <span>{project.local_path}</span>
                    <small>
                      Code graph: {indexStatusLabel(project.index_status, project.index_error)} | Diagram graph:{" "}
                      {indexStatusLabel(project.graphify_index_status, project.graphify_index_error)}
                    </small>
                  </div>
                  <div className="project-switchboard-actions">
                    {!project.active && (
                      <button className="btn ghost compact" type="button" disabled={loading} onClick={() => activate(project.id)}>
                        Switch project
                      </button>
                    )}
                    <button className="btn ghost compact" type="button" onClick={() => toggleSubpaths(project.id)}>
                      {expandedSubpathsProjectId === project.id
                        ? "Hide sub-paths"
                        : `Sub-paths${subpathsByProject[project.id]?.length ? ` (${subpathsByProject[project.id].length})` : ""}`}
                    </button>
                    <button
                      className="btn ghost compact danger"
                      type="button"
                      disabled={removingId === project.id}
                      onClick={() => remove(project)}
                    >
                      {removingId === project.id ? "Removing..." : "Remove"}
                    </button>
                  </div>

                  {expandedSubpathsProjectId === project.id && (
                    <div className="project-subpath-panel">
                      <p className="field-hint">
                        Name the frontend/backend/admin folders inside this project so Project Overview and endpoint
                        tracing can target each one individually — leave empty to keep treating this project as one
                        folder.
                      </p>
                      {(subpathsByProject[project.id] || []).length > 0 && (
                        <ul className="project-subpath-list">
                          {subpathsByProject[project.id].map((sp) => (
                            <li key={sp.id}>
                              <strong>{sp.label}</strong>
                              <span>{sp.path}</span>
                              <button
                                className="btn ghost compact danger"
                                type="button"
                                disabled={removingSubpathId === sp.id}
                                onClick={() => removeSubpath(project.id, sp.id)}
                              >
                                {removingSubpathId === sp.id ? "Removing..." : "Remove"}
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                      <div className="project-subpath-add-row">
                        <input
                          type="text"
                          value={newSubpathLabel}
                          onChange={(event) => setNewSubpathLabel(event.target.value)}
                          placeholder="e.g. Frontend"
                        />
                        <input
                          type="text"
                          value={newSubpathPath}
                          onChange={(event) => setNewSubpathPath(event.target.value)}
                          placeholder="C:/path/to/frontend"
                        />
                        <button className="btn ghost compact" type="button" onClick={() => setSubpathBrowserOpenFor(project.id)}>
                          Browse…
                        </button>
                        <button className="btn primary compact" type="button" disabled={addingSubpath} onClick={() => addSubpath(project.id)}>
                          {addingSubpath ? "Adding..." : "Add"}
                        </button>
                      </div>
                      {subpathError && <ErrorBox message={subpathError} />}
                    </div>
                  )}
                  {subpathBrowserOpenFor === project.id && (
                    <FolderBrowserModal
                      initialPath={newSubpathPath}
                      onSelect={(path) => {
                        setNewSubpathPath(path);
                        setSubpathBrowserOpenFor(null);
                      }}
                      onClose={() => setSubpathBrowserOpenFor(null)}
                    />
                  )}
                </article>
              ))}
            </div>
          </section>
        )}

          {/* <aside className="project-telemetry-panel">
            <div className="project-telemetry-head">
              <span className="source-pill">Overview project tracker</span>
              <h2>Project control functions</h2>
              <p>Import a repo, understand the project, switch the active workspace, and monitor ticket progress before the analyst handoff.</p>
            </div>
            <div className="project-health-grid">
              <span><strong>{activeProject ? 1 : 0}</strong> Active project</span>
              <span><strong>{readyCount}</strong> Ready repo</span>
              <span><strong>{indexingCount}</strong> Indexing</span>
              <span><strong>4</strong> Ticket status</span>
            </div>
            <div className="project-pipeline">
              <span>Import repo</span>
              <span>Overview diagram</span>
              <span>Track phase</span>
              <span>Kanban status</span>
            </div>
            <div className="project-integration-row">
              <span>Graphify</span>
              <span>Codebase Memory</span>
              <span>Jira</span>
              <span>Hermes handoff</span>
            </div>
          </aside> */}
        </div>

        <section className="project-control-switchboard">
          <div className="project-process-board-head">
            <div>
              <label className="field-label">Hermes bridge setup</label>
              <p>Set this up now, before heading into the workbench — repo intake channels, storage paths, and PR-package flow, all in one place.</p>
            </div>
          </div>
          <HermesSetupWizardPage workspace={activeProject} embedded />
        </section>

      </section>
    </main>
  );
}

function FolderBrowserModal({ initialPath, onSelect, onClose }) {
  const [currentPath, setCurrentPath] = useState("");
  const [parentPath, setParentPath] = useState(null);
  const [entries, setEntries] = useState([]);
  const [isGitRepo, setIsGitRepo] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    load(initialPath || "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function load(path) {
    setLoading(true);
    setError("");
    try {
      const query = path ? `?path=${encodeURIComponent(path)}` : "";
      const result = await api(`/api/workspace/browse${query}`);
      setCurrentPath(result.current_path);
      setParentPath(result.parent_path);
      setEntries(result.entries || []);
      setIsGitRepo(result.current_is_git_repo);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="folder-browser-modal">
        <div className="folder-browser-head">
          <strong>Choose a folder</strong>
          <button className="btn ghost compact" type="button" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="folder-browser-path">
          {isGitRepo && <span className="tag good">Git repo</span>}
          <code>{currentPath || "…"}</code>
        </div>
        {error && <ErrorBox message={error} />}
        <div className="folder-browser-list">
          {parentPath && (
            <button className="folder-browser-item" type="button" disabled={loading} onClick={() => load(parentPath)}>
              .. (up)
            </button>
          )}
          {entries.map((entry) => (
            <button
              key={entry.path}
              className="folder-browser-item"
              type="button"
              disabled={loading}
              onClick={() => load(entry.path)}
            >
              {entry.name}
              {entry.git_repo && <span className="tag good">git</span>}
            </button>
          ))}
          {!loading && entries.length === 0 && <p className="muted-note">No subfolders here.</p>}
        </div>
        <div className="action-row">
          <button
            className="btn primary"
            type="button"
            disabled={loading || !currentPath}
            onClick={() => onSelect(currentPath)}
          >
            Select this folder
          </button>
        </div>
      </div>
    </div>
  );
}

function HomePage({ status, onEnter }) {
  const queueItems = [
    {
      source: "Jira",
      key: "KAN-121",
      title: "Donor filter change request",
      state: "Pending clarification",
      priority: "High",
    },
    {
      source: "Email",
      key: "REQ-204",
      title: "Collection center notification",
      state: "Ready for triage",
      priority: "Medium",
    },
    {
      source: "Meeting",
      key: "MOM-18",
      title: "Flood report approval flow",
      state: "Notes captured",
      priority: "High",
    },
  ];

  return (
    <main className="home-shell">
      <section className="home-hero">
        <div className="home-nav">
          <div className="home-brand">
            <span className="brand-mark">
              <LogoMark />
            </span>
            <div>
              <strong>Analyst Workbench</strong>
              <span>Software Analyst workflow assistant</span>
            </div>
          </div>
          <div className="home-nav-center" aria-label="Primary navigation">
            <span>Workspace</span>
            <span>Projects</span>
            <span>AI</span>
            <span>Settings</span>
          </div>
          <div className="home-nav-right">
            <span className="home-breadcrumb">Workspace / Project / Dashboard</span>
            <span className={`connection ${status}`}>
              <span />
              {status === "up" ? "API Healthy" : status === "down" ? "API Offline" : "Checking API"}
            </span>
          </div>
        </div>

        <div className="home-hero-grid">
          <div className="home-copy">
            <div className="eyebrow">Unified analyst operations</div>
            <h1>
              One control center for requirement intake, <span>AI analysis</span>, and delivery handoff.
            </h1>
            <p>
              Monitor Jira, email, meetings, and manual requests in one place, then guide each work item through
              clarification, impact analysis, testing scope, and handoff without switching between tools.
            </p>
            <div className="home-actions">
              <button className="home-primary-action" type="button" onClick={onEnter}>
                Enter Workbench <span aria-hidden="true">&rarr;</span>
              </button>
              <div className="home-proof">
                <strong>12 requests monitored</strong>
                <span>4 Jira, 6 email, 2 meeting notes</span>
              </div>
            </div>
          </div>

          <div className="home-console" aria-label="Workflow preview">
            <div className="home-console-head">
              <span>Live analyst queue</span>
              <strong>Today</strong>
            </div>
            <div className="home-signal-row">
              {["jira", "email", "meet", "calendar"].map((platform) => (
                <span key={platform} className={`home-signal ${platform} ${platform === "jira" ? "active" : ""}`}>
                  <PlatformLogo platform={platform} />
                </span>
              ))}
            </div>
            <div className="home-live-board">
              {queueItems.map((item, index) => (
                <article key={item.key} className={`home-queue-card item-${index + 1}`}>
                  <div>
                    <span>{item.source}</span>
                    <strong>{item.key}</strong>
                  </div>
                  <p>{item.title}</p>
                  <footer>
                    <small>{item.state}</small>
                    <em>{item.priority}</em>
                  </footer>
                </article>
              ))}
            </div>
            <div className="home-ai-preview">
              <div>
                <span>AI analysis</span>
                <strong>Risk: Medium</strong>
              </div>
              <p>Missing acceptance criteria detected. Impact points to aid request filtering and notification modules.</p>
              <small>Estimated analyst review: 6 min</small>
            </div>
            <div className="home-flow-preview" aria-label="Workflow steps">
              {["Intake", "Clarify", "Impact", "Testing", "Handoff"].map((step, index) => (
                <span key={step} className={index === 2 ? "current" : ""}>
                  {step}
                </span>
              ))}
            </div>
          </div>
        </div>

        <div className="home-capability-row">
          <div>
            <strong>Inbox</strong>
            <span>12 requests · 4 emails · 2 Jira updates</span>
          </div>
          <div>
            <strong>Pipeline</strong>
            <span>5 requirement · 2 impact · 3 testing</span>
          </div>
          <div>
            <strong>Review</strong>
            <span>2 pending · 6 ready for handoff</span>
          </div>
        </div>
      </section>
    </main>
  );
}

/**
 * Pre-existing role-first / free-form skill picker (Section 4.1). Kept as a
 * fallback behind the mode switch — AnalystWorkflow below is now the primary
 * landing experience (docs/proposal.md "Software Analyst Workflow Assistant"
 * framing: one continuous pipeline, not role selection first).
 */
function LegacyWorkbench() {
  const [role, setRole] = useState(null);
  const [step, setStep] = useState("role");
  const [skill, setSkill] = useState("code-qa");
  const [artifact, setArtifact] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [handoffs, setHandoffs] = useState([]);

  const allowedSkills = useMemo(() => (role ? SKILLS_BY_ROLE[role] || [] : []), [role]);

  function selectRole(nextRole) {
    setRole(nextRole);
    const nextSkill = SKILLS_BY_ROLE[nextRole][0];
    setSkill(nextSkill);
    setStep("analyze");
    setHistoryOpen(false);
  }

  async function loadArtifact(taskId) {
    const nextArtifact = await api(`/api/artifacts/${taskId}`);
    setArtifact(nextArtifact);
    setRole((nextArtifact.agent || "").replace(/-agent$/, "") || role);
    setStep("report");
    setHistoryOpen(false);
    if (nextArtifact.skill === "impact-analysis") {
      loadHandoffs(nextArtifact.task_id);
    } else {
      setHandoffs([]);
    }
  }

  async function loadHistory() {
    const items = await api("/api/artifacts");
    setHistory(items);
    setHistoryOpen(true);
    setStep("history");
  }

  async function loadHandoffs(taskId) {
    if (!taskId) {
      setHandoffs([]);
      return;
    }
    try {
      const items = await api(`/api/artifacts/${taskId}/external-handoffs`);
      setHandoffs(items);
    } catch {
      setHandoffs([]);
    }
  }

  async function markReviewed() {
    if (!artifact) return;
    const nextArtifact = await api(`/api/artifacts/${artifact.task_id}/review`, { method: "PATCH" });
    setArtifact(nextArtifact);
    if (nextArtifact.skill === "impact-analysis") {
      loadHandoffs(nextArtifact.task_id);
    }
  }

  return (
    <>
      <WorkflowRail step={step} />
      <main className="workspace">
        <div className="legacy-toolbar">
          <button className="btn ghost compact" type="button" onClick={loadHistory}>
            History
          </button>
        </div>
        {step === "role" && <RoleScreen onSelect={selectRole} />}
        {step === "analyze" && (
          <AnalyzeScreen
            role={role}
            skill={skill}
            allowedSkills={allowedSkills}
            onSkill={setSkill}
            onArtifact={(nextArtifact) => {
              setArtifact(nextArtifact);
              setStep("report");
              if (nextArtifact.skill === "impact-analysis") {
                loadHandoffs(nextArtifact.task_id);
              }
            }}
            onChangeRole={() => setStep("role")}
          />
        )}
        {step === "report" && artifact && (
          <ReportScreen
            artifact={artifact}
            role={role}
            handoffs={handoffs}
            onReviewed={markReviewed}
            onRunAnother={() => setStep("analyze")}
            onChangeRole={() => setStep("role")}
            onArtifact={(nextArtifact) => {
              setArtifact(nextArtifact);
              setStep("report");
              if (nextArtifact.skill === "impact-analysis") {
                loadHandoffs(nextArtifact.task_id);
              } else {
                setHandoffs([]);
              }
            }}
            onReloadHandoffs={() => loadHandoffs(artifact.task_id)}
          />
        )}
        {historyOpen && (
          <HistoryScreen
            items={history}
            onBack={() => setStep(role ? "analyze" : "role")}
            onOpen={loadArtifact}
          />
        )}
      </main>
    </>
  );
}

function ArchitectureDiagramScreen({ onBack }) {
  const [svg, setSvg] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const response = await api("/api/workspace/current/diagram");
        const { svg: rendered } = await mermaid.render("architecture-diagram", response.mermaid);
        if (!cancelled) setSvg(rendered);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main className="diagram-screen">
      <div className="diagram-toolbar">
        <div>
          <h2>Architecture Diagram</h2>
          <p className="muted-note">Packages and call direction from the codebase-memory graph — a fast way to get oriented in a newly connected project.</p>
        </div>
        <button className="btn ghost compact" type="button" onClick={onBack}>
          Back to workbench
        </button>
      </div>
      {loading && <p className="muted-note">Generating diagram…</p>}
      {error && <ErrorBox message={error} />}
      {!loading && !error && svg && <div className="diagram-canvas" dangerouslySetInnerHTML={{ __html: svg }} />}
    </main>
  );
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function buildArchifySequenceHtml({ workspaceName, endpoint, summary, engine, graphReady, sequenceSvg }) {
  if (!sequenceSvg) return "";
  const route = escapeHtml(summary?.route || "No endpoint selected");
  const handler = escapeHtml(summary?.handler || "No handler selected");
  const framework = escapeHtml(summary?.framework || "Unknown");
  const analysis = escapeHtml(summary?.analysis || "Route scan");
  const project = escapeHtml(workspaceName || "Connected project");
  const source = escapeHtml(endpoint?.route_file || "Source not available");
  const engineLabel = engine === "graphify" ? "Graphify Deep Flow" : "Basic Scanner";
  const evidenceLabel = engine === "graphify" && graphReady ? "Controller dependency graph" : "Route and handler scan";
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Archify Endpoint Flow - ${route}</title>
  <style>
    :root {
      color-scheme: light;
      --ink: #12202a;
      --muted: #667684;
      --line: #d9e2e8;
      --paper: #fbfaf6;
      --panel: #ffffff;
      --slate: #17242d;
      --teal: #276b67;
      --blue: #4b6f92;
      --amber: #d4a94c;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      color: var(--ink);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background:
        linear-gradient(rgba(18, 32, 42, 0.035) 1px, transparent 1px),
        linear-gradient(90deg, rgba(18, 32, 42, 0.03) 1px, transparent 1px),
        linear-gradient(135deg, #f7fafb 0%, #eef4f5 100%);
      background-size: 36px 36px, 36px 36px, auto;
      padding: 22px;
    }
    .shell { max-width: 1280px; margin: 0 auto; display: grid; gap: 18px; }
    .hero {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 18px;
      align-items: end;
      padding: 20px;
      border: 1px solid rgba(18, 32, 42, 0.12);
      border-radius: 24px;
      background: linear-gradient(135deg, #111c23 0%, #172b33 70%, #24343c 100%);
      color: #f7fbfb;
      box-shadow: 0 24px 54px rgba(20, 31, 40, 0.18);
    }
    .kicker {
      width: fit-content;
      margin-bottom: 10px;
      border: 1px solid rgba(255, 255, 255, 0.16);
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.08);
      padding: 6px 10px;
      color: rgba(247, 251, 251, 0.78);
      font-size: 11px;
      font-weight: 850;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    h1 { margin: 0; font-size: clamp(24px, 3vw, 38px); line-height: 1.08; }
    .hero p { margin: 10px 0 0; color: rgba(247, 251, 251, 0.7); line-height: 1.55; }
    .badge-grid { display: grid; grid-template-columns: repeat(2, minmax(120px, 1fr)); gap: 10px; }
    .badge {
      min-width: 130px;
      border: 1px solid rgba(255, 255, 255, 0.13);
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.075);
      padding: 12px;
    }
    .badge span { display: block; color: rgba(247, 251, 251, 0.58); font-size: 10px; font-weight: 850; letter-spacing: 0.08em; text-transform: uppercase; }
    .badge strong { display: block; margin-top: 4px; color: #ffffff; font-size: 16px; overflow-wrap: anywhere; }
    .insight {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 12px;
    }
    .card {
      border: 1px solid var(--line);
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.92);
      padding: 14px;
      box-shadow: 0 12px 28px rgba(24, 43, 52, 0.07);
    }
    .card span { display: block; color: var(--muted); font-size: 10px; font-weight: 850; letter-spacing: 0.08em; text-transform: uppercase; }
    .card strong { display: block; margin-top: 6px; font-size: 14px; overflow-wrap: anywhere; }
    .canvas {
      overflow: auto;
      border: 1px solid rgba(18, 32, 42, 0.14);
      border-radius: 22px;
      background: linear-gradient(135deg, #101820, #17242d);
      padding: 18px;
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.035), 0 18px 42px rgba(18, 32, 42, 0.16);
    }
    .paper {
      width: max-content;
      min-width: 100%;
      border: 1px solid rgba(255, 255, 255, 0.16);
      border-radius: 18px;
      background:
        linear-gradient(rgba(18, 32, 42, 0.035) 1px, transparent 1px),
        linear-gradient(90deg, rgba(18, 32, 42, 0.03) 1px, transparent 1px),
        var(--paper);
      background-size: 34px 34px, 34px 34px, auto;
      padding: 18px;
    }
    svg { max-width: none; min-width: 760px; }
    svg text, svg .messageText, svg .labelText, svg .loopText, svg .noteText {
      fill: var(--ink) !important;
      color: var(--ink) !important;
      font-weight: 650 !important;
    }
    svg .actor, svg .actorBox, svg rect.actor {
      fill: #eef8f7 !important;
      stroke: var(--teal) !important;
      stroke-width: 1.4px !important;
    }
    svg .messageLine0, svg .messageLine1, svg .messageLine2,
    svg path.messageLine0, svg path.messageLine1, svg path.messageLine2 {
      stroke: var(--teal) !important;
      stroke-width: 1.7px !important;
    }
    svg .activation0, svg .activation1, svg .activation2,
    svg rect.activation0, svg rect.activation1, svg rect.activation2 {
      fill: #d7e8f4 !important;
      stroke: var(--blue) !important;
    }
    svg .note, svg .noteBox, svg rect.note {
      fill: #fff2cf !important;
      stroke: var(--amber) !important;
    }
    .footer-note {
      border: 1px solid var(--line);
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.88);
      padding: 12px 14px;
      color: var(--muted);
      line-height: 1.5;
    }
    @media (max-width: 900px) {
      body { padding: 12px; }
      .hero, .insight { grid-template-columns: 1fr; }
      .badge-grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <main class="shell">
    <section class="hero">
      <div>
        <div class="kicker">Archify endpoint flow</div>
        <h1>${route}</h1>
        <p>${project} - generated from ${escapeHtml(engineLabel)} so an analyst can inspect the request path before impact or testing work.</p>
      </div>
      <div class="badge-grid">
        <div class="badge"><span>Framework</span><strong>${framework}</strong></div>
        <div class="badge"><span>Evidence</span><strong>${escapeHtml(evidenceLabel)}</strong></div>
      </div>
    </section>
    <section class="insight">
      <div class="card"><span>Endpoint</span><strong>${route}</strong></div>
      <div class="card"><span>Handler</span><strong>${handler}</strong></div>
      <div class="card"><span>Analysis</span><strong>${analysis}</strong></div>
      <div class="card"><span>Source</span><strong>${source}</strong></div>
    </section>
    <section class="canvas">
      <div class="paper">${sequenceSvg}</div>
    </section>
    <section class="footer-note">
      This Archify-style view is a presentation layer over the current project evidence. Use it for onboarding, review discussion, and handoff explanation; verify unclear dependencies with Repo AI or the code owner.
    </section>
  </main>
</body>
</html>`;
}

function ProjectOverviewScreen({ workspace, onWorkspaceUpdated, onBack, onSwitchProject }) {
  const [activeMap, setActiveMap] = useState("sequence");
  const [svg, setSvg] = useState("");
  const [sequenceSvg, setSequenceSvg] = useState("");
  const [endpoints, setEndpoints] = useState([]);
  const [selectedEndpointId, setSelectedEndpointId] = useState("");
  const [sequenceEngine, setSequenceEngine] = useState("scanner");
  const [loading, setLoading] = useState(true);
  const [sequenceLoading, setSequenceLoading] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [graphifyIndexing, setGraphifyIndexing] = useState(false);
  const [error, setError] = useState("");
  const [sequenceError, setSequenceError] = useState("");
  const [projectNotice, setProjectNotice] = useState("");
  const [endpointAiOpen, setEndpointAiOpen] = useState(false);
  const [endpointAiQuestion, setEndpointAiQuestion] = useState("");
  const [endpointAiMessages, setEndpointAiMessages] = useState([]);
  const [endpointAiLoading, setEndpointAiLoading] = useState(false);
  const [endpointAiError, setEndpointAiError] = useState("");
  const [graphifySubfolders, setGraphifySubfolders] = useState([]);
  const [selectedGraphifySubfolder, setSelectedGraphifySubfolder] = useState("");
  const [diagramScale, setDiagramScale] = useState(1);
  const [diagramExpanded, setDiagramExpanded] = useState(false);
  const [diagramCopyStatus, setDiagramCopyStatus] = useState("");
  const [sequenceView, setSequenceView] = useState("diagram");

  const [subpaths, setSubpaths] = useState([]);
  const [selectedSubpathId, setSelectedSubpathId] = useState("");
  const [subpathIndexing, setSubpathIndexing] = useState(false);
  const [crossReferenceMode, setCrossReferenceMode] = useState(false);
  const [crossReferenceLoading, setCrossReferenceLoading] = useState(false);
  const [crossReferenceError, setCrossReferenceError] = useState("");

  const selectedSubpath = subpaths.find((sp) => sp.id === selectedSubpathId) || null;

  // Named sub-folders (e.g. "Frontend"/"Backend"/"Admin console") the analyst
  // defined for this project in the Project Control Center switchboard --
  // lets the diagram/endpoints below target one of them instead of only
  // ever reading workspace.local_path as a single codebase.
  useEffect(() => {
    if (!workspace?.id) {
      setSubpaths([]);
      setSelectedSubpathId("");
      return;
    }
    let cancelled = false;
    api(`/api/workspace/${workspace.id}/subpaths`)
      .then((rows) => {
        if (!cancelled) setSubpaths(rows);
      })
      .catch(() => {
        if (!cancelled) setSubpaths([]);
      });
    setSelectedSubpathId("");
    return () => {
      cancelled = true;
    };
  }, [workspace?.id]);

  useEffect(() => {
    if (selectedSubpath && selectedSubpath.index_status !== "ready") {
      setSvg("");
      setLoading(false);
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const query = selectedSubpathId ? `?subpath_id=${encodeURIComponent(selectedSubpathId)}` : "";
        const response = await api(`/api/workspace/current/diagram${query}`);
        const { svg: rendered } = await mermaid.render("project-overview-diagram", response.mermaid);
        if (!cancelled) setSvg(rendered);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [workspace?.id, workspace?.index_status, selectedSubpathId, selectedSubpath?.index_status]);

  useEffect(() => {
    let cancelled = false;
    async function loadEndpoints() {
      try {
        const query = selectedSubpathId ? `?subpath_id=${encodeURIComponent(selectedSubpathId)}` : "";
        const items = await api(`/api/workspace/current/endpoints${query}`);
        if (cancelled) return;
        setEndpoints(items);
        setSelectedEndpointId(items[0]?.id || "");
      } catch {
        if (!cancelled) setEndpoints([]);
      }
    }
    loadEndpoints();
    return () => {
      cancelled = true;
    };
  }, [workspace?.id, selectedSubpathId]);

  // Both the normal per-endpoint diagram load below and the cross-reference
  // action write to the same sequenceSvg -- a shared "latest request wins"
  // counter keeps a slow cross-reference fetch from clobbering a diagram the
  // analyst already navigated away from (and vice versa), same intent as the
  // effect's own cancelled-flag pattern, just shared across both call sites.
  const sequenceRequestIdRef = useRef(0);

  async function loadPlainSequence() {
    const requestId = ++sequenceRequestIdRef.current;
    if (!selectedEndpointId) {
      setSequenceSvg("");
      return;
    }
    const endpoint = endpoints.find((item) => item.id === selectedEndpointId);
    const requiresGraphify = sequenceEngine === "graphify" && endpoint?.framework !== "frontend";
    if (requiresGraphify && workspace?.graphify_index_status !== "ready") {
      setSequenceSvg("");
      setSequenceError("");
      setSequenceLoading(false);
      return;
    }
    setSequenceLoading(true);
    setSequenceError("");
    try {
      const subpathParam = selectedSubpathId ? `&subpath_id=${encodeURIComponent(selectedSubpathId)}` : "";
      const response = await api(
        `/api/workspace/current/endpoints/sequence?endpointId=${encodeURIComponent(selectedEndpointId)}&engine=${sequenceEngine}${subpathParam}`
      );
      const { svg: rendered } = await mermaid.render(`endpoint-sequence-${Date.now()}`, response.mermaid);
      if (requestId === sequenceRequestIdRef.current) setSequenceSvg(rendered);
    } catch (err) {
      if (requestId === sequenceRequestIdRef.current) setSequenceError(err.message);
    } finally {
      if (requestId === sequenceRequestIdRef.current) setSequenceLoading(false);
    }
  }

  useEffect(() => {
    setCrossReferenceMode(false);
    setCrossReferenceError("");
    loadPlainSequence();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEndpointId, sequenceEngine, workspace?.graphify_index_status, endpoints, selectedSubpathId]);

  async function indexSelectedSubpath() {
    if (!workspace?.id || !selectedSubpathId) return;
    setSubpathIndexing(true);
    try {
      await api(`/api/workspace/${workspace.id}/subpaths/${selectedSubpathId}/index`, { method: "POST" });
      const rows = await api(`/api/workspace/${workspace.id}/subpaths`);
      setSubpaths(rows);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubpathIndexing(false);
    }
  }

  // Works in both directions -- the backend resolves whether the selected
  // endpoint is frontend or backend and cross-references against whichever
  // other sub-path has the opposite kind.
  async function runCrossReference() {
    if (!selectedEndpointId) return;
    const requestId = ++sequenceRequestIdRef.current;
    setCrossReferenceLoading(true);
    setCrossReferenceError("");
    try {
      const subpathParam = selectedSubpathId ? `&subpath_id=${encodeURIComponent(selectedSubpathId)}` : "";
      const response = await api(
        `/api/workspace/current/endpoints/cross-reference?endpoint_id=${encodeURIComponent(selectedEndpointId)}${subpathParam}`
      );
      const { svg: rendered } = await mermaid.render(`endpoint-cross-ref-${Date.now()}`, response.mermaid);
      if (requestId === sequenceRequestIdRef.current) {
        setSequenceSvg(rendered);
        setCrossReferenceMode(true);
      }
    } catch (err) {
      if (requestId === sequenceRequestIdRef.current) setCrossReferenceError(err.message);
    } finally {
      if (requestId === sequenceRequestIdRef.current) setCrossReferenceLoading(false);
    }
  }

  function backToOwnView() {
    setCrossReferenceMode(false);
    setCrossReferenceError("");
    loadPlainSequence();
  }

  useEffect(() => {
    setEndpointAiMessages([]);
    setEndpointAiQuestion("");
    setEndpointAiError("");
  }, [selectedEndpointId, workspace?.id]);

  useEffect(() => {
    setDiagramScale(1);
    setDiagramCopyStatus("");
  }, [activeMap, selectedEndpointId, sequenceEngine, sequenceView, workspace?.id]);

  // Graphify can't index local_path directly when it's a repo root holding
  // several sub-projects (e.g. frontend + backend) rather than one
  // indexable codebase -- offer the immediate subfolders so the analyst
  // (who knows which one is actually frontend/backend) can pick.
  useEffect(() => {
    if (workspace?.graphify_index_status !== "failed" || !workspace?.local_path) {
      setGraphifySubfolders([]);
      return;
    }
    let cancelled = false;
    api(`/api/workspace/browse?path=${encodeURIComponent(workspace.local_path)}`)
      .then((result) => {
        if (!cancelled) setGraphifySubfolders(result.entries || []);
      })
      .catch(() => {
        if (!cancelled) setGraphifySubfolders([]);
      });
    return () => {
      cancelled = true;
    };
  }, [workspace?.graphify_index_status, workspace?.local_path]);

  async function graphifyIndexSubfolder() {
    if (!selectedGraphifySubfolder) {
      setProjectNotice("Pick a subfolder first.");
      return;
    }
    setGraphifyIndexing(true);
    setSequenceError("");
    setProjectNotice("");
    try {
      const next = await api("/api/workspace/current/graphify-index-path", {
        method: "POST",
        body: { path: selectedGraphifySubfolder },
      });
      onWorkspaceUpdated?.(next);
      setProjectNotice(`Graphify indexing started for ${selectedGraphifySubfolder}.`);
    } catch (err) {
      setProjectNotice(err.message);
    } finally {
      setGraphifyIndexing(false);
    }
  }

  async function reindexProject() {
    setReindexing(true);
    setError("");
    setProjectNotice("");
    try {
      const next = await api("/api/workspace/current/reindex", { method: "POST" });
      onWorkspaceUpdated?.(next);
      setProjectNotice("Indexing started. The project status will refresh automatically.");
    } catch (err) {
      setProjectNotice(err.message);
    } finally {
      setReindexing(false);
    }
  }

  async function graphifyIndexProject() {
    if (workspace?.graphify_index_status === "ready") {
      setProjectNotice("Graphify index already exists. Deep Flow can use the cached graph without re-indexing.");
      return;
    }
    setGraphifyIndexing(true);
    setSequenceError("");
    setProjectNotice("");
    try {
      const next = await api("/api/workspace/current/graphify-index", { method: "POST" });
      onWorkspaceUpdated?.(next);
      setProjectNotice("Graphify indexing started. When it is ready, use Graphify Deep Flow for endpoint diagrams.");
    } catch (err) {
      setProjectNotice(err.message);
    } finally {
      setGraphifyIndexing(false);
    }
  }

  const selectedEndpoint = endpoints.find((item) => item.id === selectedEndpointId);
  const endpointAiPrompts = [
    "Explain this endpoint in simple analyst language.",
    "What upstream and downstream areas should I check?",
    "What risks or test scenarios should I prepare?",
  ];

  async function askEndpointAi(question = endpointAiQuestion) {
    const normalized = question.trim();
    if (!normalized) {
      setEndpointAiError("Ask a question about the selected endpoint first.");
      return;
    }
    const endpointContext = selectedEndpoint
      ? [
          `Selected endpoint: ${selectedEndpoint.method} ${selectedEndpoint.path}`,
          `Framework: ${selectedEndpoint.framework}`,
          `Handler: ${selectedEndpoint.controller}@${selectedEndpoint.action}`,
          `Source file: ${selectedEndpoint.route_file}`,
          `Diagram engine: ${sequenceEngine}`,
        ].join("\n")
      : "No endpoint is selected. Answer using the connected project graph only.";
    const groundedQuestion = [
      "You are helping a Software Analyst understand a project endpoint from the Project Overview diagram.",
      endpointContext,
      "Answer in simple analyst language. Focus on business purpose, likely upstream/downstream modules, impact risk, testing focus, and unknowns that need developer confirmation.",
      `Analyst question: ${normalized}`,
    ].join("\n\n");
    const userMessage = { id: `endpoint-user-${Date.now()}`, role: "user", question: normalized };
    setEndpointAiOpen(true);
    setEndpointAiError("");
    setEndpointAiLoading(true);
    setEndpointAiMessages((prev) => [...prev, userMessage]);
    try {
      const artifact = await api("/api/skills/code-qa", {
        method: "POST",
        body: { profile: ANALYST_PROFILE, question: groundedQuestion },
      });
      setEndpointAiMessages((prev) => [
        ...prev,
        {
          id: artifact.task_id || `endpoint-ai-${Date.now()}`,
          role: "assistant",
          question: normalized,
          artifact,
          result: artifact.result || {},
        },
      ]);
      setEndpointAiQuestion("");
    } catch (err) {
      setEndpointAiError(err.message);
    } finally {
      setEndpointAiLoading(false);
    }
  }

  const codeGraphReady = workspace?.index_status === "ready";
  const graphifyReady = workspace?.graphify_index_status === "ready";
  const endpointMethodCounts = endpoints.reduce((counts, endpoint) => {
    const method = endpoint.method || "API";
    counts[method] = (counts[method] || 0) + 1;
    return counts;
  }, {});
  const endpointSummary = selectedEndpoint
    ? {
        route: `${selectedEndpoint.method} ${selectedEndpoint.path}`,
        handler: `${selectedEndpoint.controller}@${selectedEndpoint.action}`,
        framework: selectedEndpoint.framework,
        analysis: sequenceEngine === "graphify" && graphifyReady ? "Deep graph" : "Route scan",
      }
    : {
        route: "Select an endpoint",
        handler: "No handler selected",
        framework: "Unknown",
        analysis: "Waiting",
      };
  const currentDiagramSvg = activeMap === "sequence" ? sequenceSvg : svg;
  const currentDiagramClass = activeMap === "sequence" ? "sequence-canvas" : "architecture-canvas";
  const diagramScalePercent = Math.round(diagramScale * 100);
  const archifyHtml = useMemo(
    () =>
      buildArchifySequenceHtml({
        workspaceName: workspace?.name || "Connected project",
        endpoint: selectedEndpoint,
        summary: endpointSummary,
        engine: sequenceEngine,
        graphReady: graphifyReady,
        sequenceSvg,
      }),
    [workspace?.name, selectedEndpoint, endpointSummary, sequenceEngine, graphifyReady, sequenceSvg],
  );

  function zoomDiagram(delta) {
    setDiagramScale((current) => Math.min(1.8, Math.max(0.75, Number((current + delta).toFixed(2)))));
  }

  async function copyCurrentDiagram() {
    const isArchifyView = activeMap === "sequence" && sequenceView === "archify";
    const contentToCopy = isArchifyView ? archifyHtml : currentDiagramSvg;
    if (!contentToCopy) {
      setDiagramCopyStatus("No diagram to copy");
      return;
    }
    setDiagramCopyStatus("");
    try {
      if (!isArchifyView && navigator.clipboard && window.ClipboardItem) {
        const blob = new Blob([currentDiagramSvg], { type: "image/svg+xml" });
        await navigator.clipboard.write([new window.ClipboardItem({ "image/svg+xml": blob })]);
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(contentToCopy);
      } else {
        throw new Error("Clipboard is not available in this browser.");
      }
      setDiagramCopyStatus(isArchifyView ? "Copied HTML" : "Copied");
    } catch (err) {
      try {
        await navigator.clipboard.writeText(contentToCopy);
        setDiagramCopyStatus(isArchifyView ? "Copied HTML" : "Copied SVG code");
      } catch {
        setDiagramCopyStatus(err.message || "Copy failed");
      }
    }
  }

  function renderDiagram(svgMarkup, extraClass = "") {
    return (
      <div className={`diagram-canvas ${extraClass} diagram-zoomable`}>
        <div
          className="diagram-zoom-stage"
          style={{ "--diagram-scale": diagramScale }}
          dangerouslySetInnerHTML={{ __html: svgMarkup }}
        />
      </div>
    );
  }

  function renderArchifyView() {
    return (
      <div className="archify-view-shell">
        <div className="archify-view-head">
          <div>
            <span className="source-pill">Archify View</span>
            <strong>Presentation-ready endpoint flow</strong>
            <p>Uses the current Scanner / Graphify sequence output, wrapped as a self-contained Archify-style HTML view.</p>
          </div>
          <button className="btn ghost compact" type="button" onClick={copyCurrentDiagram} disabled={!archifyHtml}>
            Copy HTML
          </button>
        </div>
        <iframe title="Archify endpoint flow" className="archify-preview-frame" srcDoc={archifyHtml} />
      </div>
    );
  }

  const understandingSteps = [
    {
      step: "1",
      title: "Repo",
      detail: workspace?.local_path || "Connect a local repo so the platform can read project evidence.",
      state: codeGraphReady ? "Ready" : "Needs index",
    },
    {
      step: "2",
      title: "Endpoint",
      detail: endpoints.length > 0 ? `${endpoints.length} endpoint or frontend API flow(s) found.` : "No endpoint flow found yet.",
      state: endpoints.length > 0 ? "Mapped" : "Needs scan",
    },
    {
      step: "3",
      title: "Flow",
      detail: selectedEndpoint
        ? `${selectedEndpoint.method} ${selectedEndpoint.path} -> ${selectedEndpoint.controller}`
        : "Select an endpoint to see the request, controller, dependencies, and response.",
      state: selectedEndpoint ? "Selected" : "Waiting",
    },
    {
      step: "4",
      title: "Verify",
      detail: "Use the diagram as evidence, then ask Repo AI or the code owner when the flow is incomplete.",
      state: "Human review",
    },
  ];
  const understandingTools = [
    {
      name: "Codebase Memory MCP",
      status: codeGraphReady ? "Ready" : "Index needed",
      detail: "Architecture map, impact context, and Repo AI grounding.",
    },
    {
      name: "Basic Scanner",
      status: endpoints.length > 0 ? "Ready" : "No routes",
      detail: "Extracts Laravel/Spring routes and frontend api() calls.",
    },
    {
      name: "Graphify Deep Flow",
      status: graphifyReady ? "Ready" : "Optional",
      detail: "Adds controller dependency flow when the graphify index exists.",
    },
  ];

  return (
    <main className="diagram-screen project-overview-screen">
      <div className="diagram-toolbar project-overview-toolbar">
        <div>
          <div className="eyebrow">Project onboarding</div>
          <h2>Project Overview</h2>
          <p className="muted-note">
            Start here when an analyst joins a project. The system map is generated from the connected repo so the
            analyst can understand major modules before reviewing tickets.
          </p>
        </div>
        <div className="diagram-toolbar-actions">
          <button className="btn ghost compact" type="button" onClick={onSwitchProject}>
            Switch project
          </button>
          <button className="btn primary compact" type="button" onClick={onBack}>
            Start workbench
          </button>
        </div>
      </div>
      <section className="project-overview-header-card">
        <div className="project-overview-summary">
          <span className="source-pill">Connected project</span>
          <div>
            <h3>{workspace?.name || "No project connected"}</h3>
            <p>
              {workspace?.local_path ||
                "Connect a local repo first so impact analysis and onboarding diagrams use real codebase evidence."}
            </p>
          </div>
        </div>
        <div className="project-overview-status-row">
          <span className={`index-status-tag ${workspace?.index_status || "not_indexed"}`}>
            {indexStatusLabel(workspace?.index_status, workspace?.index_error)}
          </span>
          <span className={`index-status-tag ${workspace?.graphify_index_status || "not_indexed"}`}>
            Graphify: {indexStatusLabel(workspace?.graphify_index_status, workspace?.graphify_index_error)}
          </span>
        </div>
        <div className="project-overview-header-actions">
          <button className="btn ghost compact" type="button" disabled={reindexing} onClick={reindexProject}>
            {reindexing ? "Indexing..." : "Re-index project"}
          </button>
          <button
            className="btn ghost compact"
            type="button"
            disabled={graphifyIndexing || workspace?.graphify_index_status === "indexing" || workspace?.graphify_index_status === "ready"}
            onClick={graphifyIndexProject}
          >
            {workspace?.graphify_index_status === "ready"
              ? "Graphify ready"
              : graphifyIndexing || workspace?.graphify_index_status === "indexing"
                ? "Running Graphify..."
                : "Run Graphify index"}
          </button>
        </div>
        {workspace?.graphify_index_status === "failed" && graphifySubfolders.length > 0 && (
          <div className="graphify-subfolder-picker">
            <p className="muted-note">
              {workspace.name} isn't a single indexable codebase (it holds sub-projects). Pick which one to index for
              the diagram:
            </p>
            <div className="path-input-row">
              <select value={selectedGraphifySubfolder} onChange={(event) => setSelectedGraphifySubfolder(event.target.value)}>
                <option value="">Select a subfolder…</option>
                {graphifySubfolders.map((entry) => (
                  <option key={entry.path} value={entry.path}>
                    {entry.name}
                    {entry.git_repo ? " (git repo)" : ""}
                  </option>
                ))}
              </select>
              <button
                className="btn primary compact"
                type="button"
                disabled={graphifyIndexing || !selectedGraphifySubfolder}
                onClick={graphifyIndexSubfolder}
              >
                Index this folder
              </button>
            </div>
          </div>
        )}
      </section>
      {projectNotice && <div className="project-overview-notice info-box">{projectNotice}</div>}
      <section className="project-understanding-center">
        <div className="understanding-center-head">
          <div>
            <span className="source-pill">Project Understanding Center</span>
            <h3>Repo onboarding flow</h3>
            <p>
              Start with the connected repo, choose an endpoint, read the flow, then use AI to confirm business and
              testing questions before ticket analysis.
            </p>
          </div>
          <div className="understanding-health">
            <span>
              <strong>{endpoints.length}</strong>
              Flows found
            </span>
            <span>
              <strong>{codeGraphReady ? "Ready" : "Setup"}</strong>
              Repo graph
            </span>
            <span>
              <strong>{graphifyReady ? "Ready" : "Optional"}</strong>
              Deep flow
            </span>
          </div>
        </div>
        <div className="overview-flow-rail" role="list" aria-label="Repo onboarding flow">
          {understandingSteps.map((item, index) => (
            <div
              key={item.step}
              className={`overview-flow-step ${index === understandingSteps.length - 1 ? "last" : ""}`}
              role="listitem"
              title={item.detail}
            >
              <span className="overview-flow-marker">{item.step}</span>
              <div className="overview-flow-copy">
                <strong>{item.title}</strong>
                <small>{item.state}</small>
              </div>
            </div>
          ))}
        </div>
        <div className="overview-disclosure-row">
          <details className="overview-detail-card">
            <summary>
              <span>Evidence pipeline</span>
              <strong>Codebase Memory, scanner, and Graphify status</strong>
            </summary>
            <div className="understanding-tool-grid">
              {understandingTools.map((tool) => (
                <article key={tool.name}>
                  <div>
                    <strong>{tool.name}</strong>
                    <span>{tool.status}</span>
                  </div>
                  <p>{tool.detail}</p>
                </article>
              ))}
            </div>
          </details>
          <details className="overview-detail-card">
            <summary>
              <span>Ask AI</span>
              <strong>Suggested questions for this endpoint</strong>
            </summary>
            <div className="understanding-question-strip">
              <button type="button" onClick={() => askEndpointAi("Which endpoint starts this flow?")}>
                Which endpoint starts this flow?
              </button>
              <button type="button" onClick={() => askEndpointAi("What models or tables are affected?")}>
                What models are affected?
              </button>
              <button type="button" onClick={() => askEndpointAi("Explain this controller in simple analyst language.")}>
                Explain this controller
              </button>
              <button type="button" onClick={() => askEndpointAi("What tests should be prepared for this endpoint?")}>
                Generate test focus
              </button>
            </div>
          </details>
        </div>
      </section>
      <section className="project-overview-grid">
        <section className="project-map-panel">
          <div className="project-map-head">
            <div>
              <span className="source-pill">{activeMap === "sequence" ? "Endpoint flow" : "System map"}</span>
              <h3>{activeMap === "sequence" ? "Endpoint sequence diagram" : "Architecture diagram"}</h3>
            </div>
            <div className="project-map-head-actions">
              <span className="muted-note diagram-source-note">
                {activeMap === "sequence"
                  ? selectedEndpoint?.framework === "frontend"
                    ? "Generated from frontend api() calls and mapped backend API URLs"
                    : sequenceEngine === "graphify"
                    ? "Generated from  routes plus Graphify extracted graph"
                    : "Generated from  routes, controllers, and frontend API calls"
                  : "Generated by codebase-memory MCP"}
              </span>
              <button
                className={`diagram-ai-launcher ${endpointAiOpen ? "active" : ""}`}
                type="button"
                onClick={() => setEndpointAiOpen((open) => !open)}>
                <span>AI</span>
                <strong>{endpointAiOpen ? "Hide assistant" : "Ask endpoint AI"}</strong>
              </button>
              <div className="diagram-view-actions" aria-label="Diagram viewing tools">
                <button type="button" onClick={() => zoomDiagram(-0.15)} disabled={!currentDiagramSvg || diagramScale <= 0.75}>
                  -
                </button>
                <span>{diagramScalePercent}%</span>
                <button type="button" onClick={() => zoomDiagram(0.15)} disabled={!currentDiagramSvg || diagramScale >= 1.8}>
                  +
                </button>
                <button type="button" onClick={() => setDiagramScale(1)} disabled={!currentDiagramSvg || diagramScale === 1}>
                  Reset
                </button>
                <button type="button" onClick={() => setDiagramExpanded(true)} disabled={!currentDiagramSvg}>
                  Expand
                </button>
                <button type="button" onClick={copyCurrentDiagram} disabled={!currentDiagramSvg}>
                  Copy diagram
                </button>
              </div>
              {diagramCopyStatus && <span className="diagram-copy-status">{diagramCopyStatus}</span>}
            </div>
          </div>
          {subpaths.length > 0 && (
            <div className="project-scope-picker">
              <label className="field-label" htmlFor="subpath-scope-select">
                Scope
              </label>
              <select
                id="subpath-scope-select"
                value={selectedSubpathId}
                onChange={(event) => setSelectedSubpathId(event.target.value)}
              >
                <option value="">Whole project</option>
                {subpaths.map((sp) => (
                  <option key={sp.id} value={sp.id}>
                    {sp.label}
                  </option>
                ))}
              </select>
              {selectedSubpath && (
                <span className={`index-status-tag ${selectedSubpath.index_status || "not_indexed"}`}>
                  {indexStatusLabel(selectedSubpath.index_status, selectedSubpath.index_error)}
                </span>
              )}
              {selectedSubpath && selectedSubpath.index_status !== "ready" && activeMap !== "sequence" && (
                <button className="btn primary compact" type="button" disabled={subpathIndexing} onClick={indexSelectedSubpath}>
                  {subpathIndexing || selectedSubpath.index_status === "indexing" ? "Indexing..." : "Index this sub-path"}
                </button>
              )}
            </div>
          )}
          {activeMap === "sequence" && (
            <div className="overview-toolbar">
              <div className="overview-toolbar-endpoint">
                <label className="field-label" htmlFor="endpoint-select">
                  Select endpoint
                </label>
                <select
                  id="endpoint-select"
                  value={selectedEndpointId}
                  onChange={(event) => setSelectedEndpointId(event.target.value)}
                >
                  {endpoints.map((endpoint) => (
                    <option key={endpoint.id} value={endpoint.id}>
                      [{endpoint.framework}] {endpoint.method} {endpoint.path} - {endpoint.controller}@{endpoint.action}
                    </option>
                  ))}
                </select>
                {selectedEndpoint && (
                  <span className={`endpoint-framework-badge ${selectedEndpoint.framework === "frontend" ? "tone-blue" : "tone-teal"}`}>
                    {selectedEndpoint.framework === "frontend" ? "Frontend" : "Backend"}
                  </span>
                )}
                {selectedEndpoint && subpaths.length > 0 && (
                  <button
                    className="btn ghost compact"
                    type="button"
                    disabled={crossReferenceLoading}
                    onClick={crossReferenceMode ? backToOwnView : runCrossReference}
                  >
                    {crossReferenceLoading
                      ? "Tracing..."
                      : crossReferenceMode
                        ? `← Back to ${selectedEndpoint.framework === "frontend" ? "frontend" : "backend"} view`
                        : selectedEndpoint.framework === "frontend"
                          ? "See backend endpoint"
                          : "See frontend caller"}
                  </button>
                )}
                <div className="endpoint-method-filter" aria-label="Endpoint method summary">
                  {["GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => (
                    <span key={method} className={endpointMethodCounts[method] ? "active" : ""}>
                      {method} {endpointMethodCounts[method] || 0}
                    </span>
                  ))}
                </div>
              </div>
              <div className="overview-toolbar-segments">
                <div className="overview-segment-group" aria-label="Diagram data source">
                  <button
                    className={sequenceEngine === "scanner" ? "active" : ""}
                    type="button"
                    onClick={() => setSequenceEngine("scanner")}
                  >
                    Basic Scanner
                  </button>
                  <button
                    className={sequenceEngine === "graphify" ? "active" : ""}
                    type="button"
                    onClick={() => setSequenceEngine("graphify")}
                  >
                    Graphify Deep Flow
                  </button>
                </div>
                <div className="overview-segment-group" aria-label="Sequence diagram renderer">
                  <button
                    className={sequenceView === "diagram" ? "active" : ""}
                    type="button"
                    onClick={() => setSequenceView("diagram")}
                  >
                    Mermaid Diagram
                  </button>
                  <button
                    className={sequenceView === "archify" ? "active" : ""}
                    type="button"
                    onClick={() => setSequenceView("archify")}
                  >
                    Archify View
                  </button>
                </div>
              </div>
            </div>
          )}
          <div className="overview-summary-chips">
            <span>
              <b>Endpoint</b>
              {endpointSummary.route}
            </span>
            <span>
              <b>Handler</b>
              {endpointSummary.handler}
            </span>
            <span>
              <b>Framework</b>
              {endpointSummary.framework && (
                <span className={`endpoint-framework-badge ${endpointSummary.framework === "frontend" ? "tone-blue" : "tone-teal"}`}>
                  {endpointSummary.framework === "frontend" ? "Frontend" : endpointSummary.framework}
                </span>
              )}
            </span>
            <span>
              <b>Analysis</b>
              {endpointSummary.analysis}
            </span>
            {activeMap === "sequence" && selectedEndpoint && (
              <span className="overview-summary-source">
                <b>Source</b>
                <code>{selectedEndpoint.route_file}</code>
                {sequenceEngine === "graphify" &&
                  selectedEndpoint.framework !== "frontend" &&
                  " · graphify-out/graph.json"}
              </span>
            )}
          </div>
          {activeMap === "sequence" && (
            <>
              {sequenceEngine === "graphify" &&
                selectedEndpoint?.framework !== "frontend" &&
                workspace?.graphify_index_status !== "ready" && (
                <div className="info-box">
                  Run Graphify index first to generate evidence-based controller dependency flow.
                </div>
              )}
              {endpointAiOpen && (
                <aside className="diagram-ai-panel">
                  <div className="diagram-ai-head">
                    <div className="diagram-ai-mark">AI</div>
                    <div>
                      <span>Diagram Copilot</span>
                      <strong>Ask about this endpoint</strong>
                    </div>
                    <button type="button" onClick={() => setEndpointAiOpen(false)} aria-label="Close endpoint assistant">
                      x
                    </button>
                  </div>
                  <div className="diagram-ai-context">
                    <div>
                      <span>{selectedEndpoint ? `${selectedEndpoint.method} ${selectedEndpoint.path}` : "No endpoint selected"}</span>
                      <strong>{selectedEndpoint ? `${selectedEndpoint.controller}@${selectedEndpoint.action}` : "Select a flow first"}</strong>
                    </div>
                    <small>{sequenceEngine === "graphify" ? "Graphify deep flow" : "Basic route scanner"}</small>
                  </div>
                  <div className="diagram-ai-prompts">
                    {endpointAiPrompts.map((prompt) => (
                      <button key={prompt} type="button" onClick={() => askEndpointAi(prompt)} disabled={endpointAiLoading}>
                        {prompt}
                      </button>
                    ))}
                  </div>
                  <div className="diagram-ai-thread">
                    {endpointAiMessages.length === 0 ? (
                      <div className="diagram-ai-empty">
                        <strong>Use this when the diagram is not enough.</strong>
                        <span>Ask what this endpoint does, what depends on it, or what the analyst should verify.</span>
                      </div>
                    ) : (
                      endpointAiMessages.map((message) =>
                        message.role === "user" ? (
                          <div key={message.id} className="diagram-ai-message user">
                            {message.question}
                          </div>
                        ) : (
                          <div key={message.id} className="diagram-ai-message assistant">
                            <strong>AI explanation</strong>
                            <p>{message.result.answer || "(no answer)"}</p>
                            {(message.result.evidence || []).length > 0 && (
                              <div className="diagram-ai-evidence">
                                {(message.result.evidence || []).slice(0, 4).map((item, index) => (
                                  <span key={`${message.id}-evidence-${index}`}>
                                    {item.source || item.claim || "Evidence"}
                                  </span>
                                ))}
                              </div>
                            )}
                            {(message.result.ungrounded || []).length > 0 && (
                              <div className="diagram-ai-warning">Some claims were not grounded in the project graph.</div>
                            )}
                          </div>
                        )
                      )
                    )}
                    {endpointAiLoading && <div className="diagram-ai-message assistant loading">Reading endpoint evidence...</div>}
                  </div>
                  {endpointAiError && <ErrorBox message={endpointAiError} />}
                  <div className="diagram-ai-input">
                    <textarea
                      value={endpointAiQuestion}
                      onChange={(event) => setEndpointAiQuestion(event.target.value)}
                      placeholder="Ask what this endpoint does, what depends on it, or what to test."
                    />
                    <button className="btn primary compact" type="button" onClick={() => askEndpointAi()} disabled={endpointAiLoading}>
                      Ask AI
                    </button>
                  </div>
                </aside>
              )}
              {endpoints.length === 0 && <ErrorBox message="No supported backend routes or frontend API calls found for this project." />}
              {sequenceLoading && <p className="muted-note">Generating endpoint sequence...</p>}
              {sequenceError && <ErrorBox message={sequenceError} />}
              {crossReferenceError && <ErrorBox message={crossReferenceError} />}
              {crossReferenceMode && (
                <p className="muted-note">
                  {selectedEndpoint?.framework === "frontend"
                    ? "Showing the real backend controller this call hits"
                    : "Showing the real frontend call site that hits this route"}
                  {" — click “← Back” above, or pick a different endpoint, to return to its own view."}
                </p>
              )}
              {!sequenceLoading && !sequenceError && sequenceSvg && (
                sequenceView === "archify" ? renderArchifyView() : renderDiagram(sequenceSvg, "sequence-canvas")
              )}
            </>
          )}
          {activeMap === "architecture" && (
            <>
              {loading && <p className="muted-note">Generating diagram...</p>}
              {error && <ErrorBox message={error} />}
              {!loading && !error && svg && renderDiagram(svg, "architecture-canvas")}
            </>
          )}
        </section>
      </section>
      {diagramExpanded && currentDiagramSvg && (
        <div className="diagram-expanded-backdrop" role="dialog" aria-modal="true" aria-label="Expanded diagram viewer">
          <div className="diagram-expanded-modal">
            <div className="diagram-expanded-head">
              <div>
                <span className="source-pill">{activeMap === "sequence" ? "Endpoint flow" : "System map"}</span>
                <strong>{activeMap === "sequence" ? endpointSummary.route : "Architecture diagram"}</strong>
              </div>
              <div className="diagram-view-actions">
                <button type="button" onClick={() => zoomDiagram(-0.15)} disabled={diagramScale <= 0.75}>
                  -
                </button>
                <span>{diagramScalePercent}%</span>
                <button type="button" onClick={() => zoomDiagram(0.15)} disabled={diagramScale >= 1.8}>
                  +
                </button>
                <button type="button" onClick={() => setDiagramScale(1)}>
                  Reset
                </button>
                <button type="button" onClick={copyCurrentDiagram}>
                  Copy diagram
                </button>
                <button type="button" onClick={() => setDiagramExpanded(false)}>
                  Close
                </button>
              </div>
            </div>
            {renderDiagram(currentDiagramSvg, `${currentDiagramClass} diagram-expanded-canvas`)}
          </div>
        </div>
      )}
    </main>
  );
}

function TopBar({ status, onHome, workspace, onSwitchProject, onViewDiagram }) {
  function quickAction(action) {
    window.dispatchEvent(new CustomEvent(QUICK_ACTION_EVENT, { detail: { action } }));
  }

  return (
    <header className="topbar">
      <div className="brand-mark">
        <LogoMark />
      </div>
      <div>
        <div className="brand-name">Analyst Workbench</div>
        <div className="brand-subtitle">Software Analyst workflow assistant</div>
      </div>
      {onSwitchProject && (
        <button
          className={`active-project-pill ${workspace ? "" : "not-connected"}`}
          type="button"
          onClick={onSwitchProject}
          title="Currently connected project — click to switch"
        >
          <span className="active-project-pill-icon" aria-hidden="true">
            {workspace ? "\u{1F4C1}" : "⚠️"}
          </span>
          <span className="active-project-pill-text">
            <span className="active-project-pill-label">{workspace ? workspace.name : "No project connected"}</span>
            {workspace?.local_path && <span className="active-project-pill-path">{workspace.local_path}</span>}
          </span>
          {workspace?.index_status === "indexing" && <span className="index-status-note">indexing…</span>}
          {workspace?.index_status === "failed" && <span className="index-status-note failed">index failed</span>}
        </button>
      )}
      <div className="topbar-spacer" />
      <nav className="topbar-actions" aria-label="Quick actions">
        <button type="button" onClick={onHome}>
          Home
        </button>
        <button type="button" onClick={() => quickAction("inbox")}>
          Inbox
        </button>
        <button type="button" onClick={() => quickAction("import-jira")}>
          Import Jira
        </button>
        <button type="button" onClick={() => quickAction("manual-intake")}>
          Manual Intake
        </button>
        <button type="button" onClick={() => quickAction("connect-apps")}>
          Connect Apps
        </button>
        {onViewDiagram && (
          <button type="button" onClick={onViewDiagram}>
            Project Overview
          </button>
        )}
      </nav>
      <span className={`connection ${status}`}>
        <span />
        {status === "up" ? "Backend up" : status === "down" ? "Backend down" : "Checking backend"}
      </span>
    </header>
  );
}

/**
 * Brand mark: an "A" (Analyst) monogram whose crossbar doubles as a
 * checkmark — the review-gate/evidence discipline that runs through every
 * skill in this workflow, not just a generic initial. Matches public/logo.svg
 * (used for the favicon); this inline copy renders crisply at any size
 * without an extra network request.
 */
function LogoMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 100 100" fill="none" aria-hidden="true">
      <path d="M28 78 L50 22 L72 78" stroke="#ffffff" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M37 60 L45 68 L65 48" stroke="#ffffff" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function WorkflowRail({ step }) {
  const items = [
    ["role", "Role"],
    ["analyze", "Analyze"],
    ["report", "Report"],
  ];
  return (
    <aside className="workflow-rail">
      <div className="rail-label">Workflow</div>
      {items.map(([id, label], index) => (
        <div key={id} className={`workflow-step ${step === id ? "active" : ""}`}>
          <span>{index + 1}</span>
          {label}
        </div>
      ))}
      <div className="rail-divider" />
      <div className="rail-note">
        Review-first delivery flow. External handoff is blocked until an artifact is reviewed.
      </div>
    </aside>
  );
}

/**
 * The Software Analyst Workflow Assistant: one continuous requirement -&gt;
 * impact -&gt; test -&gt; report pipeline instead of a role picker. A single
 * fixed profile (business-analyst, already permitted for requirement-analysis
 * and impact-analysis) drives every call — the point of this mode is the
 * analyst's workflow, not which role is "allowed" to click what.
 */
const WORKFLOW_STEPS = [
  ["inbox", "Analyst Inbox"],
  ["requirement", "Requirement Triage"],
  ["impact", "Impact Analysis"],
  ["test", "Test Scenarios"],
  ["report", "Handoff Summary"],
];

function AnalystWorkflow({ workspace, onWorkspaceUpdated, onViewProjectOverview, onSwitchProject }) {
  const [phase, setPhase] = useState("inbox");
  const [inboxView, setInboxView] = useState("chat");
  const [ticket, setTicket] = useState(EMPTY_TICKET);
  const [reqArtifact, setReqArtifact] = useState(null);
  const [impactArtifact, setImpactArtifact] = useState(null);
  const [impactLoading, setImpactLoading] = useState(false);
  const [impactError, setImpactError] = useState("");
  const [testArtifacts, setTestArtifacts] = useState([]);
  const [testScopeArtifacts, setTestScopeArtifacts] = useState({});
  const [summaryArtifact, setSummaryArtifact] = useState(null);
  const [summaryHandoffs, setSummaryHandoffs] = useState([]);
  const startedImpactRef = useRef(false);

  const reqStatus = reqArtifact ? getRequirementStatus(reqArtifact) : null;

  useEffect(() => {
    function focusInboxTarget(id) {
      window.setTimeout(() => {
        document.getElementById(id)?.focus();
      }, 80);
    }

    function handleQuickAction(event) {
      const action = event.detail?.action;
      if (action === "inbox") {
        setPhase("inbox");
        setInboxView("queue");
        return;
      }
      if (action === "import-jira") {
        setPhase("inbox");
        setInboxView("jira");
        focusInboxTarget("jira-import-input");
        return;
      }
      if (action === "manual-intake") {
        setPhase("inbox");
        setInboxView("manual");
        return;
      }
      if (action === "connect-apps") {
        setPhase("inbox");
        setInboxView("apps");
      }
    }

    window.addEventListener(QUICK_ACTION_EVENT, handleQuickAction);
    return () => window.removeEventListener(QUICK_ACTION_EVENT, handleQuickAction);
  }, []);

  function clearWorkflowArtifacts() {
    setReqArtifact(null);
    setImpactArtifact(null);
    setImpactError("");
    setTestArtifacts([]);
    setTestScopeArtifacts({});
    setSummaryArtifact(null);
    setSummaryHandoffs([]);
    startedImpactRef.current = false;
  }

  function reset() {
    setPhase("inbox");
    setInboxView("chat");
    setTicket(EMPTY_TICKET);
    clearWorkflowArtifacts();
  }

  function returnToWorkQueue() {
    setPhase("inbox");
    setInboxView("queue");
  }

  function selectInboxTicket(nextTicket) {
    setTicket({ ...EMPTY_TICKET, ...nextTicket });
    clearWorkflowArtifacts();
    setPhase("requirement");
  }

  async function runImpactAnalysis() {
    if (!reqArtifact) {
      setImpactError("Requirement analysis artifact is required before impact analysis.");
      return;
    }
    setImpactLoading(true);
    setImpactError("");
    try {
      const next = await api(`/api/artifacts/${reqArtifact.task_id}/handoff/impact-analysis`, {
        method: "POST",
        body: { profile: ANALYST_PROFILE },
      });
      setImpactArtifact(next);
    } catch (err) {
      setImpactError(err.message);
    } finally {
      setImpactLoading(false);
    }
  }

  useEffect(() => {
    if (phase === "impact" && !startedImpactRef.current) {
      startedImpactRef.current = true;
      runImpactAnalysis();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  async function reviewRequirement() {
    const next = await api(`/api/artifacts/${reqArtifact.task_id}/review`, { method: "PATCH" });
    setReqArtifact({ ...next, analysis_status: reqStatus });
    setPhase("impact");
  }

  async function reviewImpact() {
    const next = await api(`/api/artifacts/${impactArtifact.task_id}/review`, { method: "PATCH" });
    setImpactArtifact(next);
  }

  async function generateTests(moduleName) {
    const next = await api(`/api/artifacts/${impactArtifact.task_id}/handoff/test-case-gen`, {
      method: "POST",
      body: { profile: ANALYST_PROFILE, target: moduleName },
    });
    setTestArtifacts((prev) => [...prev.filter((item) => item.result?.target !== moduleName), next]);
  }

  async function saveTestScope(testArtifact, cases, notes) {
    const next = await api(`/api/artifacts/${testArtifact.task_id}/test-scope`, {
      method: "POST",
      body: { profile: ANALYST_PROFILE, cases, notes },
    });
    setTestScopeArtifacts((prev) => ({ ...prev, [testArtifact.task_id]: next }));
    return next;
  }

  async function reviewTestScope(testArtifact, scopeArtifact) {
    const next = await api(`/api/artifacts/${scopeArtifact.task_id}/review`, { method: "PATCH" });
    setTestScopeArtifacts((prev) => ({ ...prev, [testArtifact.task_id]: next }));
    return next;
  }

  async function generateSummary() {
    const selectedTestTaskIds = testArtifacts.map((item) => {
      const managedScope = testScopeArtifacts[item.task_id];
      return managedScope?.reviewed ? managedScope.task_id : item.task_id;
    });
    const next = await api(`/api/artifacts/${impactArtifact.task_id}/handoff/handoff-summary`, {
      method: "POST",
      body: {
        profile: ANALYST_PROFILE,
        requirement_task_id: reqArtifact.task_id,
        test_task_ids: selectedTestTaskIds,
      },
    });
    setSummaryArtifact(next);
    setSummaryHandoffs([]);
  }

  async function reviewSummary() {
    const next = await api(`/api/artifacts/${summaryArtifact.task_id}/review`, { method: "PATCH" });
    setSummaryArtifact(next);
    await loadSummaryHandoffs(next.task_id);
  }

  async function loadSummaryHandoffs(taskId = summaryArtifact?.task_id) {
    if (!taskId) {
      setSummaryHandoffs([]);
      return;
    }
    try {
      const items = await api(`/api/artifacts/${taskId}/external-handoffs`);
      setSummaryHandoffs(items);
    } catch {
      setSummaryHandoffs([]);
    }
  }

  return (
    <>
      <AnalystWorkflowRail
        phase={phase}
        reqStatus={reqStatus}
        selectedTicket={ticket}
        impactReviewed={Boolean(impactArtifact?.reviewed)}
        testCount={testArtifacts.length}
        onReset={reset}
        inboxView={inboxView}
        onInboxViewChange={setInboxView}
        onGoInbox={() => setPhase("inbox")}
        onManual={() => selectInboxTicket({ ...EMPTY_TICKET, receivedAt: "Manual draft" })}
        workspace={workspace}
        onWorkspaceUpdated={onWorkspaceUpdated}
        onViewProjectOverview={onViewProjectOverview}
        onSwitchProject={onSwitchProject}
        onOpenPrototype={setPhase}
      />
      <main className="workspace">
        {phase === "inbox" && (
          <AnalystInboxPhase
            items={ANALYST_INBOX_ITEMS}
            onSelect={selectInboxTicket}
            onManual={() => selectInboxTicket({ ...EMPTY_TICKET, receivedAt: "Manual draft" })}
            activeView={inboxView}
            onViewChange={setInboxView}
            workspace={workspace}
            onViewProjectOverview={onViewProjectOverview}
            onSwitchProject={onSwitchProject}
          />
        )}
        {phase === "requirement" && (
          <RequirementPhase
            ticket={ticket}
            onTicketChange={setTicket}
            reqArtifact={reqArtifact}
            reqStatus={reqStatus}
            onBackToInbox={reset}
            onArtifact={setReqArtifact}
            onReview={reviewRequirement}
          />
        )}
        {phase === "impact" && (
          <ImpactPhase
            loading={impactLoading}
            error={impactError}
            artifact={impactArtifact}
            onRetry={runImpactAnalysis}
            onReview={reviewImpact}
            onBack={() => setPhase("requirement")}
            onNext={() => setPhase("test")}
          />
        )}
        {phase === "test" && (
          <TestPhase
            impactArtifact={impactArtifact}
            testArtifacts={testArtifacts}
            testScopeArtifacts={testScopeArtifacts}
            onGenerate={generateTests}
            onSaveTestScope={saveTestScope}
            onReviewTestScope={reviewTestScope}
            onBack={() => setPhase("impact")}
            onNext={() => setPhase("report")}
          />
        )}
        {phase === "report" && (
          <ReportPhase
            ticket={ticket}
            reqArtifact={reqArtifact}
            impactArtifact={impactArtifact}
            testArtifacts={testArtifacts}
            testScopeArtifacts={testScopeArtifacts}
            summaryArtifact={summaryArtifact}
            summaryHandoffs={summaryHandoffs}
            onGenerateSummary={generateSummary}
            onReviewSummary={reviewSummary}
            onReloadSummaryHandoffs={loadSummaryHandoffs}
            onRestart={reset}
            onBack={() => setPhase("test")}
          />
        )}
        {phase === "testing-sync" && <TestingSyncPrototype workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "project-tracker" && (
          <ProjectTrackerPage
            workspace={workspace}
            onWorkspaceUpdated={onWorkspaceUpdated}
            onSwitchProject={onSwitchProject}
            onBack={returnToWorkQueue}
          />
        )}
        {phase === "ticket-kanban" && <TicketKanbanPage workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "db-diagnostics" && <DbDiagnosticsPrototype workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "evidence-gate" && <EvidenceGatePrototype workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "hermes-tracker" && <HermesTrackerPrototype workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "hermes-version-advisor" && <HermesVersionAdvisorPage workspace={workspace} onBack={returnToWorkQueue} />}
        {phase === "hermes-trending-digest" && <HermesTrendingDigestPage onBack={returnToWorkQueue} />}
        {phase === "memory-center" && <MemoryCenterPrototype workspace={workspace} onBack={returnToWorkQueue} />}
      </main>
    </>
  );
}

function AnalystWorkflowRail({
  phase,
  reqStatus,
  selectedTicket,
  impactReviewed,
  testCount,
  onReset,
  inboxView,
  onInboxViewChange,
  onGoInbox,
  onManual,
  workspace,
  onWorkspaceUpdated,
  onViewProjectOverview,
  onSwitchProject,
  onOpenPrototype,
}) {
  const order = WORKFLOW_STEPS.map(([id]) => id);
  const currentIndex = order.indexOf(phase);
  const subtitle = {
    inbox: phase === "inbox" ? "Select work item" : selectedTicket.ticketKey || selectedTicket.sourceType || "Selected",
    requirement: reqStatus ? formatStatus(reqStatus) : "Not started",
    impact: impactReviewed ? "Reviewed" : currentIndex >= order.indexOf("impact") ? "In review" : "Pending",
    test: testCount > 0 ? `${testCount} scenario set${testCount === 1 ? "" : "s"}` : "Pending",
    report: phase === "report" ? "Viewing" : "Pending",
  };
  return (
    <aside className="workflow-rail">
      <div className="rail-section">
        <div className="rail-label">Project</div>
        <div className="rail-project-card">
          <strong>{workspace?.name || "No project connected"}</strong>
          {workspace?.local_path && <span className="rail-project-path">{workspace.local_path}</span>}
          <span>{workspace?.index_status === "indexing" ? "Indexing project..." : workspace?.index_status === "ready" ? "Ready for analysis" : "Project setup"}</span>
          <div className="rail-project-actions">
            <button className="btn ghost compact" type="button" onClick={onViewProjectOverview}>
              Project Overview Diagram
            </button>
            <button className="btn ghost compact" type="button" onClick={onSwitchProject}>
              Switch
            </button>
          </div>
        </div>
      </div>
      <div className="rail-section rail-workspace-nav-section">
        <div className="rail-label">Workspace</div>
        <div className="rail-nav">
          {/* Ordered by where an analyst actually spends time day to day:
              bring work in -> understand the repo -> monitor progress
              (two views) -> check what's happened downstream at Hermes.
              Hermes Incident Tracker moved here from Enhancement Lab now
              that it's wired to real data (GET /api/hermes/status/current),
              not the earlier dummy view. Hermes Setup Wizard moved out of
              this rail entirely — it now lives in the Project Control
              Center (ConnectProjectScreen) so setup happens up front,
              before an analyst ever reaches these workbench pages. */}
          <button
            className={phase === "inbox" && (inboxView === "jira" || inboxView === "manual") ? "active intake-nav-item" : "intake-nav-item"}
            type="button"
            onClick={() => {
              onGoInbox();
              onInboxViewChange("jira");
            }}
          >
            <span>Ticket Intake</span>
            <small>Import Jira or draft manually</small>
          </button>
          <button
            className={phase === "inbox" && inboxView === "chat" ? "active" : ""}
            type="button"
            onClick={() => {
              onGoInbox();
              onInboxViewChange("chat");
            }}
          >
            <span>Chat</span>
            <small>Ask the current repo</small>
          </button>
          <button
            className={phase === "project-tracker" ? "active" : ""}
            type="button"
            onClick={() => onOpenPrototype("project-tracker")}
          >
            <span>Project Tracker</span>
            <small>Monitor project phase</small>
          </button>
          <button
            className={phase === "ticket-kanban" ? "active" : ""}
            type="button"
            onClick={() => onOpenPrototype("ticket-kanban")}
          >
            <span>Kanban Ticket Status</span>
            <small>Track ticket status</small>
          </button>
          <button
            className={phase === "hermes-tracker" ? "active" : ""}
            type="button"
            onClick={() => onOpenPrototype("hermes-tracker")}
          >
            <span>Hermes Incident Tracker</span>
            <small>Check Incident Progress</small>
          </button>
          <button
            className={phase === "hermes-version-advisor" ? "active" : ""}
            type="button"
            onClick={() => onOpenPrototype("hermes-version-advisor")}
          >
            <span>Hermes Version Control</span>
            <small>Which upstream commit to adopt</small>
          </button>
          <button
            className={phase === "hermes-trending-digest" ? "active" : ""}
            type="button"
            onClick={() => onOpenPrototype("hermes-trending-digest")}
          >
            <span> Project Trending  </span>
            <small>Weekly GitHub Trending scan</small>
          </button>
          <button
            className={phase === "inbox" && inboxView === "apps" ? "active" : ""}
            type="button"
            onClick={() => {
              onGoInbox();
              onInboxViewChange("apps");
            }}
          >
            <span>Connect Apps</span>
            <small>Jira, email, calendar</small>
          </button>
        </div>
      </div>
      {/* <div className="rail-section rail-workspace-nav-section">
        <div className="rail-label">Enhancement Lab</div>
        <div className="rail-nav compact">
          {ENHANCEMENT_TOOLS.map(([id, label, caption]) => (
            <button
              key={id}
              className={phase === id ? "active" : ""}
              type="button"
              onClick={() => onOpenPrototype(id)}
            >
              <span>{label}</span>
              <small>{caption}</small>
            </button>
          ))}
        </div>
      </div> */}
      {/* <div className="rail-current-card">
        <span>Current work</span>
        <strong>{selectedTicket.ticketKey || selectedTicket.ticketTitle || (phase === "inbox" ? "Inbox" : "Analysis")}</strong>
        <small>{reqStatus ? formatStatus(reqStatus) : impactReviewed ? "Impact reviewed" : testCount > 0 ? `${testCount} test set${testCount === 1 ? "" : "s"}` : "No ticket selected"}</small>
      </div> */}
      <div className="rail-label">Analyst Workflow</div>
      {WORKFLOW_STEPS.map(([id, label], index) => {
        const state = index < currentIndex ? "complete" : index === currentIndex ? "active" : "";
        return (
          <div key={id} className={`workflow-step ${state}`}>
            <span>{index < currentIndex ? "✓" : index + 1}</span>
            <div className="workflow-step-text">
              {label}
              <small>{subtitle[id]}</small>
            </div>
          </div>
        );
      })}
      <div className="rail-divider" />
      <div className="rail-note">
        One continuous external work item to requirement to impact to test to report pipeline. Clarification and review gates run inline before you can move to the next step.
      </div>
      <button className="btn ghost compact rail-reset" type="button" onClick={onReset}>
        Start new analysis
      </button>
      <AnalystCalendar />
    </aside>
  );
}

/**
 * Google Calendar-style agenda list, not a month grid — the rail is 292px
 * wide, an agenda view is what Google Calendar itself falls back to at this
 * width. Sample events for now (per the user's own scoping call: build the
 * widget and notification behavior first, wire real Google Calendar OAuth
 * later once there's something worth syncing against). The red dot marks
 * "new since last check" the same way an inbox unread marker would.
 */
const CALENDAR_EVENTS = [
  { id: "cal-1", day: "Today", time: "10:00", title: "Sprint planning", withWho: "Dev + QA", isNew: true },
  { id: "cal-2", day: "Today", time: "14:30", title: "Clarify MBC-211 with stakeholder", withWho: "Ops Lead", isNew: true },
  { id: "cal-3", day: "Tomorrow", time: "09:30", title: "Requirement review", withWho: "Product Owner", isNew: false },
  { id: "cal-4", day: "Thu", time: "15:00", title: "QA handoff sync", withWho: "Tester", isNew: false },
];

const CALENDAR_DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** "2026-07-30" (all-day) or a full ISO datetime — Google Calendar sends either shape. */
function formatEventDayTime(value) {
  if (!value) {
    return { day: "", time: "" };
  }
  const isAllDay = value.length === 10;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return { day: value, time: "" };
  }
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const diffDays = Math.round((startOfDay(date) - startOfDay(new Date())) / 86400000);
  let day;
  if (diffDays === 0) day = "Today";
  else if (diffDays === 1) day = "Tomorrow";
  else if (diffDays > 1 && diffDays < 7) day = date.toLocaleDateString([], { weekday: "short" });
  else day = date.toLocaleDateString([], { month: "short", day: "numeric" });
  const time = isAllDay ? "All day" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
  return { day, time };
}

function calendarDateFromEvent(event) {
  const value = event.start_time || event.startTime || event.date || "";
  const parsed = value ? new Date(value) : null;
  if (parsed && !Number.isNaN(parsed.getTime())) return parsed;
  if (event.day === "Today") return new Date();
  if (event.day === "Tomorrow") {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    return tomorrow;
  }
  return null;
}

function calendarDateKey(date) {
  if (!date) return "";
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function normalizeCalendarEvent(event) {
  const date = calendarDateFromEvent(event);
  const fromGoogle = formatEventDayTime(event.start_time || event.startTime || "");
  return {
    id: event.id,
    title: event.title || "(no title)",
    day: fromGoogle.day || event.day || "",
    time: fromGoogle.time || event.time || "",
    withWho: event.attendees || event.withWho || (event.meet_link || event.meetLink ? "Google Meet" : ""),
    meetLink: event.meet_link || event.meetLink || "",
    isNew: Boolean(event.recently_updated ?? event.isNew),
    date,
    dateKey: calendarDateKey(date),
  };
}

function buildCalendarDays(events, mode) {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  if (mode === "month") {
    start.setDate(1);
  } else {
    start.setDate(start.getDate() - start.getDay());
  }
  const length = mode === "month" ? 35 : 7;
  const normalized = events.map(normalizeCalendarEvent);
  return Array.from({ length }, (_, index) => {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    const dateKey = calendarDateKey(date);
    return {
      date,
      dateKey,
      label: date.getDate(),
      dayName: CALENDAR_DAY_NAMES[date.getDay()],
      isToday: dateKey === calendarDateKey(today),
      isCurrentMonth: date.getMonth() === today.getMonth(),
      events: normalized.filter((event) => event.dateKey === dateKey),
    };
  });
}

function emailSenderName(from) {
  if (!from) return "Unknown sender";
  return from.replace(/<.*?>/g, "").trim() || from;
}

function emailSenderInitial(from) {
  return emailSenderName(from).slice(0, 1).toUpperCase() || "E";
}

function emailCategory(message) {
  const text = `${message.subject || ""} ${message.snippet || ""}`.toLowerCase();
  if (/(requirement|request|change|ticket|issue|bug|clarify|approval|stakeholder)/.test(text)) return "Likely requirement";
  if (/(meeting|schedule|agenda|sync|review)/.test(text)) return "Meeting follow-up";
  return "Needs triage";
}

function formatEmailDate(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString([], { month: "short", day: "numeric" });
}

function AnalystCalendar() {
  const [events, setEvents] = useState(CALENDAR_EVENTS);
  const [live, setLive] = useState(false);
  const [mode, setMode] = useState("week");

  useEffect(() => {
    let cancelled = false;
    api("/api/integrations/google/status")
      .then((status) => {
        if (cancelled || !status.connected) return undefined;
        return api("/api/integrations/google/calendar/events");
      })
      .then((items) => {
        if (cancelled || !items) return;
        setEvents(items.map(normalizeCalendarEvent));
        setLive(true);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const newCount = events.filter((event) => event.isNew).length;
  const days = buildCalendarDays(events, mode);
  const todayKey = calendarDateKey(new Date());
  const todayEvents = events.map(normalizeCalendarEvent).filter((event) => event.dateKey === todayKey);
  return (
    <div className="analyst-calendar">
      <div className="analyst-calendar-header">
        <span className="rail-label">Calendar</span>
        {newCount > 0 && <span className="calendar-badge">{newCount} new</span>}
      </div>
      <div className="calendar-toggle" aria-label="Calendar view">
        <button className={mode === "week" ? "active" : ""} type="button" onClick={() => setMode("week")}>
          Week
        </button>
        <button className={mode === "month" ? "active" : ""} type="button" onClick={() => setMode("month")}>
          Month
        </button>
      </div>
      <CalendarGrid days={days} compact={mode === "month"} />
      <div className="calendar-notification-panel">
        <strong>Today notifications</strong>
        {todayEvents.length === 0 ? (
          <span>No meetings today.</span>
        ) : (
          todayEvents.slice(0, 3).map((event) => (
            <div key={event.id} className="calendar-notification">
              <span>{event.time || "All day"}</span>
              <p>{event.title}</p>
            </div>
          ))
        )}
      </div>
      <p className="calendar-note">
        {live
          ? "Live from your connected Google Calendar."
          : "Sample schedule. Connect Google Calendar to sync real invites and get live notifications here."}
      </p>
    </div>
  );
}

function CalendarGrid({ days, compact = false }) {
  return (
    <div className={`calendar-grid ${compact ? "month" : "week"}`}>
      {days.map((day) => (
        <div
          key={day.dateKey}
          className={`calendar-day ${day.isToday ? "today" : ""} ${day.isCurrentMonth ? "" : "muted"}`}
        >
          <div className="calendar-day-head">
            <span>{day.dayName}</span>
            <strong>{day.label}</strong>
          </div>
          <div className="calendar-day-events">
            {day.events.slice(0, compact ? 2 : 3).map((event) => (
              <span key={event.id} title={event.title}>
                {event.time && !compact ? `${event.time} ` : ""}
                {event.title}
              </span>
            ))}
            {day.events.length > (compact ? 2 : 3) && <em>+{day.events.length - (compact ? 2 : 3)}</em>}
          </div>
        </div>
      ))}
    </div>
  );
}

const SAMPLE_CSV_ROW =
  "key,title,priority,reporter,description,acceptance criteria,comments\n" +
  "MBC-231,Export approved aid requests to Excel,Medium,Ops Lead," +
  "Ops team wants a CSV export of approved aid requests for the weekly report.," +
  "Given approved aid requests exist, when the Ops Lead exports, then a CSV with city/category/urgency is downloaded.," +
  "Confirm whether donors' contact info should be excluded from the export.";

/**
 * Mission-control strip: one place to see whether any connected platform
 * needs attention, instead of tab-switching between Jira/email/Zoom/Meet/
 * Calendar to check. Jira is marked connected because it really is (live
 * API token). Email/Meet/Calendar all ride the same Google OAuth connection
 * (Calendar + Gmail scopes — a Meet event's join link lives on the Calendar
 * event itself, there's no separate personal "list my Meet meetings" API),
 * so their tiles reflect one /api/integrations/google/status check, and
 * "Connect" is a real browser navigation to the OAuth flow, not a message.
 * Zoom stays honestly "Not connected" — that needs its own separate OAuth
 * app from Zoom App Marketplace that nothing here is wired to yet, so
 * "Connect" explains that instead of pretending to be live.
 */
const PLATFORM_STATUS = [
  { id: "jira", label: "Jira", google: false, detail: "KAN project synced today", metric: "3 tickets", isNew: false },
  { id: "email", label: "Email", google: true, detail: "Requirement threads from stakeholders", metric: "2 unread", isNew: true },
  { id: "zoom", label: "Zoom", google: false, detail: "Meeting notes after OAuth setup", metric: "offline", isNew: false },
  { id: "meet", label: "Google Meet", google: true, detail: "Meet links from Calendar events", metric: "auto-log", isNew: false },
  { id: "calendar", label: "Calendar", google: true, detail: "Upcoming requirement sessions", metric: "2 invites", isNew: true },
];

const CONNECTOR_SETTINGS = [
  {
    id: "jira",
    label: "Jira",
    group: "Ticket system",
    status: "Connected",
    mode: "Read + write",
    fields: ["Site URL", "Project key", "Issue type", "API token"],
    purpose: "Import analyst tickets, create follow-up issues, and post reviewed handoff comments.",
  },
  {
    id: "github",
    label: "GitHub",
    group: "Code host",
    status: "Read-only",
    mode: "PR / repo context",
    fields: ["Repository URL", "Access token optional", "Allowed branches"],
    purpose: "Read PR details or restricted repo snippets for impact analysis without requiring write access.",
  },
  {
    id: "bitbucket",
    label: "Bitbucket",
    group: "Code host",
    status: "Configured by env",
    mode: "PR comment",
    fields: ["Workspace", "Repository", "Username", "App password"],
    purpose: "Post reviewed analyst handoff notes back to a pull request after approval.",
  },
  {
    id: "hermes",
    label: "Hermes",
    group: "Agent bridge",
    status: "Bridge ready",
    mode: "Discord / email intake",
    fields: ["Discord webhook", "Target label", "Callback status URL"],
    purpose: "Send reviewed analyst packages to Hermes and receive developer/testing progress updates.",
  },
  {
    id: "google",
    label: "Google Workspace",
    group: "Mailbox + calendar",
    status: "OAuth",
    mode: "Gmail + Calendar",
    fields: ["OAuth client", "Gmail scope", "Calendar scope"],
    purpose: "Monitor requirement emails, meeting notes, and calendar signals in one analyst inbox.",
  },
];

function PlatformStatusStrip() {
  const [connectMessage, setConnectMessage] = useState("");
  const [googleConnected, setGoogleConnected] = useState(false);
  const [emailUnread, setEmailUnread] = useState(null);
  const [calendarCount, setCalendarCount] = useState(null);

  useEffect(() => {
    api("/api/integrations/google/status")
      .then((status) => setGoogleConnected(Boolean(status.connected)))
      .catch(() => setGoogleConnected(false));
  }, []);

  useEffect(() => {
    if (!googleConnected) return;
    api("/api/integrations/google/gmail/summary")
      .then((summary) => setEmailUnread(summary.unread_count))
      .catch(() => {});
    api("/api/integrations/google/calendar/events")
      .then((events) => setCalendarCount(events.length))
      .catch(() => {});
  }, [googleConnected]);

  function isConnected(platform) {
    if (platform.id === "jira") return true;
    if (platform.google) return googleConnected;
    return false;
  }

  function isNew(platform) {
    if (platform.id === "email" && emailUnread !== null) return emailUnread > 0;
    if (platform.id === "calendar" && calendarCount !== null) return calendarCount > 0;
    return platform.isNew;
  }

  function tileMetric(platform) {
    if (platform.id === "email" && emailUnread !== null) return `${emailUnread} unread`;
    if (platform.id === "calendar" && calendarCount !== null) return `${calendarCount} upcoming`;
    return platform.metric;
  }

  function tileDetail(platform) {
    if (platform.id === "email" && emailUnread !== null) {
      return emailUnread > 0 ? `${emailUnread} unread emails, live from Gmail` : "Inbox zero — nothing waiting";
    }
    if (platform.id === "calendar" && calendarCount !== null) {
      return calendarCount > 0
        ? `${calendarCount} upcoming event${calendarCount === 1 ? "" : "s"}, live from Google Calendar`
        : "No upcoming events";
    }
    return platform.detail;
  }

  function handleConnect(platform) {
    if (platform.google) {
      window.location.href = `${API_BASE}/api/integrations/google/connect`;
      return;
    }
    setConnectMessage(
      `${platform.label}: needs an OAuth app (with API credentials) created for this account before it can connect for real.`
    );
  }

  return (
    <section id="platform-command-center" className="command-center">
      <div className="command-center-head">
        <div>
          <div className="eyebrow">Unified monitoring</div>
          <h2>Analyst control center</h2>
          <p>Connected platforms are watched in one place before the workflow turns a work item into analysis, test scope, and handoff.</p>
        </div>
        <div className="command-metrics">
          <div>
            <strong>{PLATFORM_STATUS.filter((platform) => isConnected(platform)).length}</strong>
            <span>Connected</span>
          </div>
          <div>
            <strong>{PLATFORM_STATUS.filter((platform) => isNew(platform)).length}</strong>
            <span>New signals</span>
          </div>
          <div>
            <strong>1</strong>
            <span>Inbox</span>
          </div>
        </div>
      </div>
      <div className="platform-strip">
        {PLATFORM_STATUS.map((platform) => {
          const connected = isConnected(platform);
          return (
            <div key={platform.id} className={`platform-tile ${platform.id} ${connected ? "connected" : ""}`}>
              <PlatformLogo platform={platform.id} className="platform-watermark" />
              <div className="platform-tile-top">
                <span className={`platform-icon ${platform.id}`}>
                  <PlatformLogo platform={platform.id} />
                </span>
                <div>
                  <span className="platform-tile-name">{platform.label}</span>
                  <span className="platform-tile-metric">{tileMetric(platform)}</span>
                </div>
                {isNew(platform) && <span className="activity-dot" title="New activity" />}
              </div>
              <span className={`status-pill ${connected ? "reviewed" : "unreviewed"}`}>
                {connected ? "Connected" : "Not connected"}
              </span>
              <p className="platform-tile-detail">{tileDetail(platform)}</p>
              {!connected && (
                <button className="btn ghost compact" type="button" onClick={() => handleConnect(platform)}>
                  Connect
                </button>
              )}
            </div>
          );
        })}
      </div>
      {connectMessage && <div className="info-box platform-strip-message">{connectMessage}</div>}
    </section>
  );
}

function PlatformLogo({ platform, className = "" }) {
  if (platform === "jira") {
    return (
      <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#2684ff" d="M23.8 8.1 8.3 23.6a3.6 3.6 0 0 0 0 5.1l10.6 10.6 5.8-5.8-8-8 12.9-12.9-5.8-4.5Z" />
        <path fill="#0052cc" d="m24.2 39.9 15.5-15.5a3.6 3.6 0 0 0 0-5.1L29.1 8.7l-5.8 5.8 8 8-12.9 12.9 5.8 4.5Z" />
      </svg>
    );
  }
  if (platform === "email") {
    return (
      <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#ea4335" d="M8 14.5 24 26.5 40 14.5v20.2c0 2-1.5 3.6-3.5 3.6h-25c-2 0-3.5-1.6-3.5-3.6V14.5Z" />
        <path fill="#fbbc04" d="M8 14.5 24 26.5 8 38.3V14.5Z" />
        <path fill="#34a853" d="M40 14.5 24 26.5 40 38.3V14.5Z" />
        <path fill="#4285f4" d="M11.5 9.7h25c2 0 3.5 1.6 3.5 3.6v1.2L24 26.5 8 14.5v-1.2c0-2 1.5-3.6 3.5-3.6Z" />
        <path fill="#ffffff" opacity=".9" d="M12 15.3 24 24.4l12-9.1v18.8H12V15.3Z" />
      </svg>
    );
  }
  if (platform === "zoom") {
    return (
      <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
        <rect x="6" y="9" width="36" height="30" rx="10" fill="#2d8cff" />
        <rect x="13" y="17" width="16" height="14" rx="3" fill="#ffffff" />
        <path fill="#ffffff" d="M29 20.5 36 17v14l-7-3.5v-7Z" />
      </svg>
    );
  }
  if (platform === "meet") {
    return (
      <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#00ac47" d="M8 15.5c0-3 2.4-5.5 5.5-5.5H28v28H13.5A5.5 5.5 0 0 1 8 32.5v-17Z" />
        <path fill="#00832d" d="M28 20.5 40 13v22l-12-7.5v-7Z" />
        <path fill="#ffba00" d="M8 24h20v14H13.5A5.5 5.5 0 0 1 8 32.5V24Z" />
        <path fill="#0066da" d="M13.5 10H28v14H8v-8.5c0-3 2.4-5.5 5.5-5.5Z" />
        <path fill="#ffffff" opacity=".95" d="M15 17h11v14H15z" />
      </svg>
    );
  }
  return (
    <svg className={className} viewBox="0 0 48 48" aria-hidden="true">
      <rect x="9" y="8" width="30" height="32" rx="5" fill="#4285f4" />
      <path fill="#34a853" d="M9 16h30v7H9z" />
      <path fill="#fbbc04" d="M9 23h30v7H9z" />
      <path fill="#ea4335" d="M9 8h30v8H9z" />
      <rect x="14" y="19" width="20" height="17" rx="2" fill="#ffffff" />
      <text x="24" y="32" textAnchor="middle" fontSize="12" fontWeight="800" fill="#3c4043">
        31
      </text>
    </svg>
  );
}

function AnalystInboxPhase({ items, onSelect, onManual, activeView, onViewChange, workspace, onViewProjectOverview, onSwitchProject }) {
  const [importKey, setImportKey] = useState("MBC-204");
  const [importedItems, setImportedItems] = useState([]);
  const [importLoading, setImportLoading] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [error, setError] = useState("");
  const [csvText, setCsvText] = useState("");
  const [csvLoading, setCsvLoading] = useState(false);
  const [csvMessage, setCsvMessage] = useState("");
  const [csvError, setCsvError] = useState("");
  const [googleConnected, setGoogleConnected] = useState(false);
  const [gmailMessages, setGmailMessages] = useState([]);
  const [gmailUnreadCount, setGmailUnreadCount] = useState(null);
  const [gmailLoading, setGmailLoading] = useState(false);
  const [gmailMessage, setGmailMessage] = useState("");
  const [gmailError, setGmailError] = useState("");
  const [calendarEvents, setCalendarEvents] = useState([]);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarMessage, setCalendarMessage] = useState("");
  const [calendarError, setCalendarError] = useState("");
  const [repoQuestion, setRepoQuestion] = useState("Which controllers handle artifact history?");
  const [repoChatMessages, setRepoChatMessages] = useState([]);
  const [repoChatLoading, setRepoChatLoading] = useState(false);
  const [repoChatError, setRepoChatError] = useState("");
  const allItems = [...importedItems, ...items];

  useEffect(() => {
    let cancelled = false;
    async function loadGoogleSources() {
      try {
        const status = await api("/api/integrations/google/status");
        if (cancelled || !status.connected) return;
        setGoogleConnected(true);
        const [summary, messages, events] = await Promise.all([
          api("/api/integrations/google/gmail/summary"),
          api("/api/integrations/google/gmail/messages"),
          api("/api/integrations/google/calendar/events"),
        ]);
        if (cancelled) return;
        setGmailUnreadCount(summary.unread_count ?? summary.unreadCount ?? null);
        setGmailMessages(messages);
        setCalendarEvents(events);
      } catch {
        // Google not connected or unreachable; email/calendar import panels stay hidden.
      }
    }
    loadGoogleSources();
    return () => {
      cancelled = true;
    };
  }, []);

  function recordImportedItem(ticket, source, status) {
    const importedItem = {
      id: `imported-${source}-${ticket.ticketKey || Date.now()}`,
      source,
      status,
      age: ticket.receivedAt || "Imported",
      ticket,
    };
    setImportedItems((prev) => [importedItem, ...prev.filter((item) => item.id !== importedItem.id)]);
    onSelect(ticket);
  }

  async function importJiraTicket() {
    setError("");
    setImportMessage("");
    if (!importKey.trim()) {
      setError("Jira ticket key or URL is required.");
      return;
    }
    setImportLoading(true);
    try {
      const response = await api("/api/integrations/jira/import", {
        method: "POST",
        body: importKey.includes("/")
          ? { ticket_url: importKey }
          : { ticket_key: importKey },
      });
      const ticket = ticketFromImportResponse(response);
      recordImportedItem(ticket, ticket.sourceType || "Jira", response.dry_run ? "Dry-run import" : "Live Jira import");
      setImportMessage(response.message || "Jira ticket imported into Analyst Inbox.");
    } catch (err) {
      setError(err.message);
    } finally {
      setImportLoading(false);
    }
  }

  async function importCsvTicket() {
    setCsvError("");
    setCsvMessage("");
    if (!csvText.trim()) {
      setCsvError("Paste a CSV header row and one data row first.");
      return;
    }
    setCsvLoading(true);
    try {
      const response = await api("/api/integrations/csv/import", {
        method: "POST",
        body: { csv_text: csvText },
      });
      const ticket = ticketFromImportResponse(response);
      recordImportedItem(ticket, "CSV", "Spreadsheet import");
      setCsvMessage(response.message || "Ticket imported from CSV.");
    } catch (err) {
      setCsvError(err.message);
    } finally {
      setCsvLoading(false);
    }
  }

  async function importEmail(messageId) {
    setGmailError("");
    setGmailMessage("");
    setGmailLoading(true);
    try {
      const response = await api("/api/integrations/google/gmail/import", {
        method: "POST",
        body: { message_id: messageId },
      });
      const ticket = ticketFromImportResponse(response);
      recordImportedItem(ticket, "Email", "Gmail import");
      setGmailMessage(response.message || "Email imported into Analyst Inbox.");
    } catch (err) {
      setGmailError(err.message);
    } finally {
      setGmailLoading(false);
    }
  }

  async function importCalendarEventItem(eventId) {
    setCalendarError("");
    setCalendarMessage("");
    setCalendarLoading(true);
    try {
      const response = await api("/api/integrations/google/calendar/import", {
        method: "POST",
        body: { event_id: eventId },
      });
      const ticket = ticketFromImportResponse(response);
      recordImportedItem(ticket, "Calendar", "Calendar import");
      setCalendarMessage(response.message || "Meeting imported into Analyst Inbox.");
    } catch (err) {
      setCalendarError(err.message);
    } finally {
      setCalendarLoading(false);
    }
  }

  async function askRepoQuestion(question = repoQuestion) {
    const normalized = question.trim();
    if (!normalized) {
      setRepoChatError("Ask a repo question first.");
      return;
    }
    setRepoChatError("");
    setRepoChatLoading(true);
    const userMessage = { id: `user-${Date.now()}`, role: "user", question: normalized };
    setRepoChatMessages((prev) => [...prev, userMessage]);
    try {
      const artifact = await api("/api/skills/code-qa", {
        method: "POST",
        body: { profile: ANALYST_PROFILE, question: normalized },
      });
      setRepoChatMessages((prev) => [
        ...prev,
        {
          id: artifact.task_id || `ai-${Date.now()}`,
          role: "assistant",
          question: normalized,
          artifact,
          result: artifact.result || {},
        },
      ]);
      setRepoQuestion("");
    } catch (err) {
      setRepoChatError(err.message);
    } finally {
      setRepoChatLoading(false);
    }
  }

  return (
    <section className="screen">
      <HeaderBlock
        eyebrow={inboxPageMeta(activeView).eyebrow}
        title={inboxPageMeta(activeView).title}
        subtitle={inboxPageMeta(activeView).subtitle}
      />
      {activeView === "chat" && (
        <section className="start-here-panel compact-start-panel">
        <div>
          <span className="source-pill">Start here</span>
          <h2>Understand the project first, then analyse the ticket.</h2>
          <p>
            {workspace
              ? `${workspace.name} is connected. Open the Project Overview to see the system map, then choose a work item from the queue.`
              : "Connect a project so impact analysis, project overview, and handoff evidence are grounded in real code."}
          </p>
        </div>
        <div className="start-here-actions">
          <button className="btn primary compact" type="button" onClick={workspace ? onViewProjectOverview : onSwitchProject}>
            {workspace ? "View Project Overview" : "Connect Project"}
          </button>
          <button className="btn ghost compact" type="button" onClick={onManual}>
            Start Manual Intake
          </button>
        </div>
      </section>
      )}
      <RepoAssistantWorkspace
        activeView={activeView}
        onViewChange={onViewChange}
        workspace={workspace}
        question={repoQuestion}
        messages={repoChatMessages}
        loading={repoChatLoading}
        error={repoChatError}
        onQuestionChange={setRepoQuestion}
        onAsk={askRepoQuestion}
        items={allItems}
        onSelect={onSelect}
        onManual={onManual}
        importKey={importKey}
        onImportKeyChange={setImportKey}
        importLoading={importLoading}
        importMessage={importMessage}
        importError={error}
        onImportJira={importJiraTicket}
        csvText={csvText}
        onCsvTextChange={setCsvText}
        csvLoading={csvLoading}
        csvMessage={csvMessage}
        csvError={csvError}
        onLoadSampleCsv={() => setCsvText(SAMPLE_CSV_ROW)}
        onImportCsv={importCsvTicket}
        googleConnected={googleConnected}
        gmailMessages={gmailMessages}
        gmailLoading={gmailLoading}
        gmailMessage={gmailMessage}
        gmailError={gmailError}
        gmailUnreadCount={gmailUnreadCount}
        onImportEmail={importEmail}
        calendarEvents={calendarEvents}
        calendarLoading={calendarLoading}
        calendarMessage={calendarMessage}
        calendarError={calendarError}
        onImportCalendar={importCalendarEventItem}
      />
    </section>
  );
}

function repoChatPrompts(workspace) {
  const name = (workspace?.name || "").toLowerCase();
  if (name.includes("banjir")) {
    return [
      "Which files are related to aid request filtering?",
      "What should I check before changing donation status?",
      "Where is flood report approval handled?",
      "What modules may be affected by notification changes?",
    ];
  }
  return [
    "Which controllers handle artifact history?",
    "Where is requirement analysis implemented?",
    "What handles Jira external handoff?",
    "Which files are related to project workspace indexing?",
  ];
}

function inboxPageMeta(view) {
  if (view === "queue") {
    return {
      eyebrow: "Analyst Inbox",
      title: "Choose a work item",
      subtitle: "Select one request from Jira, email, meeting notes, or sample data to start the analysis workflow.",
    };
  }
  if (view === "jira") {
    return {
      eyebrow: "Ticket intake",
      title: "Import a Jira ticket",
      subtitle: "Fetch a Jira ticket into the platform so the analyst does not need to copy ticket details manually.",
    };
  }
  if (view === "manual") {
    return {
      eyebrow: "Manual intake",
      title: "Start a manual change request",
      subtitle: "Use this when the requirement came from a call, email, document, or stakeholder conversation.",
    };
  }
  if (view === "apps") {
    return {
      eyebrow: "Connected sources",
      title: "Monitor connected platforms",
      subtitle: "Check Jira, email, meetings, and calendar signals from one place.",
    };
  }
  return {
    eyebrow: "Repo AI Chat",
    title: "Ask about the current project",
    subtitle: "Use AI to understand the connected repo before analysing a ticket or change request.",
  };
}

function RepoAssistantWorkspace({
  activeView,
  onViewChange,
  workspace,
  question,
  messages,
  loading,
  error,
  onQuestionChange,
  onAsk,
  items,
  onSelect,
  onManual,
  importKey,
  onImportKeyChange,
  importLoading,
  importMessage,
  importError,
  onImportJira,
  csvText,
  onCsvTextChange,
  csvLoading,
  csvMessage,
  csvError,
  onLoadSampleCsv,
  onImportCsv,
  googleConnected,
  gmailMessages,
  gmailLoading,
  gmailMessage,
  gmailError,
  gmailUnreadCount,
  onImportEmail,
  calendarEvents,
  calendarLoading,
  calendarMessage,
  calendarError,
  onImportCalendar,
}) {
  const latestAssistant = [...messages].reverse().find((message) => message.role === "assistant");

  if (activeView === "queue") {
    return (
      <section className="focused-page-card">
        <div className="focused-page-head">
          <div>
            <span className="source-pill">Work Queue</span>
            <h2>Incoming work items</h2>
            <p>Pick a ticket when you are ready to start requirement analysis.</p>
          </div>
          <button className="btn ghost compact" type="button" onClick={() => onViewChange("chat")}>
            Ask repo first
          </button>
        </div>
        <div className="queue-card-grid">
          {items.map((item) => (
            <button key={item.id} type="button" className="queue-card" onClick={() => onSelect(item.ticket)}>
              <div>
                <span className="source-pill">{item.source}</span>
                <em>{item.age}</em>
              </div>
              <strong>{item.ticket.ticketTitle}</strong>
              <p>{item.ticket.description}</p>
              <div className="inbox-item-meta">
                <span>{item.ticket.ticketKey}</span>
                <span>{item.ticket.priority}</span>
                <span>{item.status}</span>
              </div>
            </button>
          ))}
        </div>
      </section>
    );
  }

  if (activeView === "jira") {
    return (
      <section className="focused-page-card intake-focus-card">
        <div className="focused-page-head">
          <div>
            <span className="source-pill">Jira</span>
            <h2>Import ticket details</h2>
            <p>Paste a ticket key or Jira URL. The platform maps the ticket into the analyst workflow.</p>
          </div>
        </div>
        <div className="large-import-panel">
          <label className="field-label" htmlFor="jira-import-input">Jira ticket key or URL</label>
          <div className="jira-import-controls">
            <input
              id="jira-import-input"
              type="text"
              value={importKey}
              onChange={(event) => onImportKeyChange(event.target.value)}
              placeholder="MBC-204 or Jira ticket URL"
            />
            <button className="btn primary" type="button" disabled={importLoading} onClick={onImportJira}>
              {importLoading ? "Importing..." : "Import ticket"}
            </button>
          </div>
          {importMessage && <div className="info-box">{importMessage}</div>}
          {importError && <ErrorBox message={importError} />}
        </div>
      </section>
    );
  }

  if (activeView === "manual") {
    return (
      <section className="focused-page-card manual-focus-card">
        <div className="focused-page-head">
          <div>
            <span className="source-pill">Manual</span>
            <h2>Create a manual analysis draft</h2>
            <p>Use this when the change request came from a meeting, email, or stakeholder discussion.</p>
          </div>
          <button className="btn primary" type="button" onClick={onManual}>
            Start manual intake
          </button>
        </div>
        <div className="manual-intake-preview">
          <span>Next step</span>
          <strong>Requirement Triage</strong>
          <p>The platform will open the AI readiness check before impact analysis starts.</p>
        </div>
      </section>
    );
  }

  if (activeView === "apps") {
    return (
      <section className="focused-page-card">
        <PlatformStatusStrip />
        <ConnectorSettingsCenter googleConnected={googleConnected} />
        <div className="connector-source-grid">
          {googleConnected && (
            <EmailTriagePanel
              messages={gmailMessages}
              loading={gmailLoading}
              message={gmailMessage}
              error={gmailError}
              unreadCount={gmailUnreadCount}
              onImport={onImportEmail}
            />
          )}
          {googleConnected && (
            <CalendarImportPanel
              events={calendarEvents}
              loading={calendarLoading}
              message={calendarMessage}
              error={calendarError}
              onImport={onImportCalendar}
            />
          )}
          {!googleConnected && (
            <div className="empty-monitor-state">
              Connect Google to show Gmail and Calendar import panels here.
            </div>
          )}
        </div>
      </section>
    );
  }

  return (
    <div className="repo-assistant-workspace chat-only">
      <section className="repo-chat-panel">
        <div className="repo-chat-head">
          <div>
            {/* <span className="source-pill">Repo AI</span> */}
            <h2>Ask about the current project</h2>
            <p>
              Ask before opening a ticket. Answers are grounded through the existing Code Q&A skill and project graph
              for {workspace?.name || "the connected repo"}.
            </p>
          </div>
          <div className="repo-chat-status">
            <span>{workspace?.name || "No project"}</span>
            <strong>{workspace?.index_status === "ready" ? "Graph ready" : "Graph may need indexing"}</strong>
          </div>
        </div>

        <div className="repo-chat-prompts">
          {repoChatPrompts(workspace).map((prompt) => (
            <button key={prompt} type="button" onClick={() => onAsk(prompt)} disabled={loading}>
              {prompt}
            </button>
          ))}
        </div>

        <div className="repo-chat-thread">
          {messages.length === 0 ? (
            <div className="repo-chat-empty">
              <strong>Start by asking where a feature lives, what depends on it, or what risk to check.</strong>
              <span>Example: "{repoChatPrompts(workspace)[0]}"</span>
            </div>
          ) : (
            messages.map((message) =>
              message.role === "user" ? (
                <div key={message.id} className="repo-chat-message user">
                  {message.question}
                </div>
              ) : (
                <div key={message.id} className="repo-chat-message assistant">
                  <strong>AI answer</strong>
                  <p>{message.result.answer || "(no answer)"}</p>
                  {(message.result.evidence || []).length > 0 && (
                    <div className="repo-chat-evidence">
                      {(message.result.evidence || []).slice(0, 4).map((item, index) => (
                        <span key={`${message.id}-evidence-${index}`}>
                          {item.source || item.claim || "Evidence"}
                        </span>
                      ))}
                    </div>
                  )}
                  {(message.result.ungrounded || []).length > 0 && (
                    <div className="repo-chat-warning">Some claims were not grounded in the project graph.</div>
                  )}
                </div>
              )
            )
          )}
          {loading && <div className="repo-chat-message assistant loading">Checking the project graph...</div>}
        </div>

        <div className="repo-chat-input">
          <textarea
            value={question}
            onChange={(event) => onQuestionChange(event.target.value)}
            placeholder="Ask about controllers, models, dependencies, known issues, or impact areas in this repo."
          />
          <button className="btn primary" type="button" disabled={loading} onClick={() => onAsk()}>
            {loading ? "Asking..." : "Ask Repo AI"}
          </button>
        </div>
        {error && <ErrorBox message={error} />}
        {latestAssistant?.artifact?.task_id && (
          <p className="muted-note">
            Last answer saved as artifact <code>{latestAssistant.artifact.task_id}</code>.
          </p>
        )}
      </section>

    </div>
  );
}

function ConnectorSettingsCenter({ googleConnected }) {
  const connectedCount = CONNECTOR_SETTINGS.filter((connector) =>
    connector.id === "google" ? googleConnected : ["jira", "github", "bitbucket", "hermes"].includes(connector.id)
  ).length;

  return (
    <section className="connector-settings-center">
      <div className="connector-settings-head">
        <div>
          <span className="source-pill">Connector settings</span>
          <h2>External platform setup</h2>
          <p>
            Configure the systems that feed tickets, repo evidence, PR comments, and Hermes handoff updates into the
            analyst workbench.
          </p>
        </div>
        <div className="connector-settings-metrics">
          <span>
            <strong>{connectedCount}</strong>
            Ready
          </span>
          <span>
            <strong>2</strong>
            Write-back
          </span>
          <span>
            <strong>1</strong>
            Agent bridge
          </span>
        </div>
      </div>
      <div className="connector-settings-grid">
        {CONNECTOR_SETTINGS.map((connector) => (
          <ConnectorSetupCard
            key={connector.id}
            connector={{
              ...connector,
              status: connector.id === "google" ? (googleConnected ? "Connected" : "Needs OAuth") : connector.status,
            }}
          />
        ))}
      </div>
    </section>
  );
}

function ConnectorSetupCard({ connector }) {
  const connected = !["Needs OAuth", "Not connected"].includes(connector.status);
  return (
    <article className={`connector-setup-card ${connector.id} ${connected ? "ready" : "attention"}`}>
      <div className="connector-setup-top">
        <span className={`platform-icon ${connector.id === "google" ? "calendar" : connector.id}`}>
          {connector.id === "github" ? (
            <span className="connector-letter">GH</span>
          ) : connector.id === "bitbucket" ? (
            <span className="connector-letter">BB</span>
          ) : connector.id === "hermes" ? (
            <span className="connector-letter">H</span>
          ) : connector.id === "google" ? (
            <PlatformLogo platform="calendar" />
          ) : (
            <PlatformLogo platform={connector.id} />
          )}
        </span>
        <div>
          <strong>{connector.label}</strong>
          <small>{connector.group}</small>
        </div>
        <span className={`status-pill ${connected ? "reviewed" : "unreviewed"}`}>{connector.status}</span>
      </div>
      <p>{connector.purpose}</p>
      <div className="connector-setup-meta">
        <span>{connector.mode}</span>
        <span>{connector.fields.length} setup fields</span>
      </div>
      <details className="connector-setup-details">
        <summary>View setup fields</summary>
        <div>
          {connector.fields.map((field) => (
            <span key={field}>{field}</span>
          ))}
        </div>
      </details>
    </article>
  );
}

function EmailTriagePanel({ messages, loading, message, error, unreadCount, onImport }) {
  const displayCount = unreadCount ?? messages.length;
  return (
    <div className="inbox-import-box email-triage-panel">
      <div className="panel-section-head">
        <div>
          <label className="field-label">Gmail requirement radar</label>
          <p>Unread threads that may need analyst triage.</p>
        </div>
        <span>{displayCount} unread</span>
      </div>
      {messages.length === 0 ? (
        <div className="empty-monitor-state">
          {displayCount > 0
            ? "Gmail has unread mail, but no previewable requirement thread was returned for quick import."
            : "No unread requirement emails found."}
        </div>
      ) : (
        <div className="email-triage-list">
          {messages.map((msg) => (
            <article key={msg.id} className="email-triage-card">
              <div className="email-avatar">{emailSenderInitial(msg.from)}</div>
              <div className="email-triage-body">
                <div className="email-triage-top">
                  <strong>{msg.subject}</strong>
                  <span>{formatEmailDate(msg.date)}</span>
                </div>
                <div className="email-sender">{emailSenderName(msg.from)}</div>
                {msg.snippet && <p>{msg.snippet}</p>}
                <div className="email-triage-actions">
                  <span>{emailCategory(msg)}</span>
                  <button className="btn ghost compact" type="button" disabled={loading} onClick={() => onImport(msg.id)}>
                    Import
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      {message && <div className="info-box">{message}</div>}
      {error && <ErrorBox message={error} />}
    </div>
  );
}

function CalendarImportPanel({ events, loading, message, error, onImport }) {
  const [mode, setMode] = useState("week");
  const normalizedEvents = events.map(normalizeCalendarEvent);
  const days = buildCalendarDays(normalizedEvents, mode);
  const todayKey = calendarDateKey(new Date());
  const todayEvents = normalizedEvents.filter((event) => event.dateKey === todayKey);
  const upcoming = normalizedEvents.slice(0, 4);

  return (
    <div className="inbox-import-box calendar-workspace-panel">
      <div className="panel-section-head">
        <div>
          <label className="field-label">Google Calendar monitor</label>
          <p>Requirement meetings and today notifications.</p>
        </div>
        <div className="calendar-toggle">
          <button className={mode === "week" ? "active" : ""} type="button" onClick={() => setMode("week")}>
            Week
          </button>
          <button className={mode === "month" ? "active" : ""} type="button" onClick={() => setMode("month")}>
            Month
          </button>
        </div>
      </div>
      <CalendarGrid days={days} compact={mode === "month"} />
      <div className="today-monitor-card">
        <strong>Today notifications</strong>
        {todayEvents.length === 0 ? (
          <span>No meetings scheduled today.</span>
        ) : (
          todayEvents.map((event) => (
            <button
              key={event.id}
              className="calendar-import-row"
              type="button"
              disabled={loading}
              onClick={() => onImport(event.id)}
            >
              <span>{event.time || "All day"}</span>
              <strong>{event.title}</strong>
            </button>
          ))
        )}
      </div>
      <div className="upcoming-import-list">
        <label className="field-label">Import meeting as work item</label>
        {upcoming.length === 0 ? (
          <span className="muted-note">No upcoming events.</span>
        ) : (
          upcoming.map((event) => (
            <button
              key={event.id}
              className="calendar-import-row"
              type="button"
              disabled={loading}
              onClick={() => onImport(event.id)}
            >
              <span>
                {event.day} {event.time}
              </span>
              <strong>{event.title}</strong>
            </button>
          ))
        )}
      </div>
      {message && <div className="info-box">{message}</div>}
      {error && <ErrorBox message={error} />}
    </div>
  );
}

function RequirementPhase({ ticket, onTicketChange, reqArtifact, reqStatus, onBackToInbox, onArtifact, onReview }) {
  const reviewed = Boolean(reqArtifact?.reviewed);
  const reviewBlocked = reqStatus === "NEEDS_CLARIFICATION";
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 2 - Requirement Triage"
        title="Check if this ticket is ready"
        subtitle="Review the business request first. AI checks clarity, missing information, business value, scope, and risk before impact analysis starts."
      />
      {!reqArtifact && <TicketSourceCard ticket={ticket} onBack={onBackToInbox} />}
      {!reqArtifact && <TicketIntakeForm ticket={ticket} onChange={onTicketChange} onArtifact={onArtifact} />}
      {reqArtifact && (
        <>
          <RequirementAnalysisReport
            artifact={reqArtifact}
            result={reqArtifact.result || {}}
            onArtifact={onArtifact}
            reviewed={reviewed}
            reviewBlocked={reviewBlocked}
            onReview={onReview}
          />
        </>
      )}
    </section>
  );
}

function TicketSourceCard({ ticket, onBack }) {
  return (
    <section className="ticket-source-card ticket-snapshot-card">
      <div className="ticket-snapshot-main">
        <div className="ticket-snapshot-top">
          <span className="source-pill">{ticket.sourceType || "Manual"}</span>
          {ticket.ticketKey && <span className="ticket-key-pill">{ticket.ticketKey}</span>}
          {ticket.priority && <span className={`ticket-priority-pill ${ticket.priority.toLowerCase()}`}>{ticket.priority}</span>}
        </div>
        <h2>{ticket.ticketTitle || "Untitled change request"}</h2>
        <p>{ticket.description || "No description has been mapped yet. Add the business request before running AI review."}</p>
      </div>
      <div className="ticket-source-meta">
        {ticket.reporter && <span>Reporter: {ticket.reporter}</span>}
        {ticket.receivedAt && <span>{ticket.receivedAt}</span>}
        {ticket.sourceUrl && (
          <a href={ticket.sourceUrl} target="_blank" rel="noreferrer">
            Open source
          </a>
        )}
        <button className="btn ghost compact" type="button" onClick={onBack}>
          Back to inbox
        </button>
      </div>
    </section>
  );
}

function ProjectContextCard() {
  return (
    <section className="project-context-panel">
      <div>
        <label className="field-label">Target project context</label>
        <h2>{TARGET_PROJECT.name}</h2>
        <p>{TARGET_PROJECT.framework}</p>
      </div>
      <div className="project-context-meta">
        <span>{TARGET_PROJECT.source}</span>
        <div className="module-chip-row">
          {TARGET_PROJECT.modules.map((module) => (
            <span key={module} className="module-chip">
              {module}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

function TicketIntakeForm({ ticket, onChange, onArtifact }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  function update(field, value) {
    onChange({ ...ticket, [field]: value });
  }

  async function submit() {
    setError("");
    if (!ticket.description.trim() && !ticket.ticketTitle.trim()) {
      setError("Ticket title or description is required.");
      return;
    }
    setLoading(true);
    try {
      const response = await api("/api/skills/requirement-analysis", {
        method: "POST",
        body: {
          profile: ANALYST_PROFILE,
          ticket_key: ticket.ticketKey,
          ticket_title: ticket.ticketTitle,
          priority: ticket.priority,
          reporter: ticket.reporter,
          source_type: ticket.sourceType,
          source_name: ticket.sourceName,
          source_url: ticket.sourceUrl,
          received_at: ticket.receivedAt,
          description: ticket.description,
          acceptance_criteria: ticket.acceptanceCriteria,
          comments: ticket.comments,
          code_snippet: ticket.codeSnippet,
          code_evidence_url: ticket.codeEvidenceUrl,
        },
      });
      onArtifact(normalizeRequirementResponse(response));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="ticket-triage-workspace">
      <article className="triage-action-card">
        <div>
          <span className="source-pill">AI readiness check</span>
          <h2>Can this ticket move forward?</h2>
          <p>
            The platform will read the ticket and check whether the analyst has enough information to continue to
            impact analysis.
          </p>
        </div>
        <div className="triage-check-grid">
          <span>Requirement clarity</span>
          <span>Missing information</span>
          <span>Business value</span>
          <span>Scope boundary</span>
          <span>Risk priority</span>
          <span>Testing concern</span>
        </div>
        <div className="triage-next-step">
          <strong>Output</strong>
          <span>Ready for impact analysis, or clarification needed with questions to ask.</span>
        </div>
        {error && <ErrorBox message={error} />}
        <div className="action-row">
          <button className="btn primary" type="button" disabled={loading} onClick={submit}>
            {loading ? "Reviewing..." : "Review Ticket with AI"}
          </button>
          <button className="btn ghost compact" type="button" onClick={() => onChange(SAMPLE_TICKET)}>
            Load sample
          </button>
        </div>
      </article>
      <article className="ai-restricted-card">
        <div>
          <span className="source-pill">GitHub read-only / restricted code</span>
          <h2>Use copied code evidence when the repo cannot be opened locally</h2>
          <p>
            If this platform isn't allowed to scan your repo automatically at all, skip Connect Project entirely —
            Paste the relevant GitHub file link, PR diff, function, class, or config block here. The platform reads it
            as limited evidence without cloning or indexing the full repo.
          </p>
          <p className="muted-note">
            The same idea applies to any connected system that can't be granted full workspace-root access — e.g. a
            Hermes deployment where a team isn't ready to hand an autonomous agent the whole repo. Paste the specific
            evidence instead of connecting the project.
          </p>
        </div>
        <label className="restricted-code-field">
          GitHub / PR / file URL
          <input
            type="text"
            value={ticket.codeEvidenceUrl || ""}
            onChange={(event) => update("codeEvidenceUrl", event.target.value)}
            placeholder="https://github.com/org/repo/blob/main/app/Http/Controllers/DonationController.php"
          />
        </label>
        <textarea
          className="compact-textarea code-snippet-textarea"
          value={ticket.codeSnippet || ""}
          onChange={(event) => update("codeSnippet", event.target.value)}
          placeholder="Paste the relevant function, class, config block, or PR diff here"
        />
        <div className="limited-evidence-note">
          <strong>Limited evidence mode</strong>
          <span>Impact analysis will be lower-confidence unless a full project is connected and indexed.</span>
        </div>
      </article>
      <details className="ticket-edit-drawer">
        <summary>
          <span>Edit ticket details</span>
          <small>Use this only when imported fields need cleanup before AI review.</small>
        </summary>
        <div className="ticket-edit-body">
          <div className="ticket-meta-grid">
            <label>
              Ticket key
              <input
                type="text"
                value={ticket.ticketKey}
                onChange={(event) => update("ticketKey", event.target.value)}
                placeholder="PAY-102"
              />
            </label>
            <label>
              Priority
              <select value={ticket.priority} onChange={(event) => update("priority", event.target.value)}>
                <option>Low</option>
                <option>Medium</option>
                <option>High</option>
                <option>Critical</option>
              </select>
            </label>
            <label>
              Reporter
              <input
                type="text"
                value={ticket.reporter}
                onChange={(event) => update("reporter", event.target.value)}
                placeholder="Product owner / stakeholder"
              />
            </label>
          </div>
          <label>
            Ticket title
            <input
              type="text"
              value={ticket.ticketTitle}
              onChange={(event) => update("ticketTitle", event.target.value)}
              placeholder="Short summary from Jira, email, or meeting note"
            />
          </label>
          <label>
            Description
            <textarea
              value={ticket.description}
              onChange={(event) => update("description", event.target.value)}
              placeholder="Describe the requested business change"
            />
          </label>
          <label>
            Acceptance criteria
            <textarea
              className="compact-textarea"
              value={ticket.acceptanceCriteria}
              onChange={(event) => update("acceptanceCriteria", event.target.value)}
              placeholder="Given / when / then, validation rules, or expected outcome"
            />
          </label>
          <label>
            Comments / clarification notes
            <textarea
              className="compact-textarea"
              value={ticket.comments}
              onChange={(event) => update("comments", event.target.value)}
              placeholder="Stakeholder comments, email notes, or meeting follow-up"
            />
          </label>
        </div>
      </details>
    </section>
  );
}

function ImpactPhase({ loading, error, artifact, onRetry, onReview, onBack, onNext }) {
  const result = artifact?.result || {};
  const reviewed = Boolean(artifact?.reviewed);
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 3 - Impact Analysis"
        title="Scope the blast radius"
        subtitle="Runs automatically against the reviewed requirement text, grounded in the project graph via MCP."
      />
      {loading && <p className="muted">Running impact analysis…</p>}
      {error && (
        <>
          <ErrorBox message={error} />
          <div className="action-row">
            <button className="btn ghost" type="button" onClick={onRetry}>
              Retry
            </button>
          </div>
        </>
      )}
      {artifact && !loading && (
        <>
          <ImpactVisualSummary result={result} />
          <EvidenceTraceabilityPanel
            title="Impact evidence traceability"
            subtitle="Connects each affected area or risk back to codebase, memory, or missing evidence."
            items={buildImpactTraceability(result)}
          />
          <section className="triage-detail-stack impact-detail-stack">
            <details className="triage-detail-panel">
              <summary>
                <span>Affected module evidence</span>
                <small>{(result.affected_modules || []).length} modules</small>
              </summary>
              <EvidenceList title="Affected modules" items={result.affected_modules || []} sourceKey="path" claimKey="reason" />
            </details>
            <details className="triage-detail-panel">
              <summary>
                <span>Risks & missing evidence</span>
                <small>{(result.risk_notes || []).length} notes / {(result.missing_evidence || []).length} missing</small>
              </summary>
              <EvidenceList title="Related historical issues" items={result.risk_notes || []} sourceKey="evidence" claimKey="note" />
              {(result.missing_evidence || []).length > 0 && <SimpleList title="Missing evidence" items={result.missing_evidence} tone="danger" />}
            </details>
          </section>
          <div className="action-row">
            <span className={`status-pill ${reviewed ? "reviewed" : "unreviewed"}`}>{reviewed ? "Reviewed" : "Unreviewed"}</span>
            <button className="btn primary" type="button" disabled={reviewed} onClick={onReview}>
              {reviewed ? "Reviewed" : "Mark as reviewed"}
            </button>
            <button className="btn ghost" type="button" onClick={onBack}>
              Back to requirement
            </button>
            <button className="btn ghost push" type="button" disabled={!reviewed} onClick={onNext}>
              Continue to test scenarios
            </button>
          </div>
        </>
      )}
    </section>
  );
}

function TestPhase({ impactArtifact, testArtifacts, testScopeArtifacts, onGenerate, onSaveTestScope, onReviewTestScope, onBack, onNext }) {
  const modules = impactArtifact?.result?.affected_modules || [];
  const [loadingTarget, setLoadingTarget] = useState("");
  const reviewedScopeCount = testArtifacts.filter((item) => testScopeArtifacts[item.task_id]?.reviewed).length;
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 4 - Test Scenarios"
        title="Prepare the testing scope"
        subtitle="Generate scenarios from impacted modules, then accept, reject, edit, and prioritize cases before handoff."
      />
      <TestingCoverageMap modules={modules} testArtifacts={testArtifacts} reviewedScopeCount={reviewedScopeCount} />
      {modules.length === 0 && <SimpleList title="Affected modules" items={["No affected modules resolved in the project graph."]} />}
      {modules.length > 0 && (
        <details className="triage-detail-panel test-target-panel" open={testArtifacts.length === 0}>
          <summary>
            <span>Generate more test plans</span>
            <small>{modules.length} impacted areas</small>
          </summary>
          <div className="test-target-grid">
          {modules.map((item) => {
            const done = testArtifacts.some((entry) => entry.result?.target === item.name);
            return (
              <button
                key={item.name}
                type="button"
                className={`test-target-card ${done ? "active" : ""}`}
                disabled={loadingTarget === item.name}
                onClick={async () => {
                  setLoadingTarget(item.name);
                  try {
                    await onGenerate(item.name);
                  } finally {
                    setLoadingTarget("");
                  }
                }}
              >
                <strong>{item.name}</strong>
                <span>{loadingTarget === item.name ? "Generating…" : done ? "Generated - click to regenerate" : item.reason}</span>
              </button>
            );
          })}
          </div>
        </details>
      )}
      {testArtifacts.map((item) => (
        <section key={item.task_id} className="test-plan-section">
          <div className="test-plan-title">
            <span className="source-pill">Test plan</span>
            <h3>{item.result?.target}</h3>
          </div>
          <h3>Test plan · {item.result?.target}</h3>
          <TestGenReport result={item.result || {}} />
          <TestScopeManager
            testArtifact={item}
            scopeArtifact={testScopeArtifacts[item.task_id]}
            onSave={onSaveTestScope}
            onReview={onReviewTestScope}
          />
        </section>
      ))}
      <div className="action-row">
        <button className="btn ghost" type="button" onClick={onBack}>
          Back to impact analysis
        </button>
        <button className="btn primary push" type="button" disabled={testArtifacts.length === 0} onClick={onNext}>
          {reviewedScopeCount > 0 ? "Continue with reviewed test scope" : "Continue with generated tests"}
        </button>
      </div>
    </section>
  );
}

function TestingCoverageMap({ modules, testArtifacts, reviewedScopeCount }) {
  const generatedTargets = new Set(testArtifacts.map((item) => item.result?.target).filter(Boolean));
  const missingTargets = modules.filter((item) => item.name && !generatedTargets.has(item.name));
  const generatedCases = testArtifacts.flatMap((item) => item.result?.cases || []);
  const missingEvidenceCount = testArtifacts.reduce((count, item) => count + (item.result?.missing_evidence || []).length, 0);
  return (
    <section className="testing-coverage-map">
      <div className="testing-map-head">
        <div>
          <span className="source-pill">Testing coverage map</span>
          <h2>Impact area to QA/UAT scope</h2>
          <p>Generate only useful coverage, then accept must-test items and move low-value cases to backlog.</p>
        </div>
        <div className="testing-map-metrics">
          <span>
            <strong>{modules.length}</strong>
            Impact areas
          </span>
          <span>
            <strong>{testArtifacts.length}</strong>
            Test plans
          </span>
          <span>
            <strong>{generatedCases.length}</strong>
            Cases
          </span>
          <span>
            <strong>{reviewedScopeCount}</strong>
            Reviewed
          </span>
        </div>
      </div>
      <div className="testing-flow-map">
        <div className="testing-flow-node">
          <span>Input</span>
          <strong>{modules.length} impacted areas</strong>
          <small>From impact analysis</small>
        </div>
        <div className="impact-flow-arrow">-&gt;</div>
        <div className="testing-flow-node">
          <span>AI suggested</span>
          <strong>{generatedCases.length} test cases</strong>
          <small>{missingEvidenceCount > 0 ? `${missingEvidenceCount} missing evidence warning(s)` : "Evidence checked"}</small>
        </div>
        <div className="impact-flow-arrow">-&gt;</div>
        <div className="testing-flow-node">
          <span>Analyst decision</span>
          <strong>Accept / backlog / reject</strong>
          <small>Prepare QA and UAT scope</small>
        </div>
      </div>
      {missingTargets.length > 0 && (
        <div className="testing-map-warning">
          <strong>{missingTargets.length} impacted area(s) still have no generated tests.</strong>
          <span>Generate only the areas that matter for this change.</span>
        </div>
      )}
    </section>
  );
}

function ReportPhase({
  ticket,
  reqArtifact,
  impactArtifact,
  testArtifacts,
  testScopeArtifacts,
  summaryArtifact,
  summaryHandoffs,
  onGenerateSummary,
  onReviewSummary,
  onReloadSummaryHandoffs,
  onRestart,
  onBack,
}) {
  const reqResult = reqArtifact?.result || {};
  const impactResult = impactArtifact?.result || {};
  const ticketText = formatTicketInput(ticket);
  const reviewedScopeCount = testArtifacts.filter((item) => testScopeArtifacts[item.task_id]?.reviewed).length;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 5 - Handoff Summary"
        title="Compiled handoff summary"
        subtitle="Generate a reviewable artifact that can be shared with PM, developer, tester, or supervisor."
      />
      {!summaryArtifact && (
        <>
          <div className="answer-panel">{ticketText}</div>
          <div className="stat-grid">
            <Stat
              label="Requirement status"
              value={<span className="tag reviewed">{reqArtifact ? formatStatus(getRequirementStatus(reqArtifact)) : "-"}</span>}
            />
            <Stat label="Risk level" value={<Tag kind="risk" value={impactResult.risk_level} />} />
            <Stat label="Test plans generated" value={testArtifacts.length} />
            <Stat label="Reviewed test scopes" value={reviewedScopeCount} />
          </div>
          <SimpleList title="Business rules" items={reqResult.business_rules || []} />
          <RequirementDecisionPanel result={reqResult} />
          <ProjectRisks items={reqResult.project_risks || []} />
          <SimpleList title="Assumptions" items={reqResult.assumptions || []} />
          <EvidenceList title="Affected modules" items={impactResult.affected_modules || []} sourceKey="path" claimKey="reason" />
          <SimpleList
            title="Test scenarios prepared"
            items={testArtifacts.map((item) => {
              const scope = testScopeArtifacts[item.task_id]?.result;
              return scope
                ? `${scope.target}: ${scope.accepted_count || 0} accepted, ${scope.backlog_count || 0} backlog`
                : `${item.result?.target}: ${(item.result?.cases || []).length} generated case(s)`;
            })}
          />
          {error && <ErrorBox message={error} />}
          <div className="action-row">
            <button className="btn ghost" type="button" onClick={onBack}>
              Back to test scenarios
            </button>
            <button
              className="btn primary"
              type="button"
              disabled={loading}
              onClick={async () => {
                setError("");
                setLoading(true);
                try {
                  await onGenerateSummary();
                } catch (err) {
                  setError(err.message);
                } finally {
                  setLoading(false);
                }
              }}
            >
              {loading ? "Generating..." : "Generate Handoff Summary"}
            </button>
            <button className="btn ghost" type="button" onClick={onRestart}>
              Start new analysis
            </button>
          </div>
        </>
      )}
      {summaryArtifact && (
        <>
          <div className="report-header">
            <div>
              <div className="eyebrow">Persisted artifact</div>
              <h1>Handoff Summary</h1>
              <p className="muted">Task ID: {summaryArtifact.task_id}</p>
            </div>
            <div className="review-actions">
              <span className={`status-pill ${summaryArtifact.reviewed ? "reviewed" : "unreviewed"}`}>
                {summaryArtifact.reviewed ? "Reviewed" : "Unreviewed"}
              </span>
              <button className="btn primary" type="button" disabled={summaryArtifact.reviewed} onClick={onReviewSummary}>
                {summaryArtifact.reviewed ? "Reviewed" : "Mark summary as reviewed"}
              </button>
            </div>
          </div>
          <HandoffSummaryReport result={summaryArtifact.result || {}} />
          {summaryArtifact.reviewed ? (
            <ExternalHandoff
              artifact={summaryArtifact}
              handoffs={summaryHandoffs}
              onReload={() => onReloadSummaryHandoffs(summaryArtifact.task_id)}
              initialSummary="Reviewed analyst handoff summary"
            />
          ) : (
            <section className="handoff-panel">
              <h3>External handoff</h3>
              <p className="muted">Mark the handoff summary as reviewed before creating a Jira issue or posting to a PR.</p>
            </section>
          )}
          <details className="raw">
            <summary>View raw handoff-summary artifact</summary>
            <pre>{JSON.stringify(summaryArtifact, null, 2)}</pre>
          </details>
          <div className="action-row">
            <button className="btn ghost" type="button" onClick={onBack}>
              Back to test scenarios
            </button>
            <button className="btn ghost" type="button" onClick={onRestart}>
              Start new analysis
            </button>
          </div>
        </>
      )}
    </section>
  );
}

function HandoffSummaryReport({ result }) {
  return (
    <>
      <section className="answer-panel">{result.requirement_summary || "(no requirement summary)"}</section>
      <div className="stat-grid">
        <Stat label="Risk level" value={<Tag kind="risk" value={result.risk_level} />} />
        <Stat label="Effort estimate" value={result.effort_estimate || "unknown"} />
        <Stat label="Test plans" value={(result.test_plans || []).length} />
      </div>
      <SimpleList title="Business rules" items={result.business_rules || []} />
      <SimpleList title="Clarifications" items={result.clarifications || []} />
      <SimpleList title="Assumptions" items={result.assumptions || []} />
      <EvidenceList title="Impact areas" items={result.impact_areas || []} sourceKey="path" claimKey="reason" />
      <EvidenceList title="Risk notes" items={result.risk_notes || []} sourceKey="evidence" claimKey="note" />
      <SimpleList
        title="Testing scope"
        items={(result.test_plans || []).map(
          (item) => `${item.target}: ${item.case_count} case(s); regression checks: ${(item.regression_checklist || []).length}`
        )}
      />
      <SimpleList title="Open questions" items={result.open_questions || []} tone={(result.open_questions || []).length > 0 ? "danger" : undefined} />
      <EvidenceList title="Evidence" items={result.evidence || []} sourceKey="source" claimKey="claim" />
    </>
  );
}

function ProjectTrackerPage({ workspace, onWorkspaceUpdated, onSwitchProject, onBack }) {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [hermesTasks, setHermesTasks] = useState([]);
  const [ticketTracker, setTicketTracker] = useState([]);
  const [selectedTicketId, setSelectedTicketId] = useState("");

  useEffect(() => {
    loadProjects();
    loadHermesStatuses();
    loadTicketTracker();
    const interval = setInterval(() => {
      loadHermesStatuses();
      loadTicketTracker();
    }, 8000);
    return () => clearInterval(interval);
  }, [workspace?.local_path]);

  function loadProjects() {
    api("/api/workspace")
      .then(setProjects)
      .catch(() => setProjects([]));
  }

  function loadHermesStatuses() {
    const query = workspace?.local_path ? `?project=${encodeURIComponent(workspace.local_path)}` : "";
    api(`/api/hermes/status/current${query}`)
      .then(setHermesTasks)
      .catch(() => setHermesTasks([]));
  }

  function loadTicketTracker() {
    const query = workspace?.local_path ? `?project=${encodeURIComponent(workspace.local_path)}` : "";
    api(`/api/tickets/tracker${query}`)
      .then((tickets) => {
        setTicketTracker(tickets);
        setSelectedTicketId((current) =>
          tickets.some((ticket) => ticket.task_id === current) ? current : tickets[0]?.task_id || "",
        );
      })
      .catch(() => setTicketTracker([]));
  }

  async function activateProject(id) {
    setError("");
    setLoading(true);
    try {
      const saved = await api(`/api/workspace/${id}/activate`, { method: "POST" });
      onWorkspaceUpdated?.(saved);
      loadProjects();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  const displayProjects = projects.length
    ? projects
    : workspace
      ? [workspace]
      : [{ id: "empty", name: "No project connected", local_path: "Connect a repo first" }];
  const activeProject = displayProjects.find((project) => project.active) || workspace;
  const connectedCount = displayProjects.filter((project) => project.id !== "empty").length;
  const readyProjectCount = displayProjects.filter(
    (project) => project.index_status === "ready" && project.graphify_index_status === "ready",
  ).length;
  const attentionCount = displayProjects.filter(
    (project) => project.index_status === "failed" || project.graphify_index_status === "failed" || project.id === "empty",
  ).length;
  const selectedTicket =
    ticketTracker.find((ticket) => ticket.task_id === selectedTicketId) || ticketTracker[0];
  const phaseStates = selectedTicket?.phases || [];
  const completedPhaseCount = phaseStates.filter((phase) => phase.state === "done").length;
  const pendingPhaseCount = phaseStates.filter((phase) => phase.state === "pending").length;
  const progressPercent = phaseStates.length
    ? Math.round((completedPhaseCount / phaseStates.length) * 100)
    : 0;
  const currentPhase = phaseStates.find((phase) => phase.state === "active") || phaseStates[phaseStates.length - 1];
  const readyStatus = activeProject?.index_status === "ready" ? "Repo graph ready" : "Repo graph needs setup";
  const diagramStatus = activeProject?.graphify_index_status === "ready" ? "Diagram graph ready" : "Diagram graph optional";

  return (
    <section className="screen project-monitor-page project-tracker-page">
      <HeaderBlock
        eyebrow="Project tracker"
        title="Project delivery control"
        subtitle="Track connected projects, analysis readiness, and the current delivery phase before work is synced to Jira, QA, or Hermes."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />
      <div className="tracker-command-strip">
        <div>
          <span className="source-pill">Active workspace</span>
          <h2>{activeProject?.name || "No project selected"}</h2>
          <p>{activeProject?.local_path || "Connect a project before starting analyst tracking."}</p>
        </div>
        <div className="tracker-kpi-grid">
          <Stat label="Projects" value={String(connectedCount)} />
          <Stat label="Ready graphs" value={String(readyProjectCount)} />
          <Stat label="Needs action" value={String(attentionCount)} />
          <Stat label="Active phase" value="Dev" />
        </div>
      </div>

      <div className="project-tracker-grid">
        <section className="tracker-panel project-portfolio-panel">
          <div className="tracker-panel-head">
            <div>
              <span className="source-pill">Portfolio</span>
              <h2>Connected project portfolio</h2>
              <p>Switch which repo powers Repo AI, impact analysis, project overview, and ticket workflow context.</p>
            </div>
            <button className="btn ghost compact" type="button" onClick={onSwitchProject}>
              Connect project
            </button>
          </div>
          <div className="project-switch-list">
            <div className="portfolio-selector-panel">
              <label className="field-label" htmlFor="project-tracker-project-select">
                Active project
              </label>
              <div className="premium-select-row">
                <select
                  id="project-tracker-project-select"
                  value={activeProject?.id || ""}
                  disabled={loading || displayProjects[0]?.id === "empty"}
                  onChange={(event) => {
                    const id = event.target.value;
                    if (id && id !== activeProject?.id) activateProject(id);
                  }}
                >
                  {displayProjects.map((project) => (
                    <option key={project.id} value={project.id}>
                      {project.name}
                    </option>
                  ))}
                </select>
                <span>{loading ? "Switching..." : "Synced workspace"}</span>
              </div>
            </div>
            <article className="active-portfolio-card">
              <div>
                <span className="source-pill">Current workspace</span>
                <h3>{activeProject?.name || "No project selected"}</h3>
                <p>{activeProject?.local_path || "Connect a repo first."}</p>
              </div>
              <div className="portfolio-health-grid">
                <span>
                  <strong>{readyStatus}</strong>
                  {indexStatusLabel(activeProject?.index_status, activeProject?.index_error)}
                </span>
                <span>
                  <strong>{diagramStatus}</strong>
                  {indexStatusLabel(activeProject?.graphify_index_status, activeProject?.graphify_index_error)}
                </span>
              </div>
            </article>
            {displayProjects.map((project) => (
              <article key={project.id} className={project.active ? "active" : ""}>
                <div>
                  <strong>{project.name}</strong>
                  <span>{project.local_path}</span>
                  {project.index_status && (
                    <small>
                      Code graph: {indexStatusLabel(project.index_status, project.index_error)} · Diagram graph:{" "}
                      {indexStatusLabel(project.graphify_index_status, project.graphify_index_error)}
                    </small>
                  )}
                </div>
                {project.active ? (
                  <span className="tag good">Active</span>
                ) : project.id !== "empty" ? (
                  <button className="btn ghost compact" type="button" disabled={loading} onClick={() => activateProject(project.id)}>
                    Switch project
                  </button>
                ) : (
                  <button className="btn ghost compact" type="button" onClick={onSwitchProject}>
                    Connect project
                  </button>
                )}
              </article>
            ))}
          </div>
          {error && <ErrorBox message={error} />}
        </section>

        <section className="tracker-panel phase-command-panel">
          <div className="tracker-panel-head">
            <div>
              <span className="source-pill">Delivery flow</span>
              <h2>Work phase monitor</h2>
              <p>Per-ticket view — pick a ticket to see requirement review, impact analysis, development, testing, handoff, and Jira/UI sync.</p>
            </div>
          </div>
          {ticketTracker.length === 0 && (
            <p className="muted-note">No tickets yet — run a requirement analysis to see it tracked here.</p>
          )}
          {ticketTracker.length > 0 && (
            <div className="phase-monitor-control">
              <label className="field-label" htmlFor="project-tracker-ticket-select">
                Tracked ticket
              </label>
              <select
                id="project-tracker-ticket-select"
                value={selectedTicket?.task_id || ""}
                onChange={(event) => setSelectedTicketId(event.target.value)}
              >
                {ticketTracker.map((ticket) => (
                  <option key={ticket.task_id} value={ticket.task_id}>
                    {ticket.title}
                  </option>
                ))}
              </select>
            </div>
          )}
          {selectedTicket && (
            <section className="phase-monitor-hero">
              <div>
                <div className="phase-ticket-label">
                  <span className={`ticket-type-dot ${selectedTicket.ticket_type}`} aria-hidden="true" />
                  <small>{selectedTicket.ticket_type === "change_request" ? "Change request" : "Ticket"}</small>
                </div>
                <h3>{selectedTicket.title}</h3>
                <p>Current phase: {currentPhase?.name || "Waiting for workflow activity"}</p>
              </div>
              <div className="phase-progress-ring" style={{ "--progress": `${progressPercent}%` }}>
                <strong>{progressPercent}%</strong>
                <span>complete</span>
              </div>
            </section>
          )}
          <div className="phase-monitor-metrics">
            <span>
              <strong>{completedPhaseCount}</strong>
              Done
            </span>
            <span>
              <strong>{currentPhase?.name || "-"}</strong>
              Current phase
            </span>
            <span>
              <strong>{pendingPhaseCount}</strong>
              Pending
            </span>
          </div>
          <div className="tracker-ticket-tabs">
            {ticketTracker.map((ticket) => (
              <button
                key={ticket.task_id}
                type="button"
                className={ticket.task_id === selectedTicket?.task_id ? "active" : ""}
                onClick={() => setSelectedTicketId(ticket.task_id)}
              >
                <span className={`ticket-type-dot ${ticket.ticket_type}`} aria-hidden="true" />
                <span>{ticket.title}</span>
              </button>
            ))}
          </div>
          <div className="tracker-phase-timeline phase-board">
            {phaseStates.map((phase, index) => (
              <article key={phase.name} className={phase.state}>
                <span>{index + 1}</span>
                <div>
                  <strong>{phase.name}</strong>
                  <small>
                    {phase.state === "done"
                      ? "Completed"
                      : phase.state === "active"
                        ? "Current focus"
                        : phase.state === "skipped"
                          ? "Skipped — went straight to Hermes"
                          : "Pending"}
                  </small>
                </div>
              </article>
            ))}
          </div>
          <div className="tracker-next-action-panel">
            <div>
              <span className="source-pill">Next action</span>
              <strong>Review current ticket status before closing the handoff.</strong>
            </div>
            <p>Get ticket -&gt; requirement review -&gt; impact analysis -&gt; development/fix -&gt; testing sync -&gt; Jira/UI sync -&gt; complete.</p>
          </div>
        </section>
      </div>

      <section className="tracker-panel hermes-status-panel">
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">Hermes bridge</span>
            <h2>Hermes handoff status</h2>
            <p>Live progress reported back by Hermes, scoped to {activeProject?.name || "the active project"}.</p>
          </div>
        </div>
        {hermesTasks.length === 0 ? (
          <p className="muted-note">No task has been sent to Hermes yet, or Hermes hasn't reported a status back.</p>
        ) : (
          <div className="project-switch-list">
            {hermesTasks.map((task) => (
              <article key={task.source_task_id}>
                <div>
                  <strong>Task {task.source_task_id.slice(0, 8)}</strong>
                  <span>{task.note || "No note from Hermes."}</span>
                </div>
                <span className="tag good">{task.status}</span>
              </article>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}

function TicketKanbanPage({ workspace, onBack }) {
  const [tickets, setTickets] = useState([]);
  const [hermesTasks, setHermesTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    loadBoard();
    const interval = setInterval(loadBoard, 8000);
    return () => clearInterval(interval);
  }, [workspace?.local_path]);

  function loadBoard() {
    const query = workspace?.local_path ? `?project=${encodeURIComponent(workspace.local_path)}` : "";
    Promise.all([
      api(`/api/tickets/tracker${query}`).catch(() => []),
      api(`/api/hermes/status/current${query}`).catch(() => []),
    ])
      .then(([ticketRows, hermesRows]) => {
        setTickets(ticketRows);
        setHermesTasks(hermesRows);
        setError("");
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  const cards = [];
  for (const ticket of tickets) {
    const columnId = kanbanColumnForTicket(ticket);
    if (!columnId) continue;
    const doneCount = (ticket.phases || []).filter((phase) => phase.state === "done").length;
    const activePhase = (ticket.phases || []).find((phase) => phase.state === "active");
    cards.push({
      id: ticket.task_id,
      columnId,
      kind: "mini",
      title: ticket.title,
      badge: ticket.ticket_type === "change_request" ? "Change request" : "Issue",
      stageLabel: activePhase?.name || "Jira / UI Sync",
      updatedAt: ticket.updated_at,
      progressPercent: Math.round((doneCount / (ticket.phases || []).length) * 100) || 0,
      note: null,
    });
  }
  for (const task of hermesTasks) {
    const columnId = kanbanColumnForHermesTask(task);
    if (!columnId) continue;
    const orderIndex = HERMES_STATUS_ORDER.indexOf(task.status);
    cards.push({
      id: task.source_task_id,
      columnId,
      kind: "hermes",
      title: `Hermes incident ${task.source_task_id.slice(0, 8)}`,
      badge: "Hermes",
      stageLabel: task.status,
      updatedAt: task.create_date,
      progressPercent: Math.round((orderIndex / (HERMES_STATUS_ORDER.length - 1)) * 100),
      note: task.note,
    });
  }
  const cardsByColumn = Object.fromEntries(
    KANBAN_COLUMN_META.map((column) => [
      column.id,
      cards
        .filter((card) => card.columnId === column.id)
        .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0)),
    ]),
  );

  const totalTickets = cards.length;
  const activeTickets = totalTickets - cardsByColumn.done.length;
  const reviewTickets = cardsByColumn.review.length;
  const doneTickets = cardsByColumn.done.length;

  return (
    <section className="screen project-monitor-page">
      <HeaderBlock
        eyebrow="Ticket Kanban"
        title="Project ticket control board"
        subtitle="Real progress merged from mini-Project's own review phases and Hermes's reported incident status — no placeholder tickets."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />

      <div className="kanban-command-panel">
        <div>
          <span className="source-pill">Selected project</span>
          <h2>{workspace?.name || "No project connected"}</h2>
          {workspace?.local_path && <p>{workspace.local_path}</p>}
        </div>
        <div className="kanban-sync-strip">
          <span>mini-Project tickets: review-gated</span>
          <span>Hermes incidents: accepted onward</span>
        </div>
      </div>

      {error && <ErrorBox message={error} />}

      <div className="kanban-status-grid">
        <Stat label="Total tickets" value={String(totalTickets)} />
        <Stat label="Active work" value={String(activeTickets)} />
        <Stat label="Needs review" value={String(reviewTickets)} />
        <Stat label="Completed" value={String(doneTickets)} />
      </div>

      <div className="kanban-workflow-strip">
        {["Intake", "Analysis", "Development", "Testing", "Review", "Report"].map((step, index) => (
          <span key={step}>
            <strong>{index + 1}</strong>
            {step}
          </span>
        ))}
      </div>

      {!loading && totalTickets === 0 && !error && (
        <p className="muted-note">
          No ticket has passed requirement review yet, and no Hermes incident has been accepted. Review a requirement
          analysis or wait for Hermes to accept a handoff to see cards here.
        </p>
      )}

      <div className="formal-kanban-board">
        {KANBAN_COLUMN_META.map((column) => (
          <section key={column.id} className={`formal-kanban-column kanban-${column.id}`}>
            <div className="formal-kanban-column-head">
              <div>
                <span>{column.title}</span>
                <small>{column.summary}</small>
              </div>
              <strong>{cardsByColumn[column.id].length}</strong>
            </div>
            {cardsByColumn[column.id].map((card) => (
              <article key={card.id} className="formal-ticket-card">
                <div className="formal-ticket-topline">
                  <span className="ticket-key">{card.id.slice(0, 8)}</span>
                  <span className={`kanban-source-badge source-${card.kind}`}>{card.badge}</span>
                </div>
                <h3>{card.title}</h3>
                <div className="ticket-meta-row">
                  {card.updatedAt && <span>Updated {formatRelativeTime(card.updatedAt)}</span>}
                </div>
                <div className="ticket-stage-block">
                  <span>Current stage</span>
                  <strong>{card.stageLabel}</strong>
                </div>
                <div className="ticket-progress-track" aria-label={`${card.progressPercent}% complete`}>
                  <span style={{ width: `${card.progressPercent}%` }} />
                </div>
                {card.note && (
                  <div className="ticket-next-action">
                    <small>Hermes note</small>
                    <p>{card.note}</p>
                  </div>
                )}
              </article>
            ))}
          </section>
        ))}
      </div>

      <div className="kanban-operations-panel">
        <div>
          <span className="source-pill">Operating rule</span>
          <p>Every ticket moves only after the analyst review gate is confirmed. Pass, not-pass, and pending decisions can later sync back to Jira or Hermes.</p>
        </div>
        <div>
          <span className="source-pill">Sources</span>
          <p>mini-Project tickets follow the 6-phase Work phase monitor; Hermes incidents follow Hermes's own reported status.</p>
        </div>
      </div>
    </section>
  );
}

function TestingSyncPrototype({ workspace, onBack }) {
  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Enhancement prototype"
        title="Testing + progress sync loop"
        subtitle="Dummy view for pass, not-pass, and pending testing decisions that can later update Jira and Hermes without leaving the platform."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />
      <div className="prototype-hero">
        <div>
          <span className="source-pill">Analyst control layer</span>
          <h2>Track testing status before the handoff is closed</h2>
          <p>
            {workspace?.name || "Connected project"} testing decisions can become structured status updates instead of separate Jira comments,
            Discord messages, and manual reports.
          </p>
        </div>
        <div className="prototype-metrics">
          <Stat label="Pending" value="1" />
          <Stat label="Not pass" value="1" />
          <Stat label="Pass" value="1" />
        </div>
      </div>
      <div className="prototype-board three">
        {TESTING_SYNC_COLUMNS.map((column) => (
          <section key={column.id} className={`prototype-column ${column.id}`}>
            <div className="prototype-column-head">
              <strong>{column.title}</strong>
              <span>{column.items.length}</span>
            </div>
            {column.items.map((item) => (
              <article key={item.key} className="prototype-card">
                <span className="source-pill">{item.key}</span>
                <h3>{item.title}</h3>
                <p>{item.action}</p>
                <div className="prototype-card-footer">
                  <span>{item.owner}</span>
                  <strong>{item.status}</strong>
                </div>
              </article>
            ))}
          </section>
        ))}
      </div>
    </section>
  );
}

function DbDiagnosticsPrototype({ workspace, onBack }) {
  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Enhancement prototype"
        title="DB diagnostic request flow"
        subtitle="Dummy view for cases where log or code evidence is not enough and the analyst needs a safe read-only DB check from a technical owner."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />
      <div className="prototype-grid two">
        <section className="prototype-panel">
          <span className="source-pill">Evidence gap</span>
          <h2>Generate a check request, not a database connection</h2>
          <p>
            For restricted projects, the platform can ask for the exact DB evidence needed while the actual query is executed by an authorized
            developer or DBA.
          </p>
          <div className="prototype-flow">
            <span>AI detects DB-state claim</span>
            <span>Draft read-only check</span>
            <span>Owner uploads result</span>
            <span>Rerun analysis</span>
          </div>
        </section>
        <section className="prototype-panel dark">
          <span className="source-pill">Project</span>
          <h2>{workspace?.name || "No project selected"}</h2>
          <p>Future backend hook: store open DB requests against the artifact and include result state in evidence gate.</p>
        </section>
      </div>
      <div className="prototype-list">
        {DB_DIAGNOSTIC_REQUESTS.map((item) => (
          <article key={item.id} className="prototype-row-card">
            <div>
              <span className="source-pill">{item.id}</span>
              <h3>{item.claim}</h3>
              <p>{item.query}</p>
            </div>
            <div>
              <strong>{item.state}</strong>
              <span>{item.owner}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function EvidenceGatePrototype({ workspace, onBack }) {
  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Enhancement prototype"
        title="Evidence readiness gate"
        subtitle="Dummy checklist for deciding whether an RCA, impact analysis, or handoff is grounded enough to move forward."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />
      <div className="prototype-hero evidence">
        <div>
          <span className="source-pill">Review gate</span>
          <h2>Stop weak findings before they reach Jira, Hermes, or stakeholders</h2>
          <p>
            This page turns Hermes evidence discipline into an analyst-readable checklist for {workspace?.name || "the connected project"}.
          </p>
        </div>
        <div className="prototype-score-ring">
          <strong>3/5</strong>
          <span>ready</span>
        </div>
      </div>
      <div className="prototype-gate-list">
        {EVIDENCE_GATE_ITEMS.map(([label, status, note]) => (
          <article key={label} className={`prototype-gate-card ${status.toLowerCase().replace(/\s+/g, "-")}`}>
            <div>
              <span>{label}</span>
              <strong>{status}</strong>
            </div>
            <p>{note}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function hermesStepState(stepLabel, currentStatus) {
  if (!currentStatus) {
    return "pending";
  }
  const stepIndex = HERMES_STATUS_ORDER.indexOf(stepLabel);
  const currentIndex = HERMES_STATUS_ORDER.indexOf(currentStatus);
  if (stepIndex < 0 || currentIndex < 0) {
    return "pending";
  }
  if (stepIndex < currentIndex) return "done";
  if (stepIndex === currentIndex) return "waiting";
  return "pending";
}

function HermesTrackerPrototype({ workspace, onBack }) {
  const [tracked, setTracked] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  useEffect(() => {
    loadCurrentStatuses();
    const interval = setInterval(loadCurrentStatuses, 8000);
    return () => clearInterval(interval);
  }, [workspace?.local_path]);

  function loadCurrentStatuses() {
    const query = workspace?.local_path ? `?project=${encodeURIComponent(workspace.local_path)}` : "";
    api(`/api/hermes/status/current${query}`)
      .then((rows) => {
        setTracked(rows);
        setError("");
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  // create_date is when the CURRENT status was last recorded, not when the
  // task was first sent to Hermes — filtering by it answers "what changed in
  // this window", which is the more useful question for a tracker page.
  const filteredTracked = tracked.filter((task) => {
    if (!task.create_date) return true;
    const updatedAt = new Date(task.create_date);
    if (dateFrom && updatedAt < new Date(`${dateFrom}T00:00:00`)) return false;
    if (dateTo && updatedAt > new Date(`${dateTo}T23:59:59`)) return false;
    return true;
  });
  const dateFilterActive = Boolean(dateFrom || dateTo);

  const activeCount = filteredTracked.filter((task) => task.status && task.status !== "Close summary").length;
  const completedCount = filteredTracked.filter((task) => task.status === "Close summary").length;
  const waitingCount = filteredTracked.filter(
    (task) => task.status === "Hermes accepted" || task.status === "Developer update",
  ).length;

  return (
    <section className="screen hermes-dashboard-page">
      <HeaderBlock
        eyebrow="Hermes bridge"
        title="Hermes handoff tracker"
        subtitle="Monitor reviewed analyst packages after they are handed off to Hermes, from intake acceptance to developer update, testing decision, and final summary."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />

      <div className="hermes-command-center">
        <section className="hermes-hero-panel">
          <div>
            <span className="source-pill">External execution bridge</span>
            <h2>Reviewed handoffs, tracked after send</h2>
            <p>
              mini-Project keeps the analyst-owned evidence and review gate. Hermes receives the approved package and reports
              progress back through <code>POST /api/hermes/status</code>.
            </p>
          </div>
          <div className="hermes-project-card">
            <span>Current project</span>
            <strong>{workspace?.name || "No project connected"}</strong>
            <small>{workspace?.local_path || "Connect a project to ground handoffs in repo context."}</small>
          </div>
        </section>

        <div className="hermes-metric-grid">
          <div className="hermes-metric-card">
            <span className="hermes-metric-icon" aria-hidden="true">&#9635;</span>
            <div>
              <strong>{filteredTracked.length}</strong>
              <span>Tracked handoffs</span>
            </div>
          </div>
          <div className="hermes-metric-card accent-amber">
            <span className="hermes-metric-icon" aria-hidden="true">&#9679;</span>
            <div>
              <strong>{activeCount}</strong>
              <span>Active</span>
            </div>
          </div>
          <div className="hermes-metric-card accent-blue">
            <span className="hermes-metric-icon" aria-hidden="true">&#8635;</span>
            <div>
              <strong>{waitingCount}</strong>
              <span>Waiting update</span>
            </div>
          </div>
          <div className="hermes-metric-card accent-teal">
            <span className="hermes-metric-icon" aria-hidden="true">&#10003;</span>
            <div>
              <strong>{completedCount}</strong>
              <span>Closed</span>
            </div>
          </div>
        </div>

        <section className="hermes-flow-strip" aria-label="Hermes handoff flow">
          {HERMES_STATUS_ORDER.map((label, index) => (
            <span key={label}>
              <strong>{index + 1}</strong>
              {label}
            </span>
          ))}
        </section>
      </div>

      {error && <ErrorBox message={error} />}

      {loading && tracked.length === 0 && !error && (
        <div className="hermes-task-stack">
          {[0, 1].map((i) => (
            <section className="hermes-task-card hermes-skeleton" key={i} aria-hidden="true">
              <div className="hermes-skeleton-line wide" />
              <div className="hermes-skeleton-line" />
              <div className="hermes-skeleton-rail" />
            </section>
          ))}
        </div>
      )}

      {!loading && tracked.length > 0 && (
        <div className="hermes-date-filter">
          <span className="hermes-date-filter-label">Filter by last-updated date</span>
          <div className="hermes-date-filter-inputs">
            <input
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              aria-label="From date"
            />
            <span>to</span>
            <input
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              aria-label="To date"
            />
          </div>
          {dateFilterActive && (
            <button
              className="btn ghost compact"
              type="button"
              onClick={() => {
                setDateFrom("");
                setDateTo("");
              }}
            >
              Clear
            </button>
          )}
        </div>
      )}

      {!loading && tracked.length === 0 && !error && (
        <section className="hermes-empty-state">
          <span className="hermes-empty-icon" aria-hidden="true">&#8987;</span>
          <span className="source-pill">No active handoff</span>
          <h2>No Hermes task is currently tracked</h2>
          <p>Send a reviewed handoff summary to Hermes, then this page will show its intake, developer update, testing decision, and close status.</p>
        </section>
      )}

      {!loading && tracked.length > 0 && filteredTracked.length === 0 && (
        <section className="hermes-empty-state">
          <span className="hermes-empty-icon" aria-hidden="true">&#128197;</span>
          <span className="source-pill">No match in this range</span>
          <h2>No Hermes task updated in the selected date range</h2>
          <p>Widen or clear the date filter above to see the {tracked.length} tracked task{tracked.length === 1 ? "" : "s"}.</p>
        </section>
      )}

      <div className="hermes-task-stack">
        {filteredTracked.map((task) => {
          const currentIndex = HERMES_STATUS_ORDER.indexOf(task.status);
          return (
            <section className={`hermes-task-card accent-${hermesStatusTone(task.status)}`} key={task.source_task_id}>
              <div className="hermes-task-head">
                <div>
                  <div className="hermes-task-eyebrow">
                    <span className="hermes-task-id">{task.source_task_id.slice(0, 8)}</span>
                    <span className={`hermes-status-badge tone-${hermesStatusTone(task.status)}`}>
                      {task.status || "Waiting for Hermes status"}
                    </span>
                    {task.create_date && (
                      <span className="hermes-task-date" title={formatDate(task.create_date)}>
                        Updated {formatRelativeTime(task.create_date)}
                      </span>
                    )}
                  </div>
                  {task.note && <p>{task.note}</p>}
                </div>
                <button className="btn ghost compact" type="button" onClick={loadCurrentStatuses}>
                  Refresh
                </button>
              </div>

              <div className="hermes-stage-rail" role="list">
                <div className="hermes-stage-rail-track">
                  <div
                    className="hermes-stage-rail-fill"
                    style={{ width: `${currentIndex <= 0 ? 0 : (currentIndex / (HERMES_STATUS_ORDER.length - 1)) * 100}%` }}
                  />
                </div>
                {HERMES_STATUS_ORDER.map((label, index) => {
                  const state = hermesStepState(label, task.status);
                  return (
                    <div
                      key={label}
                      className={`hermes-stage-step ${state}`}
                      role="listitem"
                      title={hermesStepDescription(label)}
                    >
                      <span className="hermes-stage-marker">{state === "done" ? "✓" : index + 1}</span>
                      <div className="hermes-stage-copy">
                        <strong>{label}</strong>
                      </div>
                    </div>
                  );
                })}
              </div>

              {task.similar_issues && (
                <details className="hermes-similar-issues">
                  <summary>
                    <span className="hermes-similar-issues-icon" aria-hidden="true">&#128269;</span>
                    Similar past incidents found (Hermes RAG check)
                  </summary>
                  <div className="hermes-similar-issues-body">{renderSimilarIssuesLite(task.similar_issues)}</div>
                </details>
              )}
            </section>
          );
        })}
      </div>

      <div className="tracker-panel" style={{ marginTop: "1.5rem" }}>
        <ProductionIncidentsPanel />
      </div>
    </section>
  );
}

function hermesStatusTone(status) {
  if (status === "Close summary") return "teal";
  if (status === "Testing decision") return "blue";
  if (status === "Developer update" || status === "Hermes accepted") return "amber";
  return "muted";
}

// Production Incidents: a Java+React port of Hermes's own
// plugins/incident-dashboard dashboard (see HermesIncidentReader.java on the
// backend) — Hermes's real email-triggered incident pipeline, read straight
// from its incident JSON files. This is a separate thing from the handoff
// tracker above it on this page: that one tracks packages *mini-Project*
// sent to Hermes; this one shows what Hermes is doing entirely on its own.

const HERMES_HOME_STORAGE_KEY = "mini-project.hermes-home";

const PRODUCTION_INCIDENT_PIPELINE = [
  { key: "email", label: "Email", stages: ["email-intake"] },
  { key: "log", label: "Log", stages: ["log-lookup"] },
  { key: "unzip", label: "Unzip", stages: ["log-extraction"] },
  { key: "analysis", label: "Analysis", stages: ["log-analysis", "debug-pipeline"] },
  { key: "rca", label: "RCA", stages: ["rca-review", "claude-rca", "root-cause-analysis", "rca-draft"] },
  { key: "code", label: "Code", stages: ["agent-code", "code-rca", "frontend-analysis", "backend-analysis"] },
];

const PRODUCTION_INCIDENT_STAGE_LABELS = {
  "email-intake": "Email intake",
  "log-date-detection": "Date detection",
  "log-lookup": "Log lookup",
  "debug-pipeline": "Debug pipeline",
  "log-extraction": "Safe unzip",
  "log-analysis": "Python log analysis",
  "similar-issue-check": "RAG similar issue",
  "agent-dispatch": "Agent dispatch",
  "rca-review": "RCA review",
  "rca-draft": "RCA draft",
  "root-cause-analysis": "Root cause analysis",
  "code-rca": "Code / Claude RCA",
  "agent-code": "Agent Code",
  "frontend-analysis": "Frontend analysis",
  "backend-analysis": "Backend analysis",
  "vpms-analysis": "VPMS evidence",
  "incident-control": "Incident control",
  resume: "Resume",
};

function productionIncidentStageLabel(stage) {
  return PRODUCTION_INCIDENT_STAGE_LABELS[stage] || stage || "Unknown";
}

function normalizeIncidentStatus(status) {
  return String(status || "").toLowerCase();
}

function productionIncidentStatusTone(status) {
  const normalized = normalizeIncidentStatus(status);
  if (normalized === "completed") return "teal";
  if (normalized === "running" || normalized === "stale" || normalized === "paused" || normalized === "blocked") return "amber";
  if (normalized === "failed") return "rust";
  return "muted";
}

function latestPipelineStageStatus(incident, step) {
  const history = Array.isArray(incident.history) ? incident.history : [];
  let found = null;
  history.forEach((item) => {
    if (step.stages.includes(item.stage)) found = item;
  });
  const status = found ? normalizeIncidentStatus(found.status) : "";
  const incidentStopped = normalizeIncidentStatus(incident.status) === "stopped" || incident.stop_requested;
  if (incidentStopped && status !== "completed") return "stopped";
  if (incident.is_stale && status === "running") return "stale";
  const incidentSkipped = ["skipped", "duplicate"].includes(normalizeIncidentStatus(incident.status));
  if (incidentSkipped && status !== "completed") return "skipped";
  const hasFinalRca = history.some(
    (item) => ["rca-review", "claude-rca", "code-rca", "root-cause-analysis"].includes(item.stage) && normalizeIncidentStatus(item.status) === "completed",
  );
  if (hasFinalRca && status === "running") return "completed";
  return status;
}

function productionIncidentOverallStatus(incident) {
  const statuses = PRODUCTION_INCIDENT_PIPELINE.map((step) => latestPipelineStageStatus(incident, step)).filter(Boolean);
  if (normalizeIncidentStatus(incident.status) === "stopped" || incident.stop_requested) return "stopped";
  if (incident.is_stale) return "stale";
  if (["skipped", "duplicate"].includes(normalizeIncidentStatus(incident.status))) return "skipped";
  if (statuses.includes("failed")) return "failed";
  if (statuses.includes("blocked") || statuses.includes("paused")) return "blocked";
  const history = Array.isArray(incident.history) ? incident.history : [];
  const hasFinalRca = history.some(
    (item) => ["rca-review", "claude-rca", "code-rca", "root-cause-analysis"].includes(item.stage) && normalizeIncidentStatus(item.status) === "completed",
  );
  if (hasFinalRca) return "completed";
  if (statuses.includes("running")) return "running";
  if (statuses.some((s) => s === "completed")) return "running";
  return normalizeIncidentStatus(incident.status);
}

function productionIncidentHasAttention(incident) {
  const status = normalizeIncidentStatus(incident.status);
  const text = [incident.message, incident.error, incident.stage, incident.status].join(" ").toLowerCase();
  if (incident.is_stale) return true;
  if (["failed", "blocked", "paused", "stale"].includes(status)) return true;
  if (text.includes("failed") || text.includes("not found")) return true;
  if (text.includes("evidence not enough") || text.includes("returned no usable output")) return true;
  const history = Array.isArray(incident.history) ? incident.history : [];
  return history.some((h) => {
    const s = normalizeIncidentStatus(h.status);
    const m = String(h.message || "").toLowerCase();
    return ["failed", "blocked", "paused"].includes(s) || m.includes("failed") || m.includes("not found") || m.includes("evidence not enough");
  });
}

function incidentDateValue(value) {
  if (!value) return "";
  try {
    const d = new Date(value);
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${d.getFullYear()}-${mm}-${dd}`;
  } catch {
    return "";
  }
}

function incidentFilename(path) {
  return path ? String(path).split(/[\\/]/).pop() : "-";
}

function uniqueSorted(items, getter) {
  const seen = new Set();
  const out = [];
  items.forEach((item) => {
    const value = getter(item);
    if (!value || seen.has(value)) return;
    seen.add(value);
    out.push(value);
  });
  return out.sort();
}

function incidentMatchesFilters(incident, filters) {
  if (filters.date && incidentDateValue(incident.updated_at) !== filters.date) return false;
  if (filters.status && productionIncidentOverallStatus(incident) !== filters.status) return false;
  if (filters.stage && String(incident.stage || "") !== filters.stage) return false;
  if (filters.attention && !productionIncidentHasAttention(incident)) return false;
  if (filters.query) {
    const q = filters.query.toLowerCase();
    const haystack = [incident.incident_key, incident.message, incident.current_agent, incident.current_log, incident.stage, incident.status]
      .join(" ")
      .toLowerCase();
    if (!haystack.includes(q)) return false;
  }
  return true;
}

function IncidentStatusIcon({ status }) {
  const normalized = normalizeIncidentStatus(status);
  if (normalized === "running") return <span className="prod-incident-spinner" title="Running" />;
  if (normalized === "stale") return <span className="prod-incident-icon tone-amber" title="Stale running task">!</span>;
  if (normalized === "completed") return <span className="prod-incident-icon tone-teal" title="Completed">✓</span>;
  if (normalized === "failed") return <span className="prod-incident-icon tone-rust" title="Failed">!</span>;
  if (normalized === "paused" || normalized === "blocked") return <span className="prod-incident-icon tone-amber" title="Needs attention">!</span>;
  if (normalized === "stopped" || normalized === "cancelled" || normalized === "canceled") return <span className="prod-incident-icon tone-muted" title="Stopped">&#9632;</span>;
  if (normalized === "skipped" || normalized === "duplicate") return <span className="prod-incident-icon tone-muted" title="Skipped">&#187;</span>;
  return <span className="prod-incident-icon tone-muted" title="Unknown">&#8226;</span>;
}

function IncidentPipelineBar({ incident }) {
  return (
    <div className="prod-incident-pipeline">
      {PRODUCTION_INCIDENT_PIPELINE.map((step) => {
        const status = latestPipelineStageStatus(incident, step);
        const tone = productionIncidentStatusTone(status);
        return (
          <span key={step.key} className={`prod-incident-pipeline-step tone-${tone}`}>
            <IncidentStatusIcon status={status} />
            {step.label}
          </span>
        );
      })}
    </div>
  );
}

function ProductionIncidentRow({ item, active, onSelect }) {
  const overallStatus = productionIncidentOverallStatus(item);
  const tone = productionIncidentStatusTone(overallStatus);
  return (
    <button className={`prod-incident-row${active ? " active" : ""}`} type="button" onClick={() => onSelect(item.incident_key)}>
      <div className="prod-incident-row-top">
        <IncidentStatusIcon status={overallStatus} />
        <div className="prod-incident-row-main">
          <div className="prod-incident-row-title">{item.incident_key || "Incident"}</div>
          <div className="prod-incident-row-message">{item.message || "-"}</div>
        </div>
      </div>
      <div className="prod-incident-row-meta">
        <span>{productionIncidentStageLabel(item.stage)}</span>
        <span className={`hermes-status-badge tone-${tone}`}>{overallStatus}</span>
        {item.updated_at && <span>{formatRelativeTime(item.updated_at)}</span>}
      </div>
      <IncidentPipelineBar incident={item} />
    </button>
  );
}

function ProductionIncidentDetail({ incident, onStop, onContinue, onRetry, stoppingKey, continuingKey, retryingKey }) {
  if (!incident) {
    return (
      <section className="prod-incident-detail prod-incident-empty-detail">
        <span className="source-pill">Select an incident</span>
        <p>Click an incident from the list to view its live progress, current log, worker, and history.</p>
      </section>
    );
  }
  const history = Array.isArray(incident.history) ? incident.history : [];
  const overallStatus = productionIncidentOverallStatus(incident);
  const tone = productionIncidentStatusTone(overallStatus);
  const canStop = !["completed", "failed", "stopped", "cancelled", "canceled"].includes(overallStatus);
  const canContinue = overallStatus === "stopped" || incident.stop_requested;
  const canRetry = overallStatus === "failed" || overallStatus === "stale" || incident.is_stale;
  const isStopping = stoppingKey === incident.incident_key;
  const isContinuing = continuingKey === incident.incident_key;
  const isRetrying = retryingKey === incident.incident_key;

  return (
    <section className="prod-incident-detail">
      <div className="prod-incident-detail-header">
        <div>
          <div className="prod-incident-detail-titleline">
            <IncidentStatusIcon status={overallStatus} />
            <h2>{incident.incident_key || "Incident"}</h2>
          </div>
          <p>{incident.message || "-"}</p>
        </div>
        <div className="prod-incident-detail-actions">
          <span className={`hermes-status-badge tone-${tone}`}>{overallStatus}</span>
          {canStop && (
            <button className="btn ghost compact danger" type="button" disabled={isStopping} onClick={() => onStop(incident.incident_key)}>
              {isStopping ? "Stopping..." : "Stop"}
            </button>
          )}
          {canContinue && (
            <button className="btn ghost compact" type="button" disabled={isContinuing} onClick={() => onContinue(incident.incident_key)}>
              {isContinuing ? "Continuing..." : "Continue"}
            </button>
          )}
          {canRetry && (
            <button className="btn ghost compact" type="button" disabled={isRetrying} onClick={() => onRetry(incident.incident_key)}>
              {isRetrying ? "Retrying..." : "Retry"}
            </button>
          )}
        </div>
      </div>

      <IncidentPipelineBar incident={incident} />

      <div className="prod-incident-detail-grid">
        <div><span>Stage</span><strong>{productionIncidentStageLabel(incident.stage)}</strong></div>
        <div><span>Worker</span><strong>{incident.current_agent || "-"}</strong></div>
        <div><span>Log</span><strong>{incidentFilename(incident.current_log)}</strong></div>
        <div><span>Updated</span><strong>{formatDate(incident.updated_at)}</strong></div>
        <div><span>Thread ID</span><strong>{incident.thread_id || "-"}</strong></div>
        <div><span>Target agent</span><strong>{incident.target_agent || "-"}</strong></div>
        <div><span>Task ID</span><strong>{incident.task_id || "-"}</strong></div>
        <div><span>RCA report</span><strong>{incidentFilename(incident.rca_code_report || incident.report_path)}</strong></div>
      </div>

      {incident.error && (
        <div className="prod-incident-warning">
          <strong>Note: </strong>
          {String(incident.error).slice(0, 350)}
        </div>
      )}

      <h3>Progress history</h3>
      <div className="prod-incident-timeline">
        {history
          .slice()
          .reverse()
          .map((item, index) => (
            <div className="prod-incident-timeline-row" key={index}>
              <IncidentStatusIcon status={item.status} />
              <div>
                <div className="prod-incident-timeline-title">
                  {productionIncidentStageLabel(item.stage)} · {item.status || "unknown"}
                </div>
                {item.message && <div className="prod-incident-timeline-msg">{item.message}</div>}
                {item.target_agent && (
                  <div className="prod-incident-timeline-meta">
                    Target: <code>{item.target_agent}</code>
                    {item.task_id && (
                      <>
                        {" "}
                        · Task: <code>{item.task_id}</code>
                      </>
                    )}
                  </div>
                )}
                <div className="prod-incident-timeline-time">{formatDate(item.at)}</div>
              </div>
            </div>
          ))}
      </div>
    </section>
  );
}

function ProductionIncidentsPanel() {
  const [hermesHome, setHermesHome] = useState(() => {
    try {
      return window.localStorage.getItem(HERMES_HOME_STORAGE_KEY) || "";
    } catch {
      return "";
    }
  });
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedKey, setSelectedKey] = useState("");
  const [filters, setFilters] = useState({ query: "", date: "", status: "", stage: "", attention: false });
  const [stoppingKey, setStoppingKey] = useState("");
  const [continuingKey, setContinuingKey] = useState("");
  const [retryingKey, setRetryingKey] = useState("");

  function updateFilter(key, value) {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }

  function load() {
    const home = hermesHome.trim();
    if (!home) return;
    setLoading(true);
    setError("");
    api(`/api/hermes/incidents?hermes_home=${encodeURIComponent(home)}&limit=100`)
      .then((res) => {
        const rows = res.incidents || [];
        setIncidents(rows);
        setSelectedKey((current) => {
          if (current && rows.some((x) => x.incident_key === current)) return current;
          return rows.length ? rows[0].incident_key : "";
        });
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    try {
      window.localStorage.setItem(HERMES_HOME_STORAGE_KEY, hermesHome);
    } catch {
      // ignore -- private browsing / storage disabled is fine, just no persistence
    }
    if (!hermesHome.trim()) {
      return;
    }
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hermesHome]);

  function runAction(action, incidentKey, setKey, confirmMessage) {
    if (!incidentKey) return;
    if (confirmMessage && !window.confirm(confirmMessage)) return;
    setKey(incidentKey);
    api(`/api/hermes/incidents/${encodeURIComponent(incidentKey)}/${action}?hermes_home=${encodeURIComponent(hermesHome.trim())}`, {
      method: "POST",
    })
      .then(() => load())
      .catch((err) => setError(err.message))
      .finally(() => setKey(""));
  }

  const stopIncident = (key) =>
    runAction("stop", key, setStoppingKey, "Stop this incident pipeline? Current command may finish, but RCA will not continue.");
  const continueIncidentAction = (key) => runAction("continue", key, setContinuingKey, null);
  const retryIncident = (key) => runAction("retry", key, setRetryingKey, "Retry this failed incident from log analysis?");

  const statuses = useMemo(() => uniqueSorted(incidents, productionIncidentOverallStatus), [incidents]);
  const stages = useMemo(() => uniqueSorted(incidents, (x) => x.stage), [incidents]);
  const dates = useMemo(() => uniqueSorted(incidents, (x) => incidentDateValue(x.updated_at)).reverse(), [incidents]);
  const filtered = useMemo(() => incidents.filter((x) => incidentMatchesFilters(x, filters)), [incidents, filters]);
  const selectedIncident = useMemo(
    () => incidents.find((x) => x.incident_key === selectedKey) || filtered[0] || null,
    [incidents, filtered, selectedKey],
  );

  const runningCount = incidents.filter((x) => productionIncidentOverallStatus(x) === "running").length;
  const attentionCount = incidents.filter(productionIncidentHasAttention).length;
  const todayCount = incidents.filter((x) => incidentDateValue(x.updated_at) === incidentDateValue(new Date())).length;
  const completedCount = incidents.filter((x) => productionIncidentOverallStatus(x) === "completed").length;

  return (
    <div className="prod-incident-panel">
      <div className="tracker-panel-head">
        <div>
          <span className="source-pill">Hermes's own pipeline</span>
          <h2>Production incidents</h2>
          <p>
            Live from Hermes's own email-triggered incident pipeline — read directly from its incident JSON files,
            independent of anything mini-Project itself handed off.
          </p>
        </div>
        <button className="btn ghost compact" type="button" disabled={loading || !hermesHome.trim()} onClick={load}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      <label className="field-label">Hermes home directory</label>
      <input
        type="text"
        value={hermesHome}
        onChange={(event) => setHermesHome(event.target.value)}
        placeholder="C:/Users/you/AppData/Local/hermes"
      />
      <p className="field-hint">
        The folder that directly contains "incidents" and "agent-tasks" — not the hermes-agent repo checkout, its
        sibling data folder. Remembered on this device.
      </p>

      {error && <ErrorBox message={error} />}

      {!hermesHome.trim() && <p className="muted-note">Enter Hermes's home directory above to load its live incident pipeline.</p>}

      {hermesHome.trim() && (
        <>
          <div className="prod-incident-summary-grid">
            <button
              className="prod-incident-summary-card accent-amber"
              type="button"
              onClick={() => setFilters({ query: "", date: "", status: "running", stage: "", attention: false })}
            >
              <span className="prod-incident-summary-icon" aria-hidden="true">&#9679;</span>
              <div>
                <strong>{runningCount}</strong>
                <span>Running</span>
              </div>
            </button>
            <button
              className="prod-incident-summary-card accent-rust"
              type="button"
              onClick={() => setFilters({ query: "", date: "", status: "", stage: "", attention: true })}
            >
              <span className="prod-incident-summary-icon" aria-hidden="true">&#9888;</span>
              <div>
                <strong>{attentionCount}</strong>
                <span>Needs attention</span>
              </div>
            </button>
            <button
              className="prod-incident-summary-card"
              type="button"
              onClick={() => setFilters({ query: "", date: incidentDateValue(new Date()), status: "", stage: "", attention: false })}
            >
              <span className="prod-incident-summary-icon" aria-hidden="true">&#128197;</span>
              <div>
                <strong>{todayCount}</strong>
                <span>Today</span>
              </div>
            </button>
            <button
              className="prod-incident-summary-card accent-teal"
              type="button"
              onClick={() => setFilters({ query: "", date: "", status: "completed", stage: "", attention: false })}
            >
              <span className="prod-incident-summary-icon" aria-hidden="true">&#10003;</span>
              <div>
                <strong>{completedCount}</strong>
                <span>Completed</span>
              </div>
            </button>
          </div>

          <div className="prod-incident-filters">
            <label className="prod-incident-filter-field prod-incident-filter-search">
              <span>Search</span>
              <input type="text" value={filters.query} onChange={(event) => updateFilter("query", event.target.value)} placeholder="Ticket, log, worker..." />
            </label>
            <label className="prod-incident-filter-field">
              <span>Date</span>
              <select value={filters.date} onChange={(event) => updateFilter("date", event.target.value)}>
                <option value="">All dates</option>
                {dates.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </label>
            <label className="prod-incident-filter-field">
              <span>Status</span>
              <select value={filters.status} onChange={(event) => updateFilter("status", event.target.value)}>
                <option value="">All status</option>
                {statuses.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
            <label className="prod-incident-filter-field">
              <span>Stage</span>
              <select value={filters.stage} onChange={(event) => updateFilter("stage", event.target.value)}>
                <option value="">All stages</option>
                {stages.map((s) => (
                  <option key={s} value={s}>
                    {productionIncidentStageLabel(s)}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="btn ghost compact"
              type="button"
              onClick={() => setFilters({ query: "", date: "", status: "", stage: "", attention: false })}
            >
              Clear
            </button>
          </div>
          {filters.attention && <p className="prod-incident-active-filter">Showing: Needs attention</p>}

          <p className="prod-incident-list-summary">
            {filtered.length} of {incidents.length} incidents
          </p>

          <div className="prod-incident-layout">
            <div className="prod-incident-list">
              {!loading && filtered.length === 0 && <p className="muted-note">No incidents match the current filters.</p>}
              {filtered.map((item) => (
                <ProductionIncidentRow
                  key={item.incident_key}
                  item={item}
                  active={Boolean(selectedIncident && selectedIncident.incident_key === item.incident_key)}
                  onSelect={setSelectedKey}
                />
              ))}
            </div>
            <ProductionIncidentDetail
              incident={selectedIncident}
              onStop={stopIncident}
              onContinue={continueIncidentAction}
              onRetry={retryIncident}
              stoppingKey={stoppingKey}
              continuingKey={continuingKey}
              retryingKey={retryingKey}
            />
          </div>
        </>
      )}
    </div>
  );
}

function renderSimilarIssuesInline(text) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? <strong key={i}>{part.slice(2, -2)}</strong> : part,
  );
}

/** Lightweight ##/###/-/** formatter for Hermes's RAG markdown output — not a full markdown parser, just enough to turn a wall of text into readable headings and lists. */
function renderSimilarIssuesLite(text) {
  const blocks = [];
  let currentList = null;
  text.split("\n").forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (line.startsWith("### ")) {
      currentList = null;
      blocks.push({ key: `h4-${index}`, node: <h4>{line.slice(4)}</h4> });
    } else if (line.startsWith("## ")) {
      currentList = null;
      blocks.push({ key: `h3-${index}`, node: <h3>{line.slice(3)}</h3> });
    } else if (line.startsWith("- ") || line.startsWith("* ")) {
      if (!currentList) {
        currentList = { key: `ul-${index}`, items: [] };
        blocks.push(currentList);
      }
      currentList.items.push(<li key={index}>{renderSimilarIssuesInline(line.slice(2))}</li>);
    } else if (line) {
      currentList = null;
      blocks.push({ key: `p-${index}`, node: <p>{renderSimilarIssuesInline(line)}</p> });
    } else {
      currentList = null;
    }
  });
  return blocks.map((block) =>
    block.items ? (
      <ul key={block.key}>{block.items}</ul>
    ) : (
      <React.Fragment key={block.key}>{block.node}</React.Fragment>
    ),
  );
}

function hermesStepDescription(label) {
  if (label === "Sent to Hermes") return "Reviewed analyst package is delivered to Hermes intake.";
  if (label === "Hermes accepted") return "Hermes has accepted the task or thread for follow-up.";
  if (label === "Developer update") return "Developer or technical agent reports fix progress.";
  if (label === "Testing decision") return "Pass, not-pass, or pending result is confirmed.";
  return "Final analyst close summary is ready for reporting.";
}

const HERMES_WIZARD_STEPS = ["Project & platforms", "Platform details", "Storage paths", "PR package"];

function linesToList(text) {
  return (text || "")
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalizeRepoPath(path) {
  return (path || "").trim().replace(/\\/g, "/").replace(/\/+$/, "").toLowerCase();
}

function findProfileForRepoPath(profiles, path) {
  const target = normalizeRepoPath(path);
  if (!target) return null;
  const matches = profiles.filter((p) => normalizeRepoPath(p.repo_path) === target);
  if (matches.length === 0) return null;
  return matches.slice().sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at))[0];
}

function HermesSetupWizardPage({ workspace, onBack, embedded = false }) {
  const [step, setStep] = useState(0);
  const [profileId, setProfileId] = useState(null);
  const [profileName, setProfileName] = useState("");
  const [savedProfiles, setSavedProfiles] = useState([]);
  const [autoMatched, setAutoMatched] = useState(false);

  const [repoPath, setRepoPath] = useState(workspace?.local_path || "");
  const [platforms, setPlatforms] = useState(["discord"]);
  const [discordChannelId, setDiscordChannelId] = useState("");
  const [emailImapHost, setEmailImapHost] = useState("");
  const [emailAccount, setEmailAccount] = useState("");
  const [emailAllowedSendersText, setEmailAllowedSendersText] = useState("");
  const [incidentReportsDir, setIncidentReportsDir] = useState("");
  const [incidentExtractsDir, setIncidentExtractsDir] = useState("");
  const [incidentDownloadsDir, setIncidentDownloadsDir] = useState("");
  const [serverLogPath, setServerLogPath] = useState("");
  const [prPackageEnabled, setPrPackageEnabled] = useState(false);
  const [gitHost, setGitHost] = useState("");

  const [artifact, setArtifact] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    loadProfiles();
  }, []);

  // Follow the connected project automatically: once both the workspace's
  // local path and the saved-profiles list are known, load whichever saved
  // profile already points at this repo -- no need to re-pick it from the
  // dropdown every time the same project is reconnected. If nothing matches,
  // just prefill the repo path for a fresh setup instead. workspace resolves
  // asynchronously (ConnectProjectScreen loads the active project after
  // mount), so this re-runs once it becomes available.
  useEffect(() => {
    if (!workspace?.local_path) {
      return;
    }
    const match = findProfileForRepoPath(savedProfiles, workspace.local_path);
    if (match) {
      if (match.id !== profileId) {
        setAutoMatched(true);
        loadProfile(match.id);
      }
    } else if (!profileId) {
      setRepoPath(workspace.local_path);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspace?.local_path, savedProfiles]);

  function loadProfiles() {
    api("/api/hermes/setup-profiles")
      .then(setSavedProfiles)
      .catch(() => setSavedProfiles([]));
  }

  async function loadProfile(id) {
    if (!id) {
      return;
    }
    setError("");
    try {
      const p = await api(`/api/hermes/setup-profiles/${id}`);
      setProfileId(p.id);
      setProfileName(p.name);
      setRepoPath(p.repo_path || "");
      setPlatforms(p.platforms && p.platforms.length ? p.platforms : ["discord"]);
      setDiscordChannelId(p.discord_channel_id || "");
      setEmailImapHost(p.email_imap_host || "");
      setEmailAccount(p.email_account || "");
      setEmailAllowedSendersText((p.email_allowed_senders || []).join("\n"));
      setIncidentReportsDir(p.incident_reports_dir || "");
      setIncidentExtractsDir(p.incident_extracts_dir || "");
      setIncidentDownloadsDir(p.incident_downloads_dir || "");
      setServerLogPath(p.server_log_path || "");
      setPrPackageEnabled(Boolean(p.pr_package_enabled));
      setGitHost(p.git_host || "");
      setArtifact(null);
      setStep(0);
    } catch (err) {
      setError(err.message);
    }
  }

  function startNew() {
    setAutoMatched(false);
    setProfileId(null);
    setProfileName("");
    setRepoPath(workspace?.local_path || "");
    setPlatforms(["discord"]);
    setDiscordChannelId("");
    setEmailImapHost("");
    setEmailAccount("");
    setEmailAllowedSendersText("");
    setIncidentReportsDir("");
    setIncidentExtractsDir("");
    setIncidentDownloadsDir("");
    setServerLogPath("");
    setPrPackageEnabled(false);
    setGitHost("");
    setArtifact(null);
    setError("");
    setStep(0);
  }

  function togglePlatform(name) {
    setPlatforms((current) =>
      current.includes(name) ? current.filter((p) => p !== name) : [...current, name],
    );
  }

  function currentAnswers() {
    return {
      repo_path: repoPath.trim(),
      platforms,
      discord_channel_id: discordChannelId.trim() || null,
      email_imap_host: emailImapHost.trim() || null,
      email_account: emailAccount.trim() || null,
      email_allowed_senders: linesToList(emailAllowedSendersText),
      incident_reports_dir: incidentReportsDir.trim() || null,
      incident_extracts_dir: incidentExtractsDir.trim() || null,
      incident_downloads_dir: incidentDownloadsDir.trim() || null,
      server_log_path: serverLogPath.trim() || null,
      pr_package_enabled: prPackageEnabled,
      git_host: gitHost.trim() || null,
    };
  }

  async function saveProfile() {
    if (!repoPath.trim()) {
      setError("Repo path is required before saving.");
      return;
    }
    const name = profileName.trim() || repoPath.trim();
    setError("");
    setSaving(true);
    try {
      const saved = await api("/api/hermes/setup-profiles", {
        method: "POST",
        body: { id: profileId, name, ...currentAnswers() },
      });
      setProfileId(saved.id);
      setProfileName(saved.name);
      loadProfiles();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function generate() {
    if (!repoPath.trim()) {
      setError("Repo path is required.");
      return;
    }
    setError("");
    setLoading(true);
    setCopied(false);
    try {
      const result = await api("/api/skills/hermes-setup-wizard", {
        method: "POST",
        body: { profile: ANALYST_PROFILE, ...currentAnswers() },
      });
      setArtifact(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  function copyYaml() {
    const yaml = artifact?.result?.generated_yaml;
    if (!yaml) return;
    navigator.clipboard?.writeText(yaml).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  const result = artifact?.result;

  return (
    <section className={embedded ? "hermes-setup-embedded" : "screen prototype-screen"}>
      {embedded ? (
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">Hermes bridge</span>
            <h2>Hermes setup wizard</h2>
            <p>Q&amp;A walkthrough of this project's Hermes setup — channel IDs, allowed senders, storage paths — filled directly into the generated YAML. Only genuine secrets stay as placeholders.</p>
          </div>
        </div>
      ) : (
        <HeaderBlock
          // eyebrow="Hermes bridge"
          // title="Hermes setup wizard"
          // subtitle="Q&A walkthrough of a Hermes deployment's real setup — channel IDs, allowed senders, storage paths — filled directly into the generated YAML. Only genuine secrets stay as placeholders."
        />
      )}
      {onBack && (
        <button className="btn ghost compact" type="button" onClick={onBack}>
          ← Back to work queue
        </button>
      )}

      <div className="tracker-panel" style={{ marginTop: "1rem" }}>
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">Saved profiles</span>
            <h2>Load a previous setup, or start a new one</h2>
            <p>Saving is separate from generating — you can save your answers without generating, or generate without saving.</p>
          </div>
        </div>
        <div className="action-row">
          <select
            value={profileId || ""}
            onChange={(event) => {
              setAutoMatched(false);
              if (event.target.value) loadProfile(event.target.value);
              else startNew();
            }}
          >
            <option value="">{profileId ? "Start new..." : "Load saved profile..."}</option>
            {savedProfiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <input
            type="text"
            value={profileName}
            onChange={(event) => setProfileName(event.target.value)}
            placeholder="Profile name (e.g. PSP Backend)"
          />
          <button className="btn ghost compact" type="button" disabled={saving} onClick={saveProfile}>
            {saving ? "Saving..." : profileId ? "Update profile" : "Save profile"}
          </button>
          {profileId && (
            <button className="btn ghost compact" type="button" onClick={startNew}>
              Start new
            </button>
          )}
        </div>
        {profileId && (
          <p className="field-hint">
            {autoMatched
              ? `Auto-loaded "${profileName}" — it's already saved for this repo path, so you don't need to pick it again. Click "Start new" to begin a fresh, unsaved setup instead.`
              : `Editing "${profileName}" — click "Start new" to begin a fresh, unsaved setup instead.`}
          </p>
        )}
      </div>

      <div className="tracker-panel" style={{ marginTop: "1rem" }}>
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">{`Step ${step + 1} of ${HERMES_WIZARD_STEPS.length}`}</span>
            <h2>{HERMES_WIZARD_STEPS[step]}</h2>
          </div>
        </div>

        {step === 0 && (
          <>
            <label className="field-label">Repo path</label>
            <input
              type="text"
              value={repoPath}
              onChange={(event) => setRepoPath(event.target.value)}
              placeholder="C:/path/to/target/repo"
            />
            <label className="field-label">Intake platforms</label>
            <div className="action-row">
              <label className="field-label" style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
                <input type="checkbox" checked={platforms.includes("discord")} onChange={() => togglePlatform("discord")} />
                Discord
              </label>
              <label className="field-label" style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
                <input type="checkbox" checked={platforms.includes("email")} onChange={() => togglePlatform("email")} />
                Email
              </label>
            </div>
          </>
        )}

        {step === 1 && (
          <>
            {platforms.includes("discord") && (
              <>
                <label className="field-label">Discord intake channel ID</label>
                <input
                  type="text"
                  value={discordChannelId}
                  onChange={(event) => setDiscordChannelId(event.target.value)}
                  placeholder="123456789012345678"
                />
                <p className="field-hint">Bot token isn't asked here — it's a secret, stays a placeholder in the generated YAML.</p>
              </>
            )}
            {platforms.includes("email") && (
              <>
                <label className="field-label">IMAP host</label>
                <input type="text" value={emailImapHost} onChange={(event) => setEmailImapHost(event.target.value)} placeholder="imap.example.com" />
                <label className="field-label">Intake email account</label>
                <input type="text" value={emailAccount} onChange={(event) => setEmailAccount(event.target.value)} placeholder="incidents@example.com" />
                <label className="field-label">Allowed senders (one per line — who is allowed to trigger Hermes by email)</label>
                <textarea
                  className="compact-textarea code-snippet-textarea"
                  value={emailAllowedSendersText}
                  onChange={(event) => setEmailAllowedSendersText(event.target.value)}
                  placeholder={"alice@example.com\nbob@example.com"}
                />
                <p className="field-hint">App password isn't asked here — it's a secret, stays a placeholder in the generated YAML.</p>
              </>
            )}
            {platforms.length === 0 && <p className="muted-note">Go back and pick at least one platform.</p>}
          </>
        )}

        {step === 2 && (
          <>
            <label className="field-label">Incident reports directory</label>
            <input type="text" value={incidentReportsDir} onChange={(event) => setIncidentReportsDir(event.target.value)} placeholder="D:/Hermes/incident-reports" />
            <label className="field-label">Incident extracts directory</label>
            <input type="text" value={incidentExtractsDir} onChange={(event) => setIncidentExtractsDir(event.target.value)} placeholder="D:/Hermes/incident-extracts" />
            <label className="field-label">Incident downloads directory (OneDrive / SharePoint path)</label>
            <input type="text" value={incidentDownloadsDir} onChange={(event) => setIncidentDownloadsDir(event.target.value)} placeholder="D:/OneDrive/Hermes/incident-downloads" />
            <label className="field-label">Server log source path</label>
            <input type="text" value={serverLogPath} onChange={(event) => setServerLogPath(event.target.value)} placeholder="\\\\fileserver\\logs or an SFTP/network path" />
            <p className="field-hint">Leave any of these blank to fall back to Hermes's own default location.</p>
          </>
        )}

        {step === 3 && (
          <>
            <label className="field-label" style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <input type="checkbox" checked={prPackageEnabled} onChange={(event) => setPrPackageEnabled(event.target.checked)} />
              Enable PR-package flow (fix plan → testing → push/PR)
            </label>
            {prPackageEnabled && (
              <>
                <label className="field-label">Git host</label>
                <input type="text" value={gitHost} onChange={(event) => setGitHost(event.target.value)} placeholder="bitbucket.org/org/repo" />
                <p className="field-hint">Credentials aren't asked here — they're a secret, stay a placeholder in the generated YAML.</p>
              </>
            )}
          </>
        )}

        {error && <ErrorBox message={error} />}
        <div className="action-row" style={{ marginTop: "0.75rem" }}>
          <button className="btn ghost compact" type="button" disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>
            ← Back
          </button>
          {step < HERMES_WIZARD_STEPS.length - 1 ? (
            <button className="btn primary compact" type="button" onClick={() => setStep((s) => Math.min(HERMES_WIZARD_STEPS.length - 1, s + 1))}>
              Next →
            </button>
          ) : (
            <button className="btn primary" type="button" disabled={loading} onClick={generate}>
              {loading ? "Generating..." : "Generate setup scaffolding"}
            </button>
          )}
        </div>
      </div>

      {result && (
        <div className="tracker-panel" style={{ marginTop: "1rem" }}>
          <div className="tracker-panel-head">
            <div>
              <span className="source-pill">Result</span>
              <h2>Review and apply by hand</h2>
              <p>This content is a draft artifact — nothing has been written to Hermes yet.</p>
            </div>
            <button className="btn ghost compact" type="button" onClick={copyYaml}>
              {copied ? "Copied" : "Copy YAML"}
            </button>
          </div>
          <pre className="code-snippet-textarea" style={{ whiteSpace: "pre-wrap" }}>
            {result.generated_yaml}
          </pre>
          <h3>Checklist before applying</h3>
          <ul>
            {(result.checklist || []).map((item, index) => (
              <li key={index}>{item}</li>
            ))}
          </ul>
          {result.notes && result.notes.length > 0 && (
            <>
              <h3>Notes</h3>
              <ul>
                {result.notes.map((note, index) => (
                  <li key={index} className="muted-note">
                    {note}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  );
}

function HermesVersionAdvisorPage({ workspace, onBack }) {
  const [repoPath, setRepoPath] = useState(workspace?.local_path || "");
  const [watchedPathsText, setWatchedPathsText] = useState("");
  const [artifact, setArtifact] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [pullStatus, setPullStatus] = useState(null);
  const [checkingStatus, setCheckingStatus] = useState(false);
  const [approving, setApproving] = useState(false);
  const [pullResult, setPullResult] = useState(null);

  async function generate() {
    if (!repoPath.trim()) {
      setError("Repo path is required.");
      return;
    }
    setError("");
    setLoading(true);
    setPullResult(null);
    try {
      const watchedPaths = watchedPathsText
        .split(/[\n,]/)
        .map((path) => path.trim())
        .filter(Boolean);
      const result = await api("/api/skills/hermes-version-advisor", {
        method: "POST",
        body: {
          profile: ANALYST_PROFILE,
          repo_path: repoPath.trim(),
          watched_paths: watchedPaths,
        },
      });
      setArtifact(result);
      checkPullStatus();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function checkPullStatus() {
    setCheckingStatus(true);
    try {
      const status = await api(`/api/hermes/version-control/status?repo_path=${encodeURIComponent(repoPath.trim())}`);
      setPullStatus(status);
    } catch (err) {
      setError(err.message);
    } finally {
      setCheckingStatus(false);
    }
  }

  async function approveAndPull() {
    if (!artifact?.task_id) return;
    setError("");
    setApproving(true);
    try {
      await api(`/api/artifacts/${artifact.task_id}/review`, { method: "PATCH" });
      const result = await api("/api/hermes/version-control/pull", {
        method: "POST",
        body: { source_task_id: artifact.task_id, repo_path: repoPath.trim() },
      });
      setPullResult(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setApproving(false);
    }
  }

  const result = artifact?.result;

  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Hermes bridge"
        title="Hermes version control"
        subtitle="Recommends which upstream commit/tag to adopt and which new feat: commits look like useful enhancements — then, only after you approve, actually runs the pull. Never pulls on its own."
      />
      {onBack && (
        <button className="btn ghost compact" type="button" onClick={onBack}>
          ← Back to work queue
        </button>
      )}

      <div className="tracker-panel" style={{ marginTop: "1rem" }}>
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">Step 1</span>
            <h2>Point at your Hermes repo</h2>
            <p>mini-Project checks upstream and figures out which files you've changed locally on its own — nothing else to configure.</p>
          </div>
        </div>

        <label className="field-label">Repo path</label>
        <input
          type="text"
          value={repoPath}
          onChange={(event) => setRepoPath(event.target.value)}
          placeholder="C:/path/to/target/repo"
        />

        <details className="collapsible-advanced">
          <summary>Advanced: manually pick which files to watch (optional)</summary>
          <p className="muted-note">
            Leave this blank — mini-Project automatically detects which files you've changed locally
            compared to upstream and treats those as "watched." Only fill this in if you want to watch
            specific files you haven't touched yet.
          </p>
          <textarea
            className="code-snippet-textarea"
            value={watchedPathsText}
            onChange={(event) => setWatchedPathsText(event.target.value)}
            placeholder={"plugins/platforms/email/adapter.py\nplugins/platforms/email/incident_status_store.py"}
          />
        </details>

        {error && <ErrorBox message={error} />}
        <div className="action-row">
          <button className="btn primary" type="button" disabled={loading} onClick={generate}>
            {loading ? "Checking upstream..." : "Check upstream"}
          </button>
        </div>
      </div>

      {result && (
        <div className="tracker-panel" style={{ marginTop: "1rem" }}>
          <div className="tracker-panel-head">
            <div>
              <span className="source-pill">Step 2</span>
              <h2>Recommendation</h2>
            </div>
          </div>
          <div className="tracker-kpi-grid">
            <Stat label="Commits behind" value={String(result.commits_behind)} />
            <Stat label="Local-only commits" value={String(result.commits_ahead)} />
            <Stat label="Latest tag" value={result.latest_tag || "—"} />
            <Stat label="Touched watched files" value={String((result.touched_watched_files || []).length)} />
          </div>

          <div className="tracker-next-action-panel">
            <div>
              <span className="source-pill">Recommended</span>
              <strong>{result.recommended_ref}</strong>
            </div>
            <p>{result.rationale}</p>
          </div>

          <h3>Files watched for this check</h3>
          {result.watched_paths && result.watched_paths.length > 0 ? (
            <>
              <p className="muted-note">
                Auto-detected from your local changes vs. upstream — commits touching these are flagged instead of auto-recommended.
              </p>
              <ul>
                {result.watched_paths.map((path, index) => (
                  <li key={index}><code>{path}</code></li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted-note">No local changes detected vs. upstream — nothing is being watched for this repo.</p>
          )}

          {result.risks_if_adopted && result.risks_if_adopted.length > 0 && (
            <>
              <h3>Risks / review before adopting</h3>
              <ul>
                {result.risks_if_adopted.map((risk, index) => (
                  <li key={index}>{risk}</li>
                ))}
              </ul>
            </>
          )}

          <h3>Suggested enhancements ({(result.feature_commits || []).length} feat: commits found upstream)</h3>
          {result.suggested_enhancements && result.suggested_enhancements.length > 0 ? (
            <ul>
              {result.suggested_enhancements.map((item, index) => (
                <li key={index}>{item}</li>
              ))}
            </ul>
          ) : (
            <p className="muted-note">No feat: commits found, or none judged relevant to this team's workflow.</p>
          )}
        </div>
      )}

      {artifact && (
        <div className="tracker-panel" style={{ marginTop: "1rem" }}>
          <div className="tracker-panel-head">
            <div>
              <span className="source-pill">Step 3</span>
              <h2>Approve & pull</h2>
              <p>Pulling is blocked until you approve this recommendation and the working tree is clean — commit or stash your own in-flight changes first.</p>
            </div>
            <button className="btn ghost compact" type="button" disabled={checkingStatus} onClick={checkPullStatus}>
              {checkingStatus ? "Checking..." : "Re-check status"}
            </button>
          </div>
          {pullStatus && (
            <p className={pullStatus.clean ? "muted-note" : "field-hint"}>
              {pullStatus.clean ? "✅ " : "⚠️ "}
              {pullStatus.message}
            </p>
          )}
          <div className="action-row">
            <button
              className="btn primary"
              type="button"
              disabled={approving || !pullStatus?.clean}
              onClick={approveAndPull}
            >
              {approving ? "Pulling..." : "Approve & Pull"}
            </button>
          </div>
          {pullResult && (
            <>
              <h3>{pullResult.success ? "Pull completed" : "Pull failed"}</h3>
              <pre className="code-snippet-textarea" style={{ whiteSpace: "pre-wrap" }}>
                {pullResult.output}
              </pre>
            </>
          )}
        </div>
      )}
    </section>
  );
}

function HermesTrendingDigestPage({ onBack }) {
  const [artifact, setArtifact] = useState(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    loadLatest();
  }, []);

  async function loadLatest() {
    setLoading(true);
    setError("");
    try {
      const summaries = await api("/api/artifacts");
      const latest = summaries
        .filter((item) => item.skill === "hermes-trending-digest")
        .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))[0];
      if (latest) {
        const full = await api(`/api/artifacts/${latest.task_id}`);
        setArtifact(full);
      } else {
        setArtifact(null);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function runNow() {
    setError("");
    setRunning(true);
    try {
      const result = await api("/api/skills/hermes-trending-digest", {
        method: "POST",
        body: { profile: ANALYST_PROFILE },
      });
      setArtifact(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  }

  const candidates = artifact?.result?.candidates || [];

  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Hermes bridge"
        title="GitHub Trending digest"
        subtitle="Weekly scan of github.com/trending's top 3 repos, judged for relevance to a Hermes deployment — not daily, trending shifts too slowly for that to be useful."
      />
      {onBack && (
        <button className="btn ghost compact" type="button" onClick={onBack}>
          ← Back to work queue
        </button>
      )}

      <div className="tracker-panel" style={{ marginTop: "1rem" }}>
        <div className="tracker-panel-head">
          <div>
            <span className="source-pill">Last run</span>
            <h2>{artifact ? formatDate(artifact.created_at) : "No digest has run yet"}</h2>
            <p>Runs automatically every Monday. You can also trigger it now for a demo.</p>
          </div>
          <button className="btn primary compact" type="button" disabled={running} onClick={runNow}>
            {running ? "Running..." : "Run now"}
          </button>
        </div>

        {error && <ErrorBox message={error} />}
        {loading && <p className="muted-note">Loading latest digest...</p>}

        {!loading && candidates.length === 0 && (
          <p className="muted-note">No candidates yet — run the digest to see this week's trending repos.</p>
        )}

        {candidates.length > 0 && (
          <div className="trending-candidate-list">
            {candidates.map((candidate) => (
              <div className="trending-candidate" key={candidate.repo_name}>
                <div className="trending-candidate-head">
                  <strong>{candidate.repo_name}</strong>
                  <span className="trending-candidate-stars">{candidate.stars} ★</span>
                  <span className={`tag ${candidate.relevant ? "good" : ""}`}>
                    {candidate.relevant ? "Relevant" : "Not judged / not relevant"}
                  </span>
                </div>
                <p>{candidate.description}</p>
                <p className="muted-note">{candidate.reasoning}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function MemoryCenterPrototype({ workspace, onBack }) {
  return (
    <section className="screen prototype-screen">
      <HeaderBlock
        eyebrow="Enhancement prototype"
        title="Similar past change memory"
        subtitle="Dummy memory page for reducing repeated analyst work by reusing previous impact areas, test scope, and clarification notes."
      />
      <ScreenBackBar onBack={onBack} label="Back to work queue" />
      <div className="prototype-hero">
        <div>
          <span className="source-pill">RAG / Memory</span>
          <h2>Compare this ticket with previous work</h2>
          <p>
            Instead of starting from zero, the analyst can see what was affected last time in {workspace?.name || "the project"} and what test
            coverage was reused.
          </p>
        </div>
        <div className="prototype-metrics">
          <Stat label="Matches" value="3" />
          <Stat label="Reusable tests" value="7" />
          <Stat label="Risk notes" value="4" />
        </div>
      </div>
      <div className="prototype-memory-grid">
        {MEMORY_MATCHES.map((item) => (
          <article key={item.title} className="prototype-memory-card">
            <div className="prototype-memory-score">{item.score}</div>
            <div>
              <h3>{item.title}</h3>
              <p>{item.outcome}</p>
              <span>{item.reuse}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function RoleScreen({ onSelect }) {
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Access profile"
        title="Select operating role"
        subtitle="Choose the delivery role for this workflow. Role permissions are enforced before any skill is executed."
      />
      <div className="role-grid">
        {ROLE_CARDS.map((card) => (
          <button className="role-card" key={card.id} type="button" onClick={() => onSelect(card.id)}>
            <span className="role-kicker">Role</span>
            <h2>{card.title}</h2>
            <p>{card.caption}</p>
          </button>
        ))}
      </div>
    </section>
  );
}

function AnalyzeScreen({ role, skill, allowedSkills, onSkill, onArtifact, onChangeRole }) {
  return (
    <section className="screen">
      <div className="role-pill">
        {ROLE_LABELS[role] || role}
        <button type="button" onClick={onChangeRole}>
          change
        </button>
      </div>
      <HeaderBlock
        eyebrow="Analysis"
        title="Run project analysis"
        subtitle="Each skill runs as a bounded action against the project graph and produces a reviewable delivery artifact."
      />
      <div className="skill-grid">
        {allowedSkills.map((id) => (
          <button
            key={id}
            type="button"
            className={`skill-tab ${skill === id ? "active" : ""}`}
            onClick={() => onSkill(id)}
          >
            <strong>{SKILL_META[id].title}</strong>
            <span>{SKILL_META[id].desc}</span>
          </button>
        ))}
      </div>
      {skill === "requirement-analysis" && <RequirementAnalysisForm role={role} onArtifact={onArtifact} />}
      {skill === "code-qa" && <CodeQaForm role={role} onArtifact={onArtifact} />}
      {skill === "impact-analysis" && <ImpactForm role={role} onArtifact={onArtifact} />}
      {skill === "test-case-gen" && <TestGenForm role={role} onArtifact={onArtifact} />}
    </section>
  );
}

function RequirementAnalysisForm({ role, onArtifact }) {
  const [description, setDescription] = useState("");
  return (
    <SkillForm
      label="Requirement or change request"
      value={description}
      onChange={setDescription}
      chips={CHIPS["requirement-analysis"]}
      placeholder="Describe the business change the analyst needs to assess"
      actionLabel="Analyze Requirement"
      onSubmit={async () => {
        const response = await api("/api/skills/requirement-analysis", {
          method: "POST",
          body: { profile: role, description },
        });
        return normalizeRequirementResponse(response);
      }}
      onArtifact={onArtifact}
    />
  );
}

function CodeQaForm({ role, onArtifact }) {
  const [question, setQuestion] = useState("");
  return (
    <SkillForm
      label="Codebase question"
      value={question}
      onChange={setQuestion}
      chips={CHIPS["code-qa"]}
      placeholder="What does checkout_endpoint depend on?"
      actionLabel="Run Code Analysis"
      onSubmit={() => api("/api/skills/code-qa", { method: "POST", body: { profile: role, question } })}
      onArtifact={onArtifact}
    />
  );
}

function ImpactForm({ role, onArtifact }) {
  const [changeRequest, setChangeRequest] = useState("");
  const [prUrl, setPrUrl] = useState("");
  const [prError, setPrError] = useState("");
  const [prLoading, setPrLoading] = useState(false);
  return (
    <>
      <SkillForm
        label="Requirement or change request"
        value={changeRequest}
        onChange={setChangeRequest}
        chips={CHIPS["impact-analysis"]}
        placeholder="Add automatic retry to charge_card when the payment gateway times out"
        actionLabel="Run Impact Assessment"
        onSubmit={() => api("/api/skills/impact-analysis", { method: "POST", body: { profile: role, change_request: changeRequest } })}
        onArtifact={onArtifact}
      />
      <div className="secondary-panel">
        <label className="field-label" htmlFor="pr-url">
          Analyze GitHub PR
        </label>
        <div className="inline-form">
          <input
            id="pr-url"
            type="text"
            value={prUrl}
            onChange={(event) => setPrUrl(event.target.value)}
            placeholder="https://github.com/owner/repo/pull/123"
          />
          <button
            className="btn ghost"
            type="button"
            disabled={prLoading}
            onClick={async () => {
              setPrError("");
              setPrLoading(true);
              try {
                const next = await api("/api/skills/impact-analysis/from-pr", {
                  method: "POST",
                  body: { profile: role, pr_url: prUrl },
                });
                onArtifact(next);
              } catch (error) {
                setPrError(error.message);
              } finally {
                setPrLoading(false);
              }
            }}
          >
            {prLoading ? "Importing..." : "Import PR Scope"}
          </button>
        </div>
        {prError && <ErrorBox message={prError} />}
      </div>
    </>
  );
}

function TestGenForm({ role, onArtifact }) {
  const [target, setTarget] = useState("");
  return (
    <SkillForm
      label="Function or endpoint"
      value={target}
      onChange={setTarget}
      chips={CHIPS["test-case-gen"]}
      placeholder="charge_card"
      actionLabel="Generate Test Plan"
      onSubmit={() => api("/api/skills/test-case-gen", { method: "POST", body: { profile: role, target } })}
      onArtifact={onArtifact}
      compact
    />
  );
}

function SkillForm({ label, value, onChange, chips, placeholder, actionLabel, onSubmit, onArtifact, compact }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  return (
    <div className="work-panel">
      <label className="field-label">{label}</label>
      <div className="chip-row">
        {chips.map((chip) => (
          <button key={chip} className="chip" type="button" onClick={() => onChange(chip)}>
            {chip}
          </button>
        ))}
      </div>
      <textarea
        className={compact ? "compact-textarea" : ""}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
      />
      {error && <ErrorBox message={error} />}
      <div className="action-row">
        <button
          className="btn primary"
          type="button"
          disabled={loading}
          onClick={async () => {
            setError("");
            if (!String(value || "").trim()) {
              setError(`${label} is required.`);
              return;
            }
            setLoading(true);
            try {
              const next = await onSubmit();
              onArtifact(next);
            } catch (err) {
              setError(err.message);
            } finally {
              setLoading(false);
            }
          }}
        >
          {loading ? "Running..." : actionLabel}
        </button>
      </div>
    </div>
  );
}

function ReportScreen({ artifact, role, handoffs, onReviewed, onRunAnother, onChangeRole, onArtifact, onReloadHandoffs }) {
  const reviewed = Boolean(artifact.reviewed);
  const requirementStatus = artifact.skill === "requirement-analysis" ? getRequirementStatus(artifact) : null;
  const reviewBlocked = requirementStatus === "NEEDS_CLARIFICATION";
  return (
    <section className="screen">
      <div className="role-pill">
        {ROLE_LABELS[role] || role}
        <button type="button" onClick={onChangeRole}>
          change
        </button>
      </div>
      <div className="report-header">
        <div>
          <div className="eyebrow">Artifact report</div>
          <h1>{SKILL_META[artifact.skill]?.title || artifact.skill}</h1>
          <p className="muted">Task ID: {artifact.task_id}</p>
        </div>
        <div className="review-actions">
          {requirementStatus && <span className={`status-pill ${statusClass(requirementStatus)}`}>{formatStatus(requirementStatus)}</span>}
          <span className={`status-pill ${reviewed ? "reviewed" : "unreviewed"}`}>{reviewed ? "Reviewed" : "Unreviewed"}</span>
          <button className="btn primary" type="button" disabled={reviewed || reviewBlocked} onClick={onReviewed}>
            {reviewed ? "Reviewed" : "Mark as reviewed"}
          </button>
        </div>
      </div>

      {artifact.skill === "requirement-analysis" && (
        <RequirementAnalysisReport artifact={artifact} result={artifact.result || {}} onArtifact={onArtifact} />
      )}
      {artifact.skill === "code-qa" && <CodeQaReport result={artifact.result || {}} />}
      {artifact.skill === "impact-analysis" && (
        <ImpactReport
          artifact={artifact}
          result={artifact.result || {}}
          handoffs={handoffs}
          onArtifact={onArtifact}
          onReloadHandoffs={onReloadHandoffs}
        />
      )}
      {artifact.skill === "test-case-gen" && <TestGenReport result={artifact.result || {}} />}
      {artifact.skill === "timeline-estimation" && <TimelineReport result={artifact.result || {}} />}
      {artifact.skill === "handoff-summary" && <HandoffSummaryReport result={artifact.result || {}} />}

      <details className="raw">
        <summary>View raw artifact.v1 response</summary>
        <pre>{JSON.stringify(artifact, null, 2)}</pre>
      </details>
      <div className="action-row">
        <button className="btn ghost" type="button" onClick={onRunAnother}>
          Run another analysis
        </button>
      </div>
    </section>
  );
}

function RequirementAnalysisReport({ artifact, result, onArtifact, reviewed, reviewBlocked, onReview }) {
  const status = getRequirementStatus(artifact);
  const ambiguities = result.ambiguities || [];
  const scopeClues = cleanScopeClues(result.potential_affected_areas || []);
  const analystConcerns = result.analyst_concerns || [];
  const projectRisks = result.project_risks || [];
  const missingInformation = result.missing_information || [];
  const ready = status !== "NEEDS_CLARIFICATION";
  const scope = result.scope_boundary || {};
  const hasScope =
    (scope.in_scope || []).length > 0 ||
    (scope.out_of_scope || []).length > 0 ||
    (scope.dependencies || []).length > 0;
  // Composed from real fields only (no fabricated confidence percentage --
  // the backend only ever returns categorical low/medium/high) so this
  // reads as the "why" behind the Ready/Needs-clarification call at a glance.
  const reasonLine = [
    `${missingInformation.length} missing item${missingInformation.length === 1 ? "" : "s"}`,
    `${projectRisks.length} risk${projectRisks.length === 1 ? "" : "s"} identified`,
    `confidence: ${String(result.confidence || "unknown").toUpperCase()}`,
  ].join(" · ");
  return (
    <>
      <section className={`triage-decision-bar ${ready ? "ready" : "needs-clarification"}`}>
        <div className="triage-decision-copy">
          <span className={`status-pill ${ready ? "reviewed" : "unreviewed"}`}>{ready ? "Ready" : "Needs clarification"}</span>
          <h2>{ready ? "Continue to impact analysis" : "Clarify before impact analysis"}</h2>
          <p>{ready ? "The request is usable. Confirm review to move forward." : "Answer the missing points first, then rerun triage."}</p>
          <p className="triage-decision-reason">{reasonLine}</p>
        </div>
        <div className="triage-decision-metrics">
          <span>
            <strong>{String(result.confidence || "unknown").toUpperCase()}</strong>
            Confidence
          </span>
          <span>
            <strong>{missingInformation.length}</strong>
            Missing
          </span>
          <span>
            <strong>{projectRisks.length}</strong>
            Risks
          </span>
          <span>
            <strong>{scopeClues.length}</strong>
            Areas
          </span>
        </div>
        {onReview && (
          <div className="triage-decision-action">
            <button className="btn primary" type="button" disabled={reviewed || reviewBlocked} onClick={onReview}>
              {reviewed ? "Reviewed" : ready ? "Mark reviewed and continue" : "Clarification required"}
            </button>
          </div>
        )}
      </section>
      {status === "NEEDS_CLARIFICATION" && <ClarificationPanel artifact={artifact} onArtifact={onArtifact} />}
      <RelatedTicketsStrip items={result.similar_past_changes || []} />
      <section className="triage-summary-grid">
        <article className="triage-summary-card business">
          <span className="source-pill">Business value</span>
          <p>{result.business_value || "No business value was detected. Confirm why this change matters before delivery starts."}</p>
        </article>
        <article className="triage-summary-card scope">
          <span className="source-pill">Scope boundary</span>
          {hasScope ? <ScopeBoundaryCompact scope={scope} /> : <p>No clear scope boundary detected yet.</p>}
        </article>
        <article className="triage-summary-card risks">
          <span className="source-pill">Top risks</span>
          <TopRisksCompact items={projectRisks} />
        </article>
      </section>
      <EvidenceTraceabilityPanel
        title="Requirement evidence traceability"
        subtitle="Shows which ticket detail, AI finding, or memory item supports the triage decision."
        items={buildRequirementTraceability(result)}
      />
      <section className="triage-detail-stack">
        <details className="triage-detail-panel" open={missingInformation.length > 0}>
          <summary>
            <span>Clarification & rules</span>
            <small>{missingInformation.length} missing / {(result.business_rules || []).length} rules</small>
          </summary>
          <SimpleList title="Missing information" items={missingInformation} tone={status === "NEEDS_CLARIFICATION" ? "danger" : undefined} />
          <SimpleList title="Business rules" items={result.business_rules || []} />
          <SimpleList title="Assumptions" items={result.assumptions || []} />
          {ambiguities.length > 0 && (
            <section className="list-section">
              <h3>Ambiguities</h3>
              <ul className="evidence-list">
                {ambiguities.map((item, index) => (
                  <li key={index}>
                    <code>{item.evidence || "requirement text"}</code>
                    <span>{item.note}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </details>
        <details className="triage-detail-panel">
          <summary>
            <span>Scope, concerns & risks</span>
            <small>{analystConcerns.length} concerns / {projectRisks.length} risks</small>
          </summary>
          <RequirementDecisionPanel result={result} />
          <AnalystConcerns items={analystConcerns} />
          <ProjectRisks items={projectRisks} />
          <ScopeClues items={scopeClues} />
        </details>
        <details className="triage-detail-panel">
          <summary>
            <span>Evidence & memory</span>
            <small>{(result.evidence || []).length} evidence / {(result.similar_past_changes || []).length} matches</small>
          </summary>
          <EvidenceList title="Evidence" items={result.evidence || []} sourceKey="source" claimKey="claim" />
          <SimilarPastChanges items={result.similar_past_changes || []} />
          <ClarificationHistoryPanel artifact={artifact} />
        </details>
      </section>
    </>
  );
}

function ScopeBoundaryCompact({ scope }) {
  const inScope = scope.in_scope || [];
  const outOfScope = scope.out_of_scope || [];
  const dependencies = scope.dependencies || [];
  return (
    <div className="scope-compact-grid">
      <ScopeCompactColumn title="In" items={inScope} />
      <ScopeCompactColumn title="Out" items={outOfScope} />
      <ScopeCompactColumn title="Depends" items={dependencies} />
    </div>
  );
}

function ScopeCompactColumn({ title, items }) {
  return (
    <div className="scope-compact-column">
      <strong>{title}</strong>
      <span>{items[0] || "None detected"}</span>
      {items.length > 1 && <small>+{items.length - 1} more</small>}
    </div>
  );
}

function TopRisksCompact({ items }) {
  if (!items.length) {
    return <p>No project risks detected.</p>;
  }
  return (
    <ul className="top-risk-list">
      {items.slice(0, 3).map((item, index) => {
        const priority = (item.priority || "P3").toUpperCase();
        const severity = item.severity || (priority === "P1" ? "high" : priority === "P2" ? "medium" : "low");
        return (
          <li key={`${priority}-${item.area || "risk"}-${index}`}>
            <span className={`tag ${severity === "high" ? "bad" : severity === "medium" ? "warn" : "good"}`}>{priority}</span>
            <strong>{formatScopeClue(item.area || "project risk")}</strong>
            <p>{item.reason}</p>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Past reviewed tickets whose memory card (MemoryCardService) overlapped this
 * ticket's text — "Memory" retrieval, not a live evidence citation, so it
 * gets its own section rather than folding into EvidenceList.
 */
// Same data SimilarPastChanges renders (real similarity score, summary,
// reviewed_at) -- previously only shown buried inside the third collapsed
// "Evidence & memory" panel. Surfacing the top match(es) near the decision
// hero means the analyst sees "we've seen something like this before"
// without having to know to expand that panel.
function RelatedTicketsStrip({ items }) {
  const top = (items || []).slice(0, 2);
  if (top.length === 0) return null;
  return (
    <section className="related-tickets-strip">
      <span className="source-pill">Related tickets</span>
      <div className="related-tickets-list">
        {top.map((item) => (
          <article key={item.task_id} className="related-ticket-card">
            <div className="related-ticket-top">
              <span className="related-ticket-score">Match score {item.score}</span>
              {item.reviewed_at && <span className="muted-note">{formatDate(item.reviewed_at)}</span>}
            </div>
            <p>{truncateText(item.summary, 160)}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function SimilarPastChanges({ items }) {
  if (!items.length) return null;
  return (
    <section className="list-section similar-past-changes-section">
      <h3>Similar past changes</h3>
      <ul className="simple-list">
        {items.map((item) => (
          <li key={item.task_id}>
            <div className="similar-past-change-row">
              <span className="source-pill">Match score {item.score}</span>
              <span className="muted-note">{formatDate(item.reviewed_at)}</span>
            </div>
            <p>{item.summary}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ClarificationHistoryPanel({ artifact }) {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const items = await api(`/api/artifacts/${artifact.task_id}/clarification-history`);
        if (!cancelled) setHistory(items);
      } catch {
        if (!cancelled) setHistory([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [artifact.task_id]);

  if (loading || history.length < 2) {
    return null;
  }

  return (
    <section className="list-section clarification-history-section">
      <h3>Clarification history</h3>
      <div className="clarification-history-list">
        {history.map((round, index) => (
          <article key={round.task_id} className="clarification-history-round">
            <div className="clarification-history-top">
              <span className="source-pill">Round {index + 1}</span>
              <span>{formatDate(round.created_at)}</span>
            </div>
            {round.clarification_answered && (
              <p>
                <strong>Analyst answered:</strong> {round.clarification_answered}
              </p>
            )}
            {round.missing_information.length > 0 ? (
              <SimpleList title="Still missing after this round" items={round.missing_information} tone="danger" />
            ) : (
              <p className="muted-note">No missing information after this round.</p>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}

function RequirementDecisionPanel({ result }) {
  const scope = result.scope_boundary || {};
  const hasScope =
    (scope.in_scope || []).length > 0 ||
    (scope.out_of_scope || []).length > 0 ||
    (scope.dependencies || []).length > 0;
  const businessValue = result.business_value;
  if (!businessValue && !hasScope) return null;
  return (
    <section className="decision-panel">
      {businessValue && (
        <article className="decision-card business-value-card">
          <span className="source-pill">Business value</span>
          <p>{businessValue}</p>
        </article>
      )}
      {hasScope && (
        <article className="decision-card scope-boundary-card">
          <span className="source-pill">Scope boundary</span>
          <div className="scope-boundary-grid">
            <ScopeBoundaryColumn title="In scope" items={scope.in_scope || []} />
            <ScopeBoundaryColumn title="Out of scope" items={scope.out_of_scope || []} />
            <ScopeBoundaryColumn title="Dependencies" items={scope.dependencies || []} />
          </div>
        </article>
      )}
    </section>
  );
}

function ScopeBoundaryColumn({ title, items }) {
  return (
    <div className="scope-boundary-column">
      <strong>{title}</strong>
      {items.length > 0 ? (
        <ul>
          {items.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      ) : (
        <span className="empty-inline">None detected</span>
      )}
    </div>
  );
}

function ProjectRisks({ items }) {
  if (!items.length) return null;
  return (
    <section className="list-section project-risks-section">
      <h3>Project risks</h3>
      <div className="project-risk-grid">
        {items.map((item, index) => {
          const priority = (item.priority || "P3").toUpperCase();
          const severity = item.severity || (priority === "P1" ? "high" : priority === "P2" ? "medium" : "low");
          return (
            <article key={`${priority}-${item.area || "risk"}-${index}`} className={`project-risk-card ${severity}`}>
              <div className="project-risk-top">
                <span className={`tag ${severity === "high" ? "bad" : severity === "medium" ? "warn" : "good"}`}>{priority}</span>
                <span className="concern-category">{formatScopeClue(item.area || "project risk")}</span>
              </div>
              <p>{item.reason}</p>
              {item.mitigation && <strong>Mitigation: {item.mitigation}</strong>}
              {item.owner && <small>Owner: {item.owner}</small>}
            </article>
          );
        })}
      </div>
    </section>
  );
}

function AnalystConcerns({ items }) {
  if (!items.length) return null;
  return (
    <section className="list-section analyst-concerns-section">
      <h3>Analyst concerns</h3>
      <div className="analyst-concern-grid">
        {items.map((item, index) => {
          const severity = item.severity || "low";
          return (
            <article key={`${item.category}-${index}`} className={`analyst-concern-card ${severity}`}>
              <div className="analyst-concern-top">
                <span className="concern-category">{formatScopeClue(item.category || "concern")}</span>
                <span className={`tag ${severity === "high" ? "bad" : severity === "medium" ? "warn" : "good"}`}>{severity}</span>
              </div>
              <p>{item.note}</p>
              {item.question && <strong>{item.question}</strong>}
              {item.evidence && <small>Evidence: {item.evidence}</small>}
            </article>
          );
        })}
      </div>
    </section>
  );
}

function ClarificationPanel({ artifact, onArtifact }) {
  const clarificationItems = useMemo(() => buildClarificationItems(artifact.result || {}), [artifact]);
  const [answers, setAnswers] = useState({});
  const [additionalInfo, setAdditionalInfo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const profile = (artifact.agent || "").replace(/-agent$/, "") || ANALYST_PROFILE;

  function updateAnswer(id, value) {
    setAnswers((prev) => ({ ...prev, [id]: value }));
  }

  function answeredItems() {
    return clarificationItems
      .map((item) => ({ ...item, answer: answers[item.id] || "" }))
      .filter((item) => item.answer.trim());
  }

  return (
    <section className="handoff-panel clarification-panel">
      <h3>Clarification</h3>
      <p className="muted">Answer each unclear point, then rerun requirement analysis as a linked artifact.</p>
      {clarificationItems.length > 0 && (
        <div className="clarification-answer-list">
          {clarificationItems.map((item) => (
            <article key={item.id} className="clarification-answer-card">
              <div className="clarification-answer-top">
                <span className="source-pill">{formatScopeClue(item.type)}</span>
                {item.category && <span className="concern-category">{formatScopeClue(item.category)}</span>}
              </div>
              <strong>{item.question}</strong>
              {item.evidence && <small>Evidence: {item.evidence}</small>}
              <textarea
                className="compact-textarea"
                value={answers[item.id] || ""}
                onChange={(event) => updateAnswer(item.id, event.target.value)}
                placeholder="Add the confirmed answer or analyst decision for this item"
              />
            </article>
          ))}
        </div>
      )}
      <label className="field-label">Additional clarification note</label>
      <textarea
        className="compact-textarea"
        value={additionalInfo}
        onChange={(event) => setAdditionalInfo(event.target.value)}
        placeholder="Optional: add a general note that does not map to one item."
      />
      {error && <ErrorBox message={error} />}
      <button
        className="btn primary"
        type="button"
        disabled={loading}
        onClick={async () => {
          setError("");
          const clarificationAnswers = answeredItems();
          if (clarificationAnswers.length === 0 && !additionalInfo.trim()) {
            setError("At least one clarification answer or additional note is required.");
            return;
          }
          setLoading(true);
          try {
            const response = await api(`/api/artifacts/${artifact.task_id}/clarify`, {
              method: "POST",
              body: {
                profile,
                additional_info: additionalInfo,
                clarification_answers: clarificationAnswers.map((item) => ({
                  type: item.type,
                  category: item.category,
                  question: item.question,
                  answer: item.answer,
                  evidence: item.evidence,
                })),
              },
            });
            onArtifact(normalizeRequirementResponse(response));
          } catch (err) {
            setError(err.message);
          } finally {
            setLoading(false);
          }
        }}
      >
        {loading ? "Submitting..." : "Submit Clarification"}
      </button>
    </section>
  );
}

function buildClarificationItems(result) {
  const items = [];
  const seen = new Set();
  (result.missing_information || []).forEach((question, index) => {
    addClarificationItem(items, seen, {
      id: `missing-${index}`,
      type: "missing information",
      category: "",
      question,
      evidence: "requirement analysis",
    });
  });
  (result.analyst_concerns || []).forEach((concern, index) => {
    const question = concern.question || concern.note;
    if (!question) return;
    addClarificationItem(items, seen, {
      id: `concern-${index}`,
      type: "analyst concern",
      category: concern.category || "",
      question,
      evidence: concern.evidence || concern.note || "",
    });
  });
  return items;
}

function addClarificationItem(items, seen, item) {
  const normalized = String(item.question || "").trim().toLowerCase();
  if (!normalized || seen.has(normalized)) {
    return;
  }
  seen.add(normalized);
  items.push({ ...item, question: String(item.question).trim() });
}

function CodeQaReport({ result }) {
  return (
    <>
      <section className="answer-panel">{result.answer || "(no answer)"}</section>
      <EvidenceList title="Evidence" items={result.evidence || []} sourceKey="source" claimKey="claim" />
      {(result.ungrounded || []).length > 0 && <SimpleList title="Ungrounded claims" items={result.ungrounded} tone="danger" />}
    </>
  );
}

function ImpactReport({ artifact, result, handoffs, onArtifact, onReloadHandoffs }) {
  const reviewed = Boolean(artifact.reviewed);
  return (
    <>
      <ImpactVisualSummary result={result} />
      <ScopeCreepPanel findings={result.scope_creep_findings} />
      <EvidenceTraceabilityPanel
        title="Impact evidence traceability"
        subtitle="Connects each affected area or risk back to codebase, memory, or missing evidence."
        items={buildImpactTraceability(result)}
      />
      {reviewed && (
        <div className="handoff-grid">
          <TimelineHandoff artifact={artifact} onArtifact={onArtifact} />
          <ExternalHandoff artifact={artifact} handoffs={handoffs} onReload={onReloadHandoffs} />
        </div>
      )}
      <section className="triage-detail-stack impact-detail-stack">
        <details className="triage-detail-panel">
          <summary>
            <span>Affected module evidence</span>
            <small>{(result.affected_modules || []).length} modules</small>
          </summary>
          <ModuleList artifact={artifact} modules={result.affected_modules || []} reviewed={reviewed} onArtifact={onArtifact} />
        </details>
        <details className="triage-detail-panel">
          <summary>
            <span>Risks & memory</span>
            <small>{(result.risk_notes || []).length} notes / {(result.similar_past_changes || []).length} matches</small>
          </summary>
          <EvidenceList title="Related historical issues" items={result.risk_notes || []} sourceKey="evidence" claimKey="note" />
          {(result.missing_evidence || []).length > 0 && <SimpleList title="Missing evidence" items={result.missing_evidence} tone="danger" />}
          <SimilarPastChanges items={result.similar_past_changes || []} />
        </details>
      </section>
    </>
  );
}

// Only populated when the impact artifact came from "Import PR Scope" -- see
// ScopeCreepDetector.java. Path matching there is best-effort (the codebase
// graph's indexed root and GitHub's repo root aren't guaranteed to agree),
// so this is framed as a check to verify, not a certainty.
function ScopeCreepPanel({ findings }) {
  const items = findings || [];
  if (items.length === 0) return null;
  const undeclared = items.filter((item) => item.kind === "undeclared_change");
  const untouched = items.filter((item) => item.kind === "declared_untouched");
  return (
    <section className="scope-creep-panel">
      <div className="scope-creep-head">
        <span className="source-pill">Scope creep check</span>
        <strong>PR diff vs. declared affected modules</strong>
        <p>Best-effort file-path comparison against the PR's actual changes — verify before treating as ground truth.</p>
      </div>
      <div className="scope-creep-list">
        {undeclared.map((item, index) => (
          <article key={`undeclared-${index}`} className="scope-creep-item tone-warn">
            <span className="scope-creep-kind">Undeclared change</span>
            <code>{item.path}</code>
            <p>{item.detail}</p>
          </article>
        ))}
        {untouched.map((item, index) => (
          <article key={`untouched-${index}`} className="scope-creep-item tone-info">
            <span className="scope-creep-kind">Declared, untouched</span>
            <code>{item.path}</code>
            <p>{item.detail}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function ImpactVisualSummary({ result }) {
  const modules = result.affected_modules || [];
  const groups = groupImpactModules(modules);
  const primaryGroups = groups.filter((group) => group.items.length > 0).slice(0, 4);
  const effort = `${result.rough_effort?.estimate || "?"}${result.rough_effort?.basis ? ` - ${result.rough_effort.basis}` : ""}`;
  return (
    <section className="impact-visual-panel">
      <div className="impact-visual-head">
        <div>
          <span className="source-pill">Blast radius map</span>
          <h2>Requirement impact flow</h2>
          <p>Follow the change from reviewed requirement to affected project areas, then into testing and handoff.</p>
        </div>
        <div className="impact-visual-metrics">
          <span>
            <strong>{String(result.risk_level || "unknown").toUpperCase()}</strong>
            Risk
          </span>
          <span>
            <strong>{modules.length}</strong>
            Modules
          </span>
          <span>
            <strong>{String(result.confidence || "unknown").toUpperCase()}</strong>
            Confidence
          </span>
        </div>
      </div>
      <div className="impact-flow-map" aria-label="Impact flow diagram">
        <div className="impact-flow-node source">
          <span>Upstream</span>
          <strong>Reviewed requirement</strong>
          <small>Confirmed ticket scope</small>
        </div>
        <div className="impact-flow-arrow">-&gt;</div>
        <div className="impact-flow-node mcp">
          <span>Analysis</span>
          <strong>Project graph + MCP</strong>
          <small>{effort}</small>
        </div>
        <div className="impact-flow-arrow">-&gt;</div>
        <div className="impact-flow-groups">
          {primaryGroups.length > 0 ? (
            primaryGroups.map((group) => (
              <div key={group.key} className={`impact-area-node ${group.key}`}>
                <span>{group.label}</span>
                <strong>{group.items.length}</strong>
                <small>{group.items.slice(0, 2).map((item) => shortModulePath(item.path || item.name)).join(", ")}</small>
              </div>
            ))
          ) : (
            <div className="impact-area-node other">
              <span>No modules</span>
              <strong>0</strong>
              <small>No affected area resolved</small>
            </div>
          )}
        </div>
        <div className="impact-flow-arrow">-&gt;</div>
        <div className="impact-flow-node downstream">
          <span>Downstream</span>
          <strong>Testing scope</strong>
          <small>Generate cases, review, handoff</small>
        </div>
      </div>
      <ImpactGroupSummary groups={groups} />
    </section>
  );
}

function ImpactGroupSummary({ groups }) {
  const visible = groups.filter((group) => group.items.length > 0);
  if (!visible.length) return null;
  return (
    <div className="impact-group-summary">
      {visible.map((group) => (
        <article key={group.key}>
          <div>
            <span className={`impact-dot ${group.key}`} />
            <strong>{group.label}</strong>
          </div>
          <p>{group.items.length} affected</p>
          <small>{group.items.slice(0, 3).map((item) => shortModulePath(item.path || item.name)).join(" / ")}</small>
        </article>
      ))}
    </div>
  );
}

function groupImpactModules(modules) {
  const groups = [
    { key: "ui", label: "UI / Views", items: [] },
    { key: "logic", label: "Business logic", items: [] },
    { key: "data", label: "Data model", items: [] },
    { key: "notification", label: "Notifications", items: [] },
    { key: "test", label: "Tests", items: [] },
    { key: "other", label: "Other", items: [] },
  ];
  const byKey = Object.fromEntries(groups.map((group) => [group.key, group]));
  modules.forEach((module) => {
    const path = String(module.path || module.name || "").toLowerCase();
    if (path.includes("test")) byKey.test.items.push(module);
    else if (path.includes("notification") || path.includes("mail")) byKey.notification.items.push(module);
    else if (path.includes("model") || path.includes("migration") || path.includes("database")) byKey.data.items.push(module);
    else if (path.includes("view") || path.includes("blade") || path.includes("frontend") || path.includes("resource")) byKey.ui.items.push(module);
    else if (path.includes("controller") || path.includes("service") || path.includes("http") || path.includes("app/")) byKey.logic.items.push(module);
    else byKey.other.items.push(module);
  });
  return groups;
}

function shortModulePath(path) {
  const parts = String(path || "").split(/[\\/]/).filter(Boolean);
  return parts.slice(-2).join("/") || String(path || "unknown");
}

function TimelineHandoff({ artifact, onArtifact }) {
  const [developers, setDevelopers] = useState(1);
  const [testersAvailable, setTestersAvailable] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  return (
    <section className="handoff-panel">
      <h3>Timeline estimation</h3>
      <div className="compact-controls">
        <label>
          Developers
          <input type="number" min="1" value={developers} onChange={(event) => setDevelopers(event.target.value)} />
        </label>
        <label className="check">
          <input type="checkbox" checked={testersAvailable} onChange={(event) => setTestersAvailable(event.target.checked)} />
          Tester available
        </label>
      </div>
      {error && <ErrorBox message={error} />}
      <button
        className="btn primary"
        type="button"
        disabled={loading}
        onClick={async () => {
          setError("");
          setLoading(true);
          try {
            const next = await api(`/api/artifacts/${artifact.task_id}/handoff/timeline-estimation`, {
              method: "POST",
              body: {
                profile: "project-analyst",
                developers: Number(developers || 1),
                testers_available: testersAvailable,
              },
            });
            onArtifact(next);
          } catch (err) {
            setError(err.message);
          } finally {
            setLoading(false);
          }
        }}
      >
        {loading ? "Generating..." : "Generate Timeline"}
      </button>
    </section>
  );
}

function ExternalHandoff({ artifact, handoffs, onReload, initialSummary = "Reviewed impact analysis" }) {
  const [summary, setSummary] = useState(initialSummary);
  const [jiraIssueKey, setJiraIssueKey] = useState("");
  const [commentDraft, setCommentDraft] = useState(() => buildJiraCommentDraft(artifact, initialSummary));
  const [prUrl, setPrUrl] = useState("");
  const [dryRun, setDryRun] = useState(true);
  const [approved, setApproved] = useState(false);
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const result = artifact.result || {};
  const affectedCount = result.affected_modules?.length || result.affectedModules?.length || 0;
  const testCount = result.test_scenarios?.length || result.testScenarios?.length || result.test_cases?.length || result.testCases?.length || 0;
  const riskLevel = result.risk_level || result.riskLevel || "not stated";

  async function send(destination) {
    setError("");
    if (!approved) {
      setError("Analyst approval is required before sending this handoff outside the platform.");
      return;
    }
    setLoading(destination);
    try {
      await api(`/api/artifacts/${artifact.task_id}/external-handoff`, {
        method: "POST",
        body: {
          destination,
          summary,
          description: commentDraft,
          pr_url: prUrl,
          jira_issue_key: jiraIssueKey,
          dry_run: dryRun,
        },
      });
      await onReload();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading("");
    }
  }

  return (
    <section className="handoff-panel">
      <h3>Controlled external handoff</h3>
      <div className="handoff-approval-box">
        <div>
          <span className="source-pill">AI draft reviewed</span>
          <strong>Human approval required before Jira, PR, or Hermes handoff</strong>
          <p>
            This handoff uses reviewed artifact <code>{artifact.task_id}</code>. Confirm the summary and evidence before sending it to an external tool.
          </p>
        </div>
        <div className="handoff-approval-metrics">
          <span>
            <strong>{String(riskLevel).toUpperCase()}</strong>
            Risk
          </span>
          <span>
            <strong>{affectedCount}</strong>
            Modules
          </span>
          <span>
            <strong>{testCount}</strong>
            Tests
          </span>
        </div>
      </div>
      <label className="field-label">Summary</label>
      <input type="text" value={summary} onChange={(event) => setSummary(event.target.value)} />
      <label className="field-label">Jira issue key for comment</label>
      <input type="text" value={jiraIssueKey} onChange={(event) => setJiraIssueKey(event.target.value)} placeholder="KAN-1" />
      <label className="field-label">Jira comment draft</label>
      <textarea
        className="compact-textarea jira-comment-draft"
        value={commentDraft}
        onChange={(event) => setCommentDraft(event.target.value)}
      />
      <label className="field-label">Bitbucket PR URL</label>
      <input type="text" value={prUrl} onChange={(event) => setPrUrl(event.target.value)} placeholder="https://bitbucket.org/workspace/repo/pull-requests/123" />
      <label className="check">
        <input type="checkbox" checked={dryRun} onChange={(event) => setDryRun(event.target.checked)} />
        Dry-run only
      </label>
      <label className="check approval-check">
        <input type="checkbox" checked={approved} onChange={(event) => setApproved(event.target.checked)} />
        I reviewed the AI draft and approve this external handoff.
      </label>
      {error && <ErrorBox message={error} />}
      <div className="action-row">
        <button className="btn primary" type="button" disabled={Boolean(loading) || !approved} onClick={() => send("jira")}>
          {loading === "jira" ? "Creating..." : "Create Jira Issue"}
        </button>
        <button className="btn ghost" type="button" disabled={Boolean(loading) || !approved || !jiraIssueKey.trim()} onClick={() => send("jira-comment")}>
          {loading === "jira-comment" ? "Posting..." : "Post Jira Comment"}
        </button>
        <button className="btn ghost" type="button" disabled={Boolean(loading) || !approved} onClick={() => send("bitbucket")}>
          {loading === "bitbucket" ? "Posting..." : "Comment Bitbucket PR"}
        </button>
        <button className="btn ghost" type="button" disabled={Boolean(loading) || !approved} onClick={() => send("hermes")}>
          {loading === "hermes" ? "Sending..." : "Send to Hermes"}
        </button>
      </div>
      <ul className="handoff-list">
        {handoffs.length === 0 && <li>No external handoff recorded yet.</li>}
        {handoffs.map((item) => (
          <li key={item.id}>
            <strong>
              {item.destination} - {item.status}
              {item.dry_run ? " (dry-run)" : ""}
            </strong>
            <span>{item.message}</span>
            {item.external_url && (
              <a href={item.external_url} target="_blank" rel="noreferrer">
                Open external record
              </a>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function buildJiraCommentDraft(artifact, fallbackTitle) {
  const result = artifact.result || {};
  const lines = [
    `${fallbackTitle}`,
    "",
    `Artifact: ${artifact.task_id}`,
    `Skill: ${artifact.skill}`,
    "",
  ];
  if (result.requirement_summary) {
    lines.push("Requirement summary:", result.requirement_summary, "");
  }
  if (result.risk_level) {
    lines.push(`Risk level: ${String(result.risk_level).toUpperCase()}`);
  }
  if (result.confidence) {
    lines.push(`Confidence: ${String(result.confidence).toUpperCase()}`);
  }
  const affected = result.affected_modules || [];
  if (affected.length > 0) {
    lines.push("", "Affected areas:");
    affected.slice(0, 5).forEach((item) => {
      lines.push(`- ${item.path || item.name || "Affected module"}: ${item.reason || "Review required"}`);
    });
  }
  const risks = result.risk_notes || result.project_risks || [];
  if (risks.length > 0) {
    lines.push("", "Risks / follow-up:");
    risks.slice(0, 5).forEach((item) => {
      lines.push(`- ${item.note || item.reason || item.mitigation || item}`);
    });
  }
  const missing = result.missing_evidence || result.missing_information || [];
  if (missing.length > 0) {
    lines.push("", "Needs confirmation:");
    missing.slice(0, 5).forEach((item) => lines.push(`- ${item}`));
  }
  lines.push("", "Analyst decision: Reviewed and ready for team follow-up.");
  return lines.filter((line, index, all) => line !== "" || all[index - 1] !== "").join("\n").trim();
}

function ModuleList({ artifact, modules, reviewed, onArtifact }) {
  const [loadingTarget, setLoadingTarget] = useState("");
  if (!modules.length) {
    return <SimpleList title="Affected modules" items={["No affected modules resolved in the project graph."]} />;
  }
  return (
    <section className="list-section">
      <h3>Affected modules</h3>
      <ul className="evidence-list">
        {modules.map((item, index) => (
          <li key={`${item.path}-${index}`}>
            <code>{item.path}</code>
            <span>{item.reason}</span>
            {reviewed && item.name && (
              <button
                className="btn ghost compact push"
                type="button"
                disabled={loadingTarget === item.name}
                onClick={async () => {
                  setLoadingTarget(item.name);
                  try {
                    const next = await api(`/api/artifacts/${artifact.task_id}/handoff/test-case-gen`, {
                      method: "POST",
                      body: { profile: "tester", target: item.name },
                    });
                    onArtifact(next);
                  } finally {
                    setLoadingTarget("");
                  }
                }}
              >
                {loadingTarget === item.name ? "Generating..." : "Send to Tester"}
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function TestGenReport({ result }) {
  const cases = result.cases || [];
  return (
    <>
      <div className="stat-grid">
        <Stat label="Target" value={result.target || "(unknown)"} />
        <Stat label="Confidence" value={<Tag kind="confidence" value={result.confidence} />} />
      </div>
      <SimpleList title="Existing coverage" items={result.existing_coverage || []} />
      {["positive", "negative", "edge"].map((type) => (
        <SimpleList
          key={type}
          title={`Cases - ${type}`}
          items={cases.filter((item) => item.type === type).map((item) => `${item.id}: ${item.input} | Expected: ${item.expected} | ${item.rationale}`)}
        />
      ))}
      <SimpleList title="Regression checklist" items={result.regression_checklist || []} />
      {(result.missing_evidence || []).length > 0 && <SimpleList title="Missing evidence" items={result.missing_evidence} tone="danger" />}
    </>
  );
}

function TestScopeManager({ testArtifact, scopeArtifact, onSave, onReview }) {
  const generatedCases = testArtifact.result?.cases || [];
  const [cases, setCases] = useState(() => buildManagedCases(generatedCases));
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const result = scopeArtifact?.result || {};
  const reviewed = Boolean(scopeArtifact?.reviewed);
  const scopeCounts = countManagedCases(cases);
  const missingEvidence = testArtifact.result?.missing_evidence || [];

  function updateCase(index, patch) {
    setCases((prev) => prev.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)));
  }

  function addManualCase() {
    setCases((prev) => [
      ...prev,
      {
        id: `MANUAL-${prev.length + 1}`,
        type: "manual",
        input: "",
        expected: "",
        rationale: "",
        evidence: "analyst added",
        status: "accepted",
        priority: "medium",
      },
    ]);
  }

  async function saveScope() {
    setError("");
    const usefulCases = cases.filter((item) => item.input.trim() || item.expected.trim());
    if (usefulCases.length === 0) {
      setError("At least one test case with input or expected result is required.");
      return;
    }
    setLoading("save");
    try {
      await onSave(testArtifact, usefulCases, notes);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading("");
    }
  }

  async function markReviewed() {
    if (!scopeArtifact) return;
    setError("");
    setLoading("review");
    try {
      await onReview(testArtifact, scopeArtifact);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading("");
    }
  }

  return (
    <section className="test-scope-panel">
      <div className="test-scope-header">
        <div>
          <h4>Testing scope review</h4>
          <p className="muted">Accept the scenarios that should go into QA/UAT scope, reject noise, and keep lower priority items in backlog.</p>
        </div>
        {scopeArtifact && (
          <span className={`status-pill ${reviewed ? "reviewed" : "unreviewed"}`}>
            {reviewed ? "Scope reviewed" : formatStatus(result.readiness || "READY_FOR_REVIEW")}
          </span>
        )}
      </div>
      <div className="test-scope-decision-bar">
        <div>
          <strong>{scopeCounts.accepted} must test</strong>
          <span>{scopeCounts.backlog} backlog / {scopeCounts.rejected} rejected</span>
        </div>
        <div className="test-scope-status-pills">
          <span>{scopeCounts.high} high priority</span>
          <span>{cases.length} total cases</span>
        </div>
        <div className="test-scope-actions">
          <button className="btn primary compact" type="button" disabled={Boolean(loading)} onClick={saveScope}>
            {loading === "save" ? "Saving..." : scopeArtifact ? "Save scope" : "Save testing scope"}
          </button>
          <button className="btn ghost compact" type="button" disabled={!scopeArtifact || reviewed || Boolean(loading)} onClick={markReviewed}>
            {reviewed ? "Reviewed" : loading === "review" ? "Reviewing..." : "Mark reviewed"}
          </button>
        </div>
      </div>
      {missingEvidence.length > 0 && (
        <div className="testing-warning-banner">
          <strong>Confirm before final QA/UAT scope.</strong>
          <span>{missingEvidence[0]}</span>
        </div>
      )}
      <TestScopeBoard cases={cases} onChange={updateCase} />
      <div className="test-case-editor-list">
        {cases.map((item, index) => (
          <div key={`${item.id}-${index}`} className={`test-case-editor ${item.status}`}>
            <div className="test-case-editor-top">
              <strong>{item.id}</strong>
              <select value={item.status} onChange={(event) => updateCase(index, { status: event.target.value })}>
                <option value="accepted">Accepted</option>
                <option value="rejected">Rejected</option>
                <option value="backlog">Backlog</option>
              </select>
              <select value={item.priority} onChange={(event) => updateCase(index, { priority: event.target.value })}>
                <option value="high">High</option>
                <option value="medium">Medium</option>
                <option value="low">Low</option>
              </select>
            </div>
            <div className="test-case-editor-grid">
              <label>
                Input / action
                <textarea value={item.input} onChange={(event) => updateCase(index, { input: event.target.value })} />
              </label>
              <label>
                Expected result
                <textarea value={item.expected} onChange={(event) => updateCase(index, { expected: event.target.value })} />
              </label>
            </div>
            <label>
              Analyst rationale
              <input type="text" value={item.rationale} onChange={(event) => updateCase(index, { rationale: event.target.value })} />
            </label>
            <small>{item.type} · {item.evidence || "no evidence"}</small>
          </div>
        ))}
      </div>
      <label className="field-label">Testing notes</label>
      <textarea
        className="compact-textarea"
        value={notes}
        onChange={(event) => setNotes(event.target.value)}
        placeholder="Example: Prioritize accepted high-risk cases for UAT; keep backlog items for regression hardening."
      />
      {scopeArtifact && (
        <div className="test-scope-summary">
          <span>{result.accepted_count || 0} accepted</span>
          <span>{result.rejected_count || 0} rejected</span>
          <span>{result.backlog_count || 0} backlog</span>
          <code>{scopeArtifact.task_id}</code>
        </div>
      )}
      {error && <ErrorBox message={error} />}
      <div className="action-row">
        <button className="btn ghost compact" type="button" onClick={addManualCase}>
          Add manual case
        </button>
      </div>
    </section>
  );
}

function TestScopeBoard({ cases, onChange }) {
  const columns = [
    { id: "accepted", title: "Must test", hint: "QA/UAT scope" },
    { id: "backlog", title: "Regression / optional", hint: "Keep for later" },
    { id: "rejected", title: "Rejected", hint: "Noise or not relevant" },
  ];
  return (
    <div className="test-scope-board">
      {columns.map((column) => {
        const items = cases
          .map((item, index) => ({ ...item, originalIndex: index }))
          .filter((item) => item.status === column.id);
        return (
          <section key={column.id} className={`test-scope-column ${column.id}`}>
            <div className="test-scope-column-head">
              <div>
                <strong>{column.title}</strong>
                <span>{column.hint}</span>
              </div>
              <b>{items.length}</b>
            </div>
            <div className="test-scope-card-list">
              {items.length === 0 && <p className="empty-monitor-state">No cases here.</p>}
              {items.map((item) => (
                <article key={`${item.id}-${item.originalIndex}`} className={`test-scope-card ${item.status}`}>
                  <div className="test-scope-card-top">
                    <span className="source-pill">{item.id}</span>
                    <span className={`tag ${item.priority === "high" ? "bad" : item.priority === "medium" ? "warn" : "good"}`}>
                      {item.priority}
                    </span>
                  </div>
                  <strong>{item.input || "Untitled test case"}</strong>
                  <p>{item.expected || "No expected result recorded."}</p>
                  <div className="test-scope-card-controls">
                    <select value={item.status} onChange={(event) => onChange(item.originalIndex, { status: event.target.value })}>
                      <option value="accepted">Must test</option>
                      <option value="backlog">Backlog</option>
                      <option value="rejected">Reject</option>
                    </select>
                    <select value={item.priority} onChange={(event) => onChange(item.originalIndex, { priority: event.target.value })}>
                      <option value="high">High</option>
                      <option value="medium">Medium</option>
                      <option value="low">Low</option>
                    </select>
                  </div>
                  <details className="test-scope-edit-details">
                    <summary>Edit details</summary>
                    <label>
                      Input / action
                      <textarea value={item.input} onChange={(event) => onChange(item.originalIndex, { input: event.target.value })} />
                    </label>
                    <label>
                      Expected result
                      <textarea value={item.expected} onChange={(event) => onChange(item.originalIndex, { expected: event.target.value })} />
                    </label>
                    <label>
                      Analyst rationale
                      <input type="text" value={item.rationale} onChange={(event) => onChange(item.originalIndex, { rationale: event.target.value })} />
                    </label>
                    <small>{item.type} / {item.evidence || "no evidence"}</small>
                  </details>
                </article>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function countManagedCases(cases) {
  return cases.reduce(
    (counts, item) => {
      counts[item.status] = (counts[item.status] || 0) + 1;
      if (item.priority === "high") counts.high += 1;
      return counts;
    },
    { accepted: 0, backlog: 0, rejected: 0, high: 0 }
  );
}

function TimelineReport({ result }) {
  const breakdown = result.breakdown || {};
  return (
    <>
      <section className="answer-panel timeline">{result.estimated_timeline || "(no estimate)"}</section>
      <div className="stat-grid">
        <Stat label="Development" value={`${breakdown.development || "?"} day(s)`} />
        <Stat label="Unit testing" value={`${breakdown.unit_testing || "?"} day(s)`} />
        <Stat label="QA regression" value={`${breakdown.qa_regression || "?"} day(s)`} />
        <Stat label="Review / UAT" value={`${breakdown.review_uat || "?"} day(s)`} />
        <Stat label="Risk buffer" value={`${breakdown.risk_buffer || "?"} day(s)`} />
        <Stat label="Confidence" value={<Tag kind="confidence" value={result.confidence} />} />
      </div>
      <SimpleList title="Basis" items={result.basis || []} />
      <SimpleList title="Assumptions" items={result.assumptions || []} />
    </>
  );
}

function HistoryScreen({ items, onBack, onOpen }) {
  return (
    <section className="screen">
      <HeaderBlock eyebrow="Audit trail" title="Analysis history" subtitle="Every skill run is persisted as a reviewable artifact." />
      <ul className="history-list">
        {items.length === 0 && <li>No analyses run yet.</li>}
        {items.map((item) => (
          <li key={item.task_id} className="history-row">
            <button type="button" className="history-row-main" onClick={() => onOpen(item.task_id)}>
              <strong>{item.skill}</strong>
              <span>{item.input_preview || "(no input recorded)"}</span>
              <small>
                {item.profile} · {formatDate(item.created_at)} · {item.reviewed ? "Reviewed" : "Unreviewed"}
              </small>
            </button>
            {(item.jira_url || item.bitbucket_url) && (
              <div className="history-row-links">
                {item.jira_url && <ExternalLinkIcon href={item.jira_url} label="Open Jira issue" kind="jira" />}
                {item.bitbucket_url && <ExternalLinkIcon href={item.bitbucket_url} label="Open Bitbucket PR comment" kind="bitbucket" />}
              </div>
            )}
          </li>
        ))}
      </ul>
      <button className="btn ghost" type="button" onClick={onBack}>
        Back
      </button>
    </section>
  );
}

function ExternalLinkIcon({ href, label, kind }) {
  return (
    <a
      className={`external-icon ${kind}`}
      href={href}
      target="_blank"
      rel="noreferrer"
      title={label}
      aria-label={label}
      onClick={(event) => event.stopPropagation()}
    >
      {kind === "jira" ? "J" : "B"}
    </a>
  );
}

function HeaderBlock({ eyebrow, title, subtitle }) {
  return (
    <div className="header-block">
      <div className="eyebrow">{eyebrow}</div>
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </div>
  );
}

function ScreenBackBar({ onBack, label = "Back" }) {
  if (!onBack) return null;
  return (
    <div className="screen-back-bar">
      <button className="btn ghost compact" type="button" onClick={onBack}>
        <span aria-hidden="true">←</span>
        {label}
      </button>
    </div>
  );
}

// Groups by the item's existing `type` tag into a handful of named buckets
// (Business Rule / Ambiguity / Missing Information / Concern / Risk /
// Evidence / Memory) so the panel can show collapsed counts instead of every
// card flat -- same disclosure pattern already used lower on this page
// (Clarification & rules / Scope+concerns+risks / Evidence & memory), just
// applied one level higher where it matters most (this panel was ~70% of
// page height with 10 cards shown at once).
function groupTraceItems(items) {
  const groups = new Map();
  for (const item of items) {
    const type = item.type || "";
    let key = "other";
    let label = "Other";
    if (type === "Business rule") {
      key = "business_rule";
      label = "Business Rule";
    } else if (type === "Ambiguity") {
      key = "ambiguity";
      label = "Ambiguity";
    } else if (type === "Missing information") {
      key = "missing_information";
      label = "Missing Information";
    } else if (type === "Source evidence") {
      key = "evidence";
      label = "Evidence";
    } else if (type.startsWith("Past change")) {
      key = "memory";
      label = "Memory";
    } else if (type.endsWith("concern")) {
      key = "concern";
      label = "Concern";
    } else if (/^P\d/.test(type) || type === "Risk") {
      key = "risk";
      label = "Risk";
    } else if (type === "Affected module") {
      key = "affected_module";
      label = "Affected Module";
    } else if (type === "Risk note") {
      key = "risk_note";
      label = "Risk Note";
    } else if (type === "Evidence gap") {
      key = "evidence_gap";
      label = "Evidence Gap";
    }
    if (!groups.has(key)) {
      groups.set(key, { key, label, items: [] });
    }
    groups.get(key).items.push(item);
  }
  return Array.from(groups.values());
}

function EvidenceTraceabilityPanel({ title, subtitle, items }) {
  if (!items.length) return null;
  const groups = groupTraceItems(items);
  return (
    <section className="evidence-trace-panel">
      <div className="evidence-trace-head">
        <div>
          <span className="source-pill">Traceability</span>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        <strong>{items.length} linked item{items.length === 1 ? "" : "s"}</strong>
      </div>
      <div className="evidence-trace-groups">
        {groups.map((group) => (
          <details key={group.key} className="evidence-trace-group">
            <summary>
              <span>{group.label}</span>
              <small>{group.items.length}</small>
            </summary>
            <div className="evidence-trace-grid">
              {group.items.map((item, index) => (
                <article key={`${item.source}-${item.type}-${index}`} className={`evidence-trace-card ${item.tone || ""}`}>
                  <div className="evidence-trace-card-top">
                    <span className="source-pill">{item.source}</span>
                    <span className="concern-category">{item.type}</span>
                  </div>
                  <strong>{item.finding}</strong>
                  {item.evidence && <p>{item.evidence}</p>}
                  {item.action && <small>{item.action}</small>}
                </article>
              ))}
            </div>
          </details>
        ))}
      </div>
    </section>
  );
}

function buildRequirementTraceability(result) {
  const items = [];
  const seen = new Set();
  const add = (item) => addTraceItem(items, seen, item);

  (result.business_rules || []).slice(0, 3).forEach((rule) => add({
    source: "Ticket text",
    type: "Business rule",
    finding: rule,
    evidence: "Extracted from the submitted requirement or imported ticket fields.",
  }));
  (result.ambiguities || []).slice(0, 3).forEach((item) => add({
    source: item.evidence || "Requirement text",
    type: "Ambiguity",
    finding: item.note,
    action: "Confirm wording before impact analysis.",
    tone: "warn",
  }));
  (result.missing_information || []).slice(0, 3).forEach((question) => add({
    source: "AI triage",
    type: "Missing information",
    finding: question,
    action: "Ask stakeholder or product owner.",
    tone: "danger",
  }));
  (result.analyst_concerns || []).slice(0, 4).forEach((concern) => add({
    source: concern.evidence || "Ticket fields",
    type: `${formatScopeClue(concern.category || "concern")} concern`,
    finding: concern.note || concern.question,
    action: concern.question || "Confirm before approval.",
    tone: concern.severity === "high" ? "danger" : concern.severity === "medium" ? "warn" : "",
  }));
  (result.project_risks || []).slice(0, 4).forEach((risk) => add({
    source: risk.area || "Project risk",
    type: risk.priority || "Risk",
    finding: risk.reason,
    evidence: risk.mitigation ? `Mitigation: ${risk.mitigation}` : "",
    action: risk.owner ? `Owner: ${risk.owner}` : "Assign owner before handoff.",
    tone: risk.severity === "high" ? "danger" : risk.severity === "medium" ? "warn" : "",
  }));
  (result.evidence || []).slice(0, 3).forEach((item) => add({
    source: item.source || "Evidence",
    type: "Source evidence",
    finding: item.claim,
  }));
  (result.similar_past_changes || []).slice(0, 2).forEach((item) => add({
    source: "Memory",
    type: `Past change ${item.score ? `score ${item.score}` : ""}`.trim(),
    finding: item.summary,
    evidence: item.reviewed_at ? `Reviewed ${formatDate(item.reviewed_at)}` : "",
  }));

  return items.slice(0, 10);
}

function buildImpactTraceability(result) {
  const items = [];
  const seen = new Set();
  const add = (item) => addTraceItem(items, seen, item);

  (result.affected_modules || []).slice(0, 6).forEach((module) => add({
    source: module.path || module.name || "Codebase",
    type: "Affected module",
    finding: module.reason || module.name,
    evidence: module.evidence || "Matched from project context.",
  }));
  (result.risk_notes || []).slice(0, 4).forEach((risk) => add({
    source: risk.evidence || "Impact analysis",
    type: "Risk note",
    finding: risk.note,
    tone: "warn",
  }));
  (result.missing_evidence || []).slice(0, 4).forEach((missing) => add({
    source: "Missing evidence",
    type: "Evidence gap",
    finding: missing,
    action: "Check with developer, code owner, or project document.",
    tone: "danger",
  }));
  (result.evidence || []).slice(0, 4).forEach((item) => add({
    source: item.source || "Evidence",
    type: "Source evidence",
    finding: item.claim,
  }));
  (result.similar_past_changes || []).slice(0, 2).forEach((item) => add({
    source: "Memory",
    type: `Past change ${item.score ? `score ${item.score}` : ""}`.trim(),
    finding: item.summary,
    evidence: item.reviewed_at ? `Reviewed ${formatDate(item.reviewed_at)}` : "",
  }));

  return items.slice(0, 10);
}

function addTraceItem(items, seen, item) {
  const finding = String(item.finding || "").trim();
  if (!finding) return;
  const source = String(item.source || "Unknown source").trim();
  const type = String(item.type || "Finding").trim();
  const key = `${source}|${type}|${finding}`.toLowerCase();
  if (seen.has(key)) return;
  seen.add(key);
  items.push({
    source,
    type,
    finding,
    evidence: String(item.evidence || "").trim(),
    action: String(item.action || "").trim(),
    tone: item.tone || "",
  });
}

function EvidenceList({ title, items, sourceKey, claimKey }) {
  if (!items.length) return null;
  return (
    <section className="list-section">
      <h3>{title}</h3>
      <ul className="evidence-list">
        {items.map((item, index) => (
          <li key={index}>
            <code>{item[sourceKey]}</code>
            <span>{item[claimKey]}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function SimpleList({ title, items, tone }) {
  if (!items.length) return null;
  return (
    <section className={`list-section ${tone || ""}`}>
      <h3>{title}</h3>
      <ul className="simple-list">
        {items.map((item, index) => (
          <li key={index}>{item}</li>
        ))}
      </ul>
    </section>
  );
}

function ScopeClues({ items }) {
  if (!items.length) return null;
  return (
    <section className="list-section scope-clues-section">
      <h3>Scope clues</h3>
      <div className="scope-clue-row">
        {items.map((item) => (
          <span key={item} className="scope-clue">
            {formatScopeClue(item)}
          </span>
        ))}
      </div>
    </section>
  );
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Tag({ kind, value }) {
  const normalized = value || "unknown";
  const tone =
    kind === "risk"
      ? normalized === "elevated"
        ? "bad"
        : normalized === "medium"
          ? "warn"
          : "good"
      : normalized === "high"
        ? "good"
        : normalized === "medium"
          ? "warn"
          : "bad";
  return <span className={`tag ${tone}`}>{normalized}</span>;
}

function normalizeRequirementResponse(response) {
  if (!response || !response.artifact) {
    return response;
  }
  return { ...response.artifact, analysis_status: response.status };
}

function getRequirementStatus(artifact) {
  if (artifact.analysis_status) {
    return artifact.analysis_status;
  }
  const missing = artifact.result?.missing_information || [];
  return missing.length === 0 ? "READY_FOR_REVIEW" : "NEEDS_CLARIFICATION";
}

function formatStatus(status) {
  return String(status || "UNKNOWN").replaceAll("_", " ");
}

function statusClass(status) {
  return status === "READY_FOR_REVIEW" ? "reviewed" : "unreviewed";
}

function buildManagedCases(cases) {
  return (cases || []).map((item, index) => ({
    id: item.id || `TC-${index + 1}`,
    type: item.type || "manual",
    input: item.input || "",
    expected: item.expected || "",
    rationale: item.rationale || "",
    evidence: item.evidence || "",
    status: "accepted",
    priority: item.type === "negative" ? "high" : "medium",
  }));
}

const SCOPE_CLUE_STOPWORDS = new Set([
  "ticket",
  "key",
  "title",
  "priority",
  "reporter",
  "description",
  "acceptance",
  "criteria",
  "comments",
  "notes",
  "given",
  "when",
  "then",
  "page",
  "matching",
  "mbc",
  "fyp",
  "supervisor",
  "stakeholder",
  "high",
  "medium",
  "low",
  "critical",
  "only",
  "shows",
  "show",
  "use",
  "first",
  "future",
  "enhancement",
  "required",
  "now",
  "available",
  "records",
  "responding",
  "help",
  "browsing",
  "treated",
  "confirms",
  "confirm",
  "whether",
  "dry",
  "run",
  "import",
]);

function cleanScopeClues(items) {
  const seen = new Set();
  return items
    .map((item) => String(item || "").trim())
    .filter(Boolean)
    .filter((item) => {
      const normalized = item.toLowerCase().replaceAll("_", " ").replaceAll("-", " ");
      if (SCOPE_CLUE_STOPWORDS.has(normalized)) {
        return false;
      }
      if (/^\d+$/.test(normalized)) {
        return false;
      }
      if (seen.has(normalized)) {
        return false;
      }
      seen.add(normalized);
      return true;
    })
    .slice(0, 10);
}

function formatScopeClue(item) {
  return String(item).replaceAll("_", " ").replaceAll("-", " ");
}

function formatTicketInput(ticket) {
  const lines = [];
  appendTicketLine(lines, "Ticket key", ticket.ticketKey);
  appendTicketLine(lines, "Title", ticket.ticketTitle);
  appendTicketLine(lines, "Priority", ticket.priority);
  appendTicketLine(lines, "Reporter", ticket.reporter);
  appendTicketLine(lines, "Source type", ticket.sourceType);
  appendTicketLine(lines, "Source name", ticket.sourceName);
  appendTicketLine(lines, "Source URL", ticket.sourceUrl);
  appendTicketLine(lines, "Received", ticket.receivedAt);
  appendTicketBlock(lines, "Description", ticket.description);
  appendTicketBlock(lines, "Acceptance criteria", ticket.acceptanceCriteria);
  appendTicketBlock(lines, "Comments / notes", ticket.comments);
  return lines.join("\n").trim();
}

function ticketFromImportResponse(response) {
  return {
    sourceType: response.source_type || "Jira",
    sourceName: response.source_name || "Jira import",
    sourceUrl: response.source_url || "",
    receivedAt: response.received_at || "Imported from Jira",
    ticketKey: response.ticket_key || "",
    ticketTitle: response.ticket_title || "",
    priority: response.priority || "Medium",
    reporter: response.reporter || "",
    description: response.description || "",
    acceptanceCriteria: response.acceptance_criteria || "",
    comments: response.comments || "",
  };
}

function appendTicketLine(lines, label, value) {
  if (value && value.trim()) {
    lines.push(`${label}: ${value.trim()}`);
  }
}

function appendTicketBlock(lines, label, value) {
  if (value && value.trim()) {
    if (lines.length > 0) {
      lines.push("");
    }
    lines.push(`${label}:`);
    lines.push(value.trim());
  }
}

function ErrorBox({ message }) {
  return <div className="error-box">{message}</div>;
}

async function checkBackend(setBackendStatus) {
  try {
    const response = await fetch(`${API_BASE}/api/health`);
    if (!response.ok) throw new Error(String(response.status));
    setBackendStatus("up");
  } catch {
    setBackendStatus("down");
  }
}

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method || "GET",
    headers: options.body ? { "Content-Type": "application/json" } : undefined,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`HTTP ${response.status}: ${text || response.statusText}`);
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  return JSON.parse(text);
}

function formatDate(value) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function truncateText(value, maxChars) {
  const text = String(value || "").trim();
  return text.length > maxChars ? `${text.slice(0, maxChars).trim()}...` : text;
}

function formatRelativeTime(value) {
  try {
    const then = new Date(value).getTime();
    const diffSeconds = Math.round((Date.now() - then) / 1000);
    if (diffSeconds < 60) return "just now";
    const diffMinutes = Math.round(diffSeconds / 60);
    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    const diffHours = Math.round(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.round(diffHours / 24);
    if (diffDays < 30) return `${diffDays}d ago`;
    return new Date(value).toLocaleDateString();
  } catch {
    return formatDate(value);
  }
}

function buildProjectProcessColumns(projects) {
  const columns = [
    { id: "active", title: "Active", projects: [] },
    { id: "indexing", title: "Indexing", projects: [] },
    { id: "ready", title: "Ready", projects: [] },
    { id: "attention", title: "Needs action", projects: [] },
  ];
  const byId = Object.fromEntries(columns.map((column) => [column.id, column]));
  projects.forEach((project) => {
    byId[projectProcessStage(project)].projects.push(project);
  });
  return columns;
}

function projectProcessMetrics(projects) {
  return projects.reduce(
    (metrics, project) => {
      metrics[projectProcessStage(project)] += 1;
      return metrics;
    },
    { active: 0, indexing: 0, ready: 0, attention: 0 }
  );
}

function projectProcessStage(project) {
  if (project.active) return "active";
  if (project.index_status === "indexing" || project.graphify_index_status === "indexing") return "indexing";
  if (project.index_status === "ready" && project.graphify_index_status === "ready") return "ready";
  return "attention";
}

function projectProcessHint(project) {
  if (project.active) return "Currently used for Repo AI, impact analysis, and project overview.";
  if (project.index_status === "indexing" || project.graphify_index_status === "indexing") {
    return "Background indexing is still running. Switch after the graphs are ready.";
  }
  if (project.index_status === "ready" && project.graphify_index_status === "ready") {
    return "Ready to switch into analysis with project graph and diagram support.";
  }
  if (project.index_status === "failed" || project.graphify_index_status === "failed") {
    return "Indexing needs attention before diagram or repo-grounded analysis is reliable.";
  }
  return "Connect or re-index before using this project for analysis.";
}

function indexStatusLabel(status, error) {
  if (status === "ready") return "Indexed";
  if (status === "indexing") return "Indexing…";
  if (status === "failed") return `Index failed${error ? `: ${error}` : ""}`;
  return "Not indexed";
}

createRoot(document.getElementById("root")).render(<App />);

