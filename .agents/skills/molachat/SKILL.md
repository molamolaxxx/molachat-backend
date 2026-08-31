---
name: molachat
description: Inspect, diagnose, modify, and validate the MolaChat repository, a Java 8/Spring Boot chat application with WebSocket messaging, ACP/cmd-proxy integration, Fast Team distributed runtime, Thymeleaf, and vanilla JavaScript. Use when Codex needs to quickly understand this project, trace a frontend/backend/event-flow problem, investigate logs or runtime state, change MolaChat code or tests, review a proposed change, or run project-specific verification and packaging.
---

# Work on MolaChat

## Start with evidence

1. Resolve this skill's installed directory, then run `<skill-directory>/scripts/project-snapshot.sh`; the script itself works from any current directory. If the resource is unavailable, inspect the same inputs manually.
2. Read `git status --short` before editing. Treat all existing changes as user-owned and preserve them.
3. Classify the request as explanation/review, diagnosis, implementation, or deployment. Do not turn diagnosis into an unrequested fix or local development into a production mutation.
4. Trace the smallest relevant path before reading broad portions of the repository. Use `rg` and targeted tests.
5. State assumptions when runtime state, the companion cmd-proxy repository, or deployment files are unavailable.

## Load only relevant project knowledge

- Read [references/architecture.md](references/architecture.md) to locate components, request/event paths, configuration, and extension points.
- Read [references/troubleshooting.md](references/troubleshooting.md) for symptom-driven diagnosis and log/runtime checks.
- Read [references/fast-team.md](references/fast-team.md) before changing Team, ACP callbacks, cross-instance RPC, TalkTo, session rotation, or reconnect behavior.
- Read [references/validation.md](references/validation.md) before testing, packaging, frontend verification, or reporting completion.

## Diagnose systematically

1. Reproduce or define the failing boundary: browser, HTTP controller, WebSocket action, service/solution, persistence, cmd-proxy RPC, or callback projection.
2. Establish expected behavior from tests, nearby code, protocol DTOs, and existing project documents. Do not use old plans as proof that current code behaves that way.
3. Follow identifiers end to end. For ACP and Team flows, check `chatterId`, `sessionId`, `groupId`, `instanceId`, `teamId`, `teamMemberId`, `transportGroup`, and event ID where applicable.
4. Check state transitions, idempotency, ordering, lock boundaries, retry behavior, and cleanup paths for asynchronous failures.
5. Separate root cause from downstream symptoms. Cite concrete files, methods, logs, or test failures.
6. For runtime-only incidents, prefer read-only inspection first. Require explicit authorization before cleanup, restart, deployment, or production writes.

## Implement narrowly

1. Preserve Java 8 compatibility and the existing Spring Boot 2.1.6 style.
2. Follow the established controller → service/solution → data/event boundary; reuse existing DTOs and factories where appropriate.
3. Avoid redundant cached state when the authoritative value is already safely available on an existing object or DOM node.
4. Keep protocol changes compatible across MolaChat and cmd-proxy. If the companion repository is not in scope, identify the required counterpart change rather than inventing its behavior.
5. Add or update focused regression tests for the bug boundary and failure mode.
6. For UI work, preserve the requested interaction boundary and existing popup style unless the user asks for redesign. Verify desktop, narrow/short viewport, scrolled page, and variable member counts when relevant.

## Verify proportionally

1. Run the narrowest meaningful test first, then broaden according to risk.
2. Always override Maven's repository-level `skipTests=true` when claiming tests ran: use `-DskipTests=false`.
3. Run JavaScript syntax checks for changed scripts and `git diff --check` for touched content.
4. Package after backend or integration changes when practical.
5. Distinguish current command results from historical Surefire reports. Report test counts and any skipped validation explicitly.
6. Treat real cross-device/cross-instance E2E, browser layout verification, deployment sync, and production checks as separate evidence—not implied by unit tests.

## Report the result

Lead with the outcome. Summarize the root cause or implementation, list current validation evidence, name anything not verified, and link directly to the most important changed files. Do not claim deployment or production recovery unless it was actually performed and observed.
