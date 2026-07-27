import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

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
  "business-analyst": ["code-qa", "impact-analysis"],
  tester: ["code-qa", "test-case-gen"],
};

const SKILL_META = {
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
};

const CHIPS = {
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

function App() {
  const [role, setRole] = useState(null);
  const [step, setStep] = useState("role");
  const [skill, setSkill] = useState("code-qa");
  const [backendStatus, setBackendStatus] = useState("checking");
  const [artifact, setArtifact] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [handoffs, setHandoffs] = useState([]);

  useEffect(() => {
    checkBackend(setBackendStatus);
  }, []);

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
    <div className="app-shell">
      <TopBar status={backendStatus} onHistory={loadHistory} />
      <WorkflowRail step={step} />

      <main className="workspace">
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

      <footer className="app-footer">
        API: <code>{API_BASE}</code> · Artifacts, review gate, Jira issue creation, and Bitbucket PR comments are backed by the Spring Boot service.
      </footer>
    </div>
  );
}

function TopBar({ status, onHistory }) {
  return (
    <header className="topbar">
      <div className="brand-mark">H</div>
      <div>
        <div className="brand-name">Hermes Analyst Workbench</div>
        <div className="brand-subtitle">AI software analyst delivery console</div>
      </div>
      <div className="topbar-spacer" />
      <button className="btn ghost compact" type="button" onClick={onHistory}>
        History
      </button>
      <span className={`connection ${status}`}>
        <span />
        {status === "up" ? "Backend up" : status === "down" ? "Backend down" : "Checking backend"}
      </span>
    </header>
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
      {skill === "code-qa" && <CodeQaForm role={role} onArtifact={onArtifact} />}
      {skill === "impact-analysis" && <ImpactForm role={role} onArtifact={onArtifact} />}
      {skill === "test-case-gen" && <TestGenForm role={role} onArtifact={onArtifact} />}
    </section>
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
          <span className={`status-pill ${reviewed ? "reviewed" : "unreviewed"}`}>{reviewed ? "Reviewed" : "Unreviewed"}</span>
          <button className="btn primary" type="button" disabled={reviewed} onClick={onReviewed}>
            {reviewed ? "Reviewed" : "Mark as reviewed"}
          </button>
        </div>
      </div>

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
      <div className="stat-grid">
        <Stat label="Risk level" value={<Tag kind="risk" value={result.risk_level} />} />
        <Stat label="Rough effort" value={`${result.rough_effort?.estimate || "?"}${result.rough_effort?.basis ? ` - ${result.rough_effort.basis}` : ""}`} />
        <Stat label="Confidence" value={<Tag kind="confidence" value={result.confidence} />} />
      </div>
      {reviewed && (
        <div className="handoff-grid">
          <TimelineHandoff artifact={artifact} onArtifact={onArtifact} />
          <ExternalHandoff artifact={artifact} handoffs={handoffs} onReload={onReloadHandoffs} />
        </div>
      )}
      <ModuleList artifact={artifact} modules={result.affected_modules || []} reviewed={reviewed} onArtifact={onArtifact} />
      <EvidenceList title="Related historical issues" items={result.risk_notes || []} sourceKey="evidence" claimKey="note" />
      {(result.missing_evidence || []).length > 0 && <SimpleList title="Missing evidence" items={result.missing_evidence} tone="danger" />}
    </>
  );
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

function ExternalHandoff({ artifact, handoffs, onReload }) {
  const [summary, setSummary] = useState("Reviewed impact analysis");
  const [prUrl, setPrUrl] = useState("");
  const [dryRun, setDryRun] = useState(true);
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");

  async function send(destination) {
    setError("");
    setLoading(destination);
    try {
      await api(`/api/artifacts/${artifact.task_id}/external-handoff`, {
        method: "POST",
        body: { destination, summary, pr_url: prUrl, dry_run: dryRun },
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
      <label className="field-label">Summary</label>
      <input type="text" value={summary} onChange={(event) => setSummary(event.target.value)} />
      <label className="field-label">Bitbucket PR URL</label>
      <input type="text" value={prUrl} onChange={(event) => setPrUrl(event.target.value)} placeholder="https://bitbucket.org/workspace/repo/pull-requests/123" />
      <label className="check">
        <input type="checkbox" checked={dryRun} onChange={(event) => setDryRun(event.target.checked)} />
        Dry-run only
      </label>
      {error && <ErrorBox message={error} />}
      <div className="action-row">
        <button className="btn primary" type="button" disabled={Boolean(loading)} onClick={() => send("jira")}>
          {loading === "jira" ? "Creating..." : "Create Jira Issue"}
        </button>
        <button className="btn ghost" type="button" disabled={Boolean(loading)} onClick={() => send("bitbucket")}>
          {loading === "bitbucket" ? "Posting..." : "Comment Bitbucket PR"}
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
          <li key={item.task_id}>
            <button type="button" onClick={() => onOpen(item.task_id)}>
              <strong>{item.skill}</strong>
              <span>{item.input_preview || "(no input recorded)"}</span>
              <small>
                {item.profile} · {formatDate(item.created_at)} · {item.reviewed ? "Reviewed" : "Unreviewed"}
              </small>
            </button>
          </li>
        ))}
      </ul>
      <button className="btn ghost" type="button" onClick={onBack}>
        Back
      </button>
    </section>
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
  return response.json();
}

function formatDate(value) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

createRoot(document.getElementById("root")).render(<App />);
