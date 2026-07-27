# Frontend

React/Vite implementation of the Software Analyst Workflow Assistant.

## What It Does

- Shows one guided Software Analyst workflow on first load.
- Runs requirement analysis from a requirement or ticket description.
- Shows `NEEDS_CLARIFICATION` / `READY_FOR_REVIEW` status.
- Lets the analyst add clarification and rerun requirement analysis as a linked artifact.
- Blocks review while requirement analysis still needs clarification.
- Hands reviewed requirement artifacts to impact analysis.
- Hands reviewed impact modules to test scenario generation.
- Generates and reviews a persisted handoff summary artifact.

## Run Locally

Start the backend first:

```powershell
cd ..\backend
mvn spring-boot:run
```

Then start the frontend:

```powershell
cd ..\frontend
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

The frontend calls the backend at `http://localhost:8080` by default. Override it with:

```powershell
$env:VITE_API_BASE="http://localhost:8080"
npm run dev
```

## Build

```powershell
npm run build
```

The production build is emitted to `dist/`, which is intentionally ignored by git.
