# Figma Optimization Spec: Actual UI Alignment

Source UI: `frontend/prototype/code-qa.html`

Target Figma file: `https://www.figma.com/design/McO1t8S56tC2R54D6Nqpub`

## Goal

Update the Figma prototype so it reflects the actual running HTML UI, not only the earlier concept/dashboard design.

The Figma prototype should show the product as a centered analyst workbench:

```text
Role selection -> Analyze skill -> Reviewable report -> Reviewed-only handoff
```

## Visual Tokens

Use these values from the actual CSS:

| Token | Value | Usage |
|---|---|---|
| Ink | `#1c2b27` | Main text |
| Ink soft | `#435049` | Secondary text |
| Paper | `#eef0ea` | Page background |
| Paper raised | `#ffffff` | App shell/cards |
| Line | `#d7dbd0` | Borders |
| Line strong | `#b9c0b2` | Muted dots/inactive steps |
| Teal | `#2f6f62` | Primary actions, active state |
| Teal soft | `#e3ede9` | Active pills/tags |
| Amber | `#a3742c` | Warning/risk tags |
| Amber soft | `#f1e3c6` | Warning background |
| Rust | `#a5432e` | Error/unreviewed state |
| Rust soft | `#f3e0da` | Error/unreviewed background |

Typography:

| CSS source | Figma approximation |
|---|---|
| System sans: `-apple-system`, `Segoe UI`, Roboto, Arial | Inter |
| Serif H1: Georgia / Iowan / Palatino | Georgia if available, otherwise Inter Bold |
| Mono: Cascadia Code / SF Mono / Consolas | Roboto Mono or Inter Mono if available |

## Frame Set To Add

Create a new top-level Figma board:

```text
Actual UI Prototype / HTML-aligned
```

Recommended board size:

```text
3000 x 940
```

Inside it, create three main screens:

| Frame | Size | Purpose |
|---|---:|---|
| `01 Actual UI - Role Selection` | `920 x 680` | Initial role picker |
| `02 Actual UI - Impact Analysis` | `920 x 680` | Skill tab + impact form |
| `03 Actual UI - Reviewed Report + External Handoff` | `920 x 680` | Report, review gate, Jira/Bitbucket handoff |

Each screen should contain the actual app shell:

```text
Viewport background: #eef0ea
Centered app shell: 760px wide, white background, 12px radius, subtle shadow
Titlebar: mini-project - analyst workbench + History + backend status
Steps: 1 Role / 2 Analyze / 3 Report
Screen body: 24px horizontal padding
```

## Screen 1: Role Selection

Main content:

```text
H1: Who's asking?
Subcopy: The role you pick decides which skills you're allowed to call - enforced by CoordinatorService, not just a prompt.
```

Cards:

| Card | Eyebrow | Body |
|---|---|---|
| Project Analyst | Role - Project Analyst | Scope change requests, check known risk hotspots, prepare analysis for a client or PM. |
| Business Analyst | Role - Business Analyst | Check whether a change matches business intent, grounded in what the code actually does. |
| Software Tester | Role - Software Tester | Find regression scope, see what's broken here before, build a grounded test plan. |

Use the actual card style:

```text
White fill
1px #d7dbd0 border
10px radius
Hover hint can be represented with teal border on one selected/sample card
```

## Screen 2: Impact Analysis

Main content:

```text
Role pill: Project Analyst
H1: Choose an analysis
Subcopy: Each skill below is its own bounded action against the real project graph...
```

Skill tabs:

```text
Code Q&A
Impact Analysis - active teal-soft
Test Case Gen
```

Impact panel:

```text
Explainer:
impact-analysis - calls trace_impact per resolved entry point + search_issues, then applies a rule-based risk tag. No LLM.

Textarea placeholder:
Add automatic retry to charge_card when the payment gateway times out

Primary button:
Analyze Impact

Alternate GitHub PR input:
Analyze a GitHub PR instead (read-only)
https://github.com/owner/repo/pull/123
Analyze PR
```

## Screen 3: Reviewed Report + External Handoff

Status:

```text
Reviewed pill
Reviewed button disabled/stateful
```

Stats:

| Stat | Example |
|---|---|
| Risk level | medium |
| Rough effort | 2-3 days |
| Confidence | high |

External handoff block:

```text
Title: External handoff after review
Copy: Create a Jira ticket or comment on a Bitbucket PR only after this artifact is reviewed. Dry-run is on by default.

Input: Summary
Reviewed impact analysis for payment retry

Input: Bitbucket PR URL
https://bitbucket.org/workspace/repo/pull-requests/123

Checkbox: Dry-run only

Buttons:
Create Jira Ticket
Comment Bitbucket PR
```

Affected modules list:

```text
payments.py:1 - charge_card affected by retry behavior
app.py:5 - checkout_endpoint is the direct caller
```

## Prototype Links

Add these Figma prototype flows:

```text
Role card -> 02 Actual UI - Impact Analysis
Analyze Impact button -> 03 Actual UI - Reviewed Report + External Handoff
Analyze PR button -> 03 Actual UI - Reviewed Report + External Handoff
Create Jira Ticket button -> stay on same frame / show handoff history state
Comment Bitbucket PR button -> stay on same frame / show handoff history state
History button -> existing history frame if available
```

## Reviewer Message

Use this wording in Figma notes:

```text
This prototype is aligned with the actual HTML/JS UI in frontend/prototype/code-qa.html.
It shows the implemented workbench flow: role selection, bounded skill execution, evidence-backed report, human review gate, and reviewed-only Jira/Bitbucket handoff.
```
