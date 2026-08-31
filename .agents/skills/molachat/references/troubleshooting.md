# Troubleshooting playbook

## Establish a clean evidence baseline

Run `<skill-directory>/scripts/project-snapshot.sh` by resolving the path relative to `SKILL.md`, then capture:

- exact symptom, user action, timestamp, identity, and affected session/team;
- whether the issue is local, browser-specific, user-specific, instance-specific, or cross-instance;
- current branch and pre-existing worktree changes;
- current process/deployment only when access is in scope;
- fresh logs around the event, not only archived logs or stale test reports.

Do not print secrets. Redact tokens, API keys, cookies, JWTs, and message content that is not required to diagnose the issue.

## Trace by symptom

### Browser UI is stale or wrong

1. Check `index.html` script order and cache-busting query strings.
2. Search the relevant DOM IDs/classes across template, CSS, and scripts.
3. Check shared global state and cleanup when switching sessions/users/teams.
4. Run `node --check` for every changed JavaScript file.
5. Reproduce with browser tools and verify actual loaded asset content, viewport bounds, scroll locking, and network responses.
6. If a separate Nginx static directory is used, treat repository source as authoritative and sync source → deployment only after explicit authorization. Compare files afterward; never copy deployment files back over source.

### HTTP request fails

1. Locate the controller mapping and remember the `/chat` context path.
2. Check parameter binding, token/chatter identity checks, DTO validation, and `ServerResponse` error mapping.
3. Trace into the service/solution and selected storage implementation.
4. Determine whether the failure is validation, authentication, missing state, persistence, or downstream RPC.

### WebSocket message is missing, duplicated, or stuck streaming

1. Identify the request/response action code and server implementation.
2. Trace `ActionStrategyContext` and the selected handler.
3. Check session identity and connection mapping.
4. For robot messages, trace the event bus and handler chain.
5. Check stream start/update/end cleanup on backend and frontend, especially after session changes or reconnects.
6. Look for duplicate callbacks and verify event/session idempotency.

### ACP or cmd-proxy operation fails

1. Confirm `app.use-cmd-proxy`, RPC configuration, robot registration, and the expected instance/group mapping without exposing credentials.
2. Trace the outbound command and callback method as one protocol.
3. Match `instanceId` + `groupId` for ordinary ACP; do not route on a weaker identifier.
4. Check whether the callback was accepted, queued, retried, or rejected.
5. Check session rotation reason and `newSessionId`; duplicate session-change callbacks must be harmless.
6. Distinguish cmd-proxy unavailability from MolaChat projection/UI failure.

### Fast Team is stuck, offline, duplicated, or undeletable

Read `fast-team.md`. Inspect global mixed records, local fragment state, snapshot/robot projection, discovery/home mapping, and callbacks separately. Check for intermediate states such as creating, deleting, failed cleanup, or stale projection. Never invoke maintenance cleanup until the exact owner and target records are confirmed and the user has authorized the production mutation.

## Common misleading evidence

- `mvn test` can compile without running tests because `skipTests` defaults to true.
- Old `target/surefire-reports` files can contain failures or successes from a different run.
- A successful package does not prove tests executed.
- Backend unit tests do not prove browser behavior, Nginx asset freshness, cross-device routing, or production recovery.
- A plan document describes intent, not necessarily current implementation.

## Root-cause report

Give the failing boundary, evidence, causal chain, and scope. Separate confirmed facts from inference. If only diagnosing, stop before editing and propose the smallest safe fix plus verification plan.
