# AGENTS.md

Juice is a nearly empty repository. It currently holds only the Formic agent runner setup; there is no application code, build, lint or test setup yet.

## Where things are

- `README.md` — project title only.
- `.github/workflows/formic-agent.yml` — the "Formic agent" GitHub Actions workflow (`workflow_dispatch`) that runs a coding agent for a Formic ticket and pushes to a `formic-staging/` branch.
- `.github/formic/loop-entry.mjs` — the bundled, generated loop entry the workflow runs. Do not edit it by hand; Formic replaces both files together when its version changes.

## Conventions

- Do not commit, push or create branches; Formic does that.
- Add checks (typecheck, lint, tests) only when a ticket asks for them.
