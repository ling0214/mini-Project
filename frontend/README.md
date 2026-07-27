# Frontend

React/Vite implementation of the Hermes Analyst Workbench.

The React app is now the primary frontend. The old vanilla prototype remains at
`prototype/code-qa.html` as a reference and fallback, but active UI work should
happen in `src/`.

## What It Does

- Select an operating role: Project Analyst, Business Analyst, or Software Tester.
- Run role-gated skills against the backend:
  - Code Q&A
  - Impact Analysis
  - GitHub PR impact import
  - Test Case Generation
- Render persisted artifact reports with evidence, confidence, risk, and raw JSON.
- Enforce review-before-handoff flow.
- After review, hand off to:
  - Jira issue creation
  - Bitbucket PR comment
  - Timeline estimation
  - Tester test-plan generation
- Open persisted analysis history.

## Run Locally

Start the backend first:

```powershell
cd ..\backend
mvn spring-boot:run
```

Then start the React frontend:

```powershell
cd ..\frontend
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

The frontend calls the backend at:

```text
http://localhost:8080
```

Override it with:

```powershell
$env:VITE_API_BASE="http://localhost:8080"
npm run dev
```

## Build

```powershell
npm run build
```

The production build is emitted to `dist/`, which is intentionally ignored by
git.

## Notes

- `node_modules/` and `dist/` are ignored.
- `package-lock.json` should be committed so dependency versions stay stable.
- The prototype file is still useful for comparison, but React is the path for
  the commercial UI.
