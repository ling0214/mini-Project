import React, { useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

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
  ticketKey: "",
  ticketTitle: "",
  priority: "Medium",
  reporter: "",
  description: "",
  acceptanceCriteria: "",
  comments: "",
};

const SAMPLE_TICKET = {
  ticketKey: "MBC-204",
  ticketTitle: "Allow donors to filter available aid requests by city and urgency",
  priority: "High",
  reporter: "FYP Supervisor",
  description:
    "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.",
  acceptanceCriteria:
    "Given a donor is browsing available aid requests, when the donor selects city, category, or urgency filters, then the page only shows matching approved aid requests.",
  comments:
    "Need to confirm whether filters should update through page reload or AJAX, and whether the same filter should apply to admin monitoring later.",
};

const TARGET_PROJECT = {
  name: "MyBanjirCare",
  framework: "Laravel 10 / PHP 8.1",
  source: "github.com/ling0214/MyBanjirCare",
  modules: ["Aid Request", "Donation", "Flood Report", "Collection Center", "Auth / OTP"],
};

function App() {
  const [backendStatus, setBackendStatus] = useState("checking");

  useEffect(() => {
    checkBackend(setBackendStatus);
  }, []);

  return (
    <div className="app-shell">
      <TopBar status={backendStatus} />
      <AnalystWorkflow />
      <footer className="app-footer">
        API: <code>{API_BASE}</code> · Artifacts, review gate, Jira issue creation, and Bitbucket PR comments are backed by the Spring Boot service.
      </footer>
    </div>
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

function TopBar({ status }) {
  return (
    <header className="topbar">
      <div className="brand-mark">H</div>
      <div>
        <div className="brand-name">Analyst Workbench</div>
        <div className="brand-subtitle">Software Analyst workflow assistant</div>
      </div>
      <div className="topbar-spacer" />
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

/**
 * The Software Analyst Workflow Assistant: one continuous requirement -&gt;
 * impact -&gt; test -&gt; report pipeline instead of a role picker. A single
 * fixed profile (business-analyst, already permitted for requirement-analysis
 * and impact-analysis) drives every call — the point of this mode is the
 * analyst's workflow, not which role is "allowed" to click what.
 */
const WORKFLOW_STEPS = [
  ["requirement", "Ticket Intake"],
  ["impact", "Impact Analysis"],
  ["test", "Test Scenarios"],
  ["report", "Handoff Summary"],
];

function AnalystWorkflow() {
  const [phase, setPhase] = useState("requirement");
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

  function reset() {
    setPhase("requirement");
    setTicket(EMPTY_TICKET);
    setReqArtifact(null);
    setImpactArtifact(null);
    setImpactError("");
    setTestArtifacts([]);
    setTestScopeArtifacts({});
    setSummaryArtifact(null);
    setSummaryHandoffs([]);
    startedImpactRef.current = false;
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
        impactReviewed={Boolean(impactArtifact?.reviewed)}
        testCount={testArtifacts.length}
        onReset={reset}
      />
      <main className="workspace">
        {phase === "requirement" && (
          <RequirementPhase
            ticket={ticket}
            onTicketChange={setTicket}
            reqArtifact={reqArtifact}
            reqStatus={reqStatus}
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
          />
        )}
      </main>
    </>
  );
}

function AnalystWorkflowRail({ phase, reqStatus, impactReviewed, testCount, onReset }) {
  const order = WORKFLOW_STEPS.map(([id]) => id);
  const currentIndex = order.indexOf(phase);
  const subtitle = {
    requirement: reqStatus ? formatStatus(reqStatus) : "Not started",
    impact: impactReviewed ? "Reviewed" : currentIndex >= order.indexOf("impact") ? "In review" : "Pending",
    test: testCount > 0 ? `${testCount} scenario set${testCount === 1 ? "" : "s"}` : "Pending",
    report: phase === "report" ? "Viewing" : "Pending",
  };
  return (
    <aside className="workflow-rail">
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
        One continuous ticket intake to impact to test to report pipeline. Clarification and review gates run inline before you can move to the next step.
      </div>
      <button className="btn ghost compact rail-reset" type="button" onClick={onReset}>
        Start new analysis
      </button>
    </aside>
  );
}

function RequirementPhase({ ticket, onTicketChange, reqArtifact, reqStatus, onArtifact, onReview }) {
  const reviewed = Boolean(reqArtifact?.reviewed);
  const reviewBlocked = reqStatus === "NEEDS_CLARIFICATION";
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 1 - Ticket Intake"
        title="Capture the change request ticket"
        subtitle="Enter the ticket details an analyst normally receives before clarification, impact analysis, testing scope, and handoff."
      />
      {!reqArtifact && <ProjectContextCard />}
      {!reqArtifact && <TicketIntakeForm ticket={ticket} onChange={onTicketChange} onArtifact={onArtifact} />}
      {reqArtifact && (
        <>
          <RequirementAnalysisReport artifact={reqArtifact} result={reqArtifact.result || {}} onArtifact={onArtifact} />
          <div className="action-row">
            <span className={`status-pill ${reviewed ? "reviewed" : "unreviewed"}`}>{reviewed ? "Reviewed" : "Unreviewed"}</span>
            <button className="btn primary" type="button" disabled={reviewed || reviewBlocked} onClick={onReview}>
              {reviewed ? "Reviewed - continuing" : "Mark as reviewed and continue"}
            </button>
          </div>
        </>
      )}
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
  const [importKey, setImportKey] = useState("MBC-204");
  const [importLoading, setImportLoading] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [error, setError] = useState("");
  const ticketText = formatTicketInput(ticket);

  function update(field, value) {
    onChange({ ...ticket, [field]: value });
  }

  async function importFromJira() {
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
      onChange({
        ticketKey: response.ticket_key || "",
        ticketTitle: response.ticket_title || "",
        priority: response.priority || "Medium",
        reporter: response.reporter || "",
        description: response.description || "",
        acceptanceCriteria: response.acceptance_criteria || "",
        comments: response.comments || "",
      });
      setImportMessage(response.message || "Jira ticket imported.");
    } catch (err) {
      setError(err.message);
    } finally {
      setImportLoading(false);
    }
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
          description: ticket.description,
          acceptance_criteria: ticket.acceptanceCriteria,
          comments: ticket.comments,
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
    <section className="work-panel ticket-intake-panel">
      <section className="jira-import-panel">
        <div>
          <label className="field-label">Import from Jira</label>
          <p>Dry-run import. The analyst reviews the ticket before running AI analysis.</p>
        </div>
        <div className="jira-import-controls">
          <input
            type="text"
            value={importKey}
            onChange={(event) => setImportKey(event.target.value)}
            placeholder="MBC-204 or Jira ticket URL"
          />
          <button className="btn ghost" type="button" disabled={importLoading} onClick={importFromJira}>
            {importLoading ? "Importing..." : "Import ticket"}
          </button>
        </div>
        {importMessage && <div className="info-box">{importMessage}</div>}
      </section>
      <div className="ticket-form-actions">
        <label className="field-label">Ticket details</label>
        <button className="btn ghost compact" type="button" onClick={() => onChange(SAMPLE_TICKET)}>
          Load sample ticket
        </button>
      </div>
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
        Comments / clarification history
        <textarea
          className="compact-textarea"
          value={ticket.comments}
          onChange={(event) => update("comments", event.target.value)}
          placeholder="Stakeholder comments, email notes, or meeting follow-up"
        />
      </label>
      <details className="ticket-preview">
        <summary>Preview analysis input</summary>
        <pre>{ticketText || "(ticket details will appear here)"}</pre>
      </details>
      {error && <ErrorBox message={error} />}
      <div className="action-row">
        <button className="btn primary" type="button" disabled={loading} onClick={submit}>
          {loading ? "Analysing..." : "Analyze Ticket"}
        </button>
      </div>
    </section>
  );
}

function ImpactPhase({ loading, error, artifact, onRetry, onReview, onBack, onNext }) {
  const result = artifact?.result || {};
  const reviewed = Boolean(artifact?.reviewed);
  return (
    <section className="screen">
      <HeaderBlock
        eyebrow="Step 2 · Impact Analysis"
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
          <div className="stat-grid">
            <Stat label="Risk level" value={<Tag kind="risk" value={result.risk_level} />} />
            <Stat
              label="Rough effort"
              value={`${result.rough_effort?.estimate || "?"}${result.rough_effort?.basis ? ` - ${result.rough_effort.basis}` : ""}`}
            />
            <Stat label="Confidence" value={<Tag kind="confidence" value={result.confidence} />} />
          </div>
          <EvidenceList title="Affected modules" items={result.affected_modules || []} sourceKey="path" claimKey="reason" />
          <EvidenceList title="Related historical issues" items={result.risk_notes || []} sourceKey="evidence" claimKey="note" />
          {(result.missing_evidence || []).length > 0 && <SimpleList title="Missing evidence" items={result.missing_evidence} tone="danger" />}
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
        eyebrow="Step 3 · Test Scenarios"
        title="Prepare the testing scope"
        subtitle="Generate scenarios from impacted modules, then accept, reject, edit, and prioritize cases before handoff."
      />
      {modules.length === 0 && <SimpleList title="Affected modules" items={["No affected modules resolved in the project graph."]} />}
      {modules.length > 0 && (
        <div className="skill-grid">
          {modules.map((item) => {
            const done = testArtifacts.some((entry) => entry.result?.target === item.name);
            return (
              <button
                key={item.name}
                type="button"
                className={`skill-tab ${done ? "active" : ""}`}
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
      )}
      {testArtifacts.map((item) => (
        <section key={item.task_id} className="list-section">
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
        eyebrow="Step 4 - Handoff Summary"
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

function RequirementAnalysisReport({ artifact, result, onArtifact }) {
  const status = getRequirementStatus(artifact);
  const ambiguities = result.ambiguities || [];
  const scopeClues = cleanScopeClues(result.potential_affected_areas || []);
  return (
    <>
      <div className="stat-grid">
        <Stat label="Workflow status" value={<span className={`tag ${statusClass(status)}`}>{formatStatus(status)}</span>} />
        <Stat label="Confidence" value={<Tag kind="confidence" value={result.confidence} />} />
        <Stat label="Scope clues" value={scopeClues.length} />
      </div>
      <SimpleList title="Business rules" items={result.business_rules || []} />
      <SimpleList title="Missing information" items={result.missing_information || []} tone={status === "NEEDS_CLARIFICATION" ? "danger" : undefined} />
      <SimpleList title="Assumptions" items={result.assumptions || []} />
      <ScopeClues items={scopeClues} />
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
      <EvidenceList title="Evidence" items={result.evidence || []} sourceKey="source" claimKey="claim" />
      {status === "NEEDS_CLARIFICATION" && <ClarificationPanel artifact={artifact} onArtifact={onArtifact} />}
    </>
  );
}

function ClarificationPanel({ artifact, onArtifact }) {
  const [additionalInfo, setAdditionalInfo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const profile = (artifact.agent || "").replace(/-agent$/, "") || ANALYST_PROFILE;
  return (
    <section className="handoff-panel clarification-panel">
      <h3>Clarification</h3>
      <p className="muted">Add the analyst answer or confirmed business rule, then rerun requirement analysis as a linked artifact.</p>
      <textarea
        value={additionalInfo}
        onChange={(event) => setAdditionalInfo(event.target.value)}
        placeholder="Example: Payment method can only be changed before policy approval."
      />
      {error && <ErrorBox message={error} />}
      <button
        className="btn primary"
        type="button"
        disabled={loading}
        onClick={async () => {
          setError("");
          if (!additionalInfo.trim()) {
            setError("Clarification text is required.");
            return;
          }
          setLoading(true);
          try {
            const response = await api(`/api/artifacts/${artifact.task_id}/clarify`, {
              method: "POST",
              body: { profile, additional_info: additionalInfo },
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

function ExternalHandoff({ artifact, handoffs, onReload, initialSummary = "Reviewed impact analysis" }) {
  const [summary, setSummary] = useState(initialSummary);
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

function TestScopeManager({ testArtifact, scopeArtifact, onSave, onReview }) {
  const generatedCases = testArtifact.result?.cases || [];
  const [cases, setCases] = useState(() => buildManagedCases(generatedCases));
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const result = scopeArtifact?.result || {};
  const reviewed = Boolean(scopeArtifact?.reviewed);

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
        <button className="btn primary compact" type="button" disabled={Boolean(loading)} onClick={saveScope}>
          {loading === "save" ? "Saving..." : scopeArtifact ? "Save new scope version" : "Save testing scope"}
        </button>
        <button className="btn ghost compact" type="button" disabled={!scopeArtifact || reviewed || Boolean(loading)} onClick={markReviewed}>
          {reviewed ? "Reviewed" : loading === "review" ? "Reviewing..." : "Mark scope reviewed"}
        </button>
      </div>
    </section>
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
  appendTicketBlock(lines, "Description", ticket.description);
  appendTicketBlock(lines, "Acceptance criteria", ticket.acceptanceCriteria);
  appendTicketBlock(lines, "Comments / notes", ticket.comments);
  return lines.join("\n").trim();
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

