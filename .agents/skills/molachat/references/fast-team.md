# Fast Team and ACP integration constraints

## Model and identity

- Fast Team supports 1–6 members. A one-member Team remains a real isolated Team; never downgrade it to an ordinary ACP session.
- MolaChat persists the global mixed-Team view while each cmd-proxy owns/reuses its local Team fragment.
- Preserve `teamId`/`teamMemberId`, owner, placement, `instanceId`, `transportGroup`, `groupId`, and session identity across projections.
- A one-member Team has no in-Team `talk_to` peer, but external channels may still be available.

## Concurrency and callback contract

- Never perform remote RPC while holding the per-owner lock. Use lock to prepare state, release it for RPC, then reacquire it to merge results after confirming the record is still current.
- Treat callbacks as asynchronous and potentially early, duplicated, retried, delayed, or reordered across independent transports.
- MolaChat callback entry uses a bounded single-worker FIFO per transport group so the RPC callback thread does not synchronously re-enter the Team state machine.
- Queue rejection/closure must be visible to the provider so retry remains possible; do not silently drop required events.
- Keep idempotency and terminal-state guards so an old create/cleanup thread cannot resurrect or regress a newer record.
- Distinguish weak projection events from routing events requiring admission. A TalkTo route may report submission success only after the event sink accepts it.
- Include event worker/sink shutdown in component lifecycle.

When modifying this area, test callback-before-command-response re-entry, FIFO behavior, duplicate events, queue full/closed, provider timeout, partial create/delete, compensation races, and stale callback arrival.

## ACP session changes

- Route ordinary MAIN ACP session changes by `instanceId` + `groupId`.
- Ignore an already-current `newSessionId` idempotently.
- On a valid change, clear stale stream/cache/session-list state and refresh the projection.
- Preserve reason-specific messaging: automatic idle rotation can produce the backend session message; manual rotation uses the cmd-proxy response. Do not reintroduce a generic frontend “ACP switched session” toast.
- Map disconnected ordinary ACP and Team instances to the offline UI state and recover it on reconnect.

## Reconnect and deployment identity

- Each cmd-proxy instance needs a stable, unique identity. Prefer explicit `CMD_PROXY_INSTANCE_ID` or `-Dcmd.proxy.instanceId`; otherwise identity must be persistently derived and reused.
- During Team recovery, verify persisted `transportGroup` still belongs to the active instance and migrate/save it before starting members and callbacks if needed.
- Validate robot registration, heartbeat, discovery, callbacks, old Team recovery, and reconnect as separate steps.

## Production maintenance safety

The known MolaChat API endpoint is TLS on port 8550. Treat endpoint, certificates, credentials, and current maintenance behavior as deployment facts that may change; verify them before use.

The stale-Team maintenance endpoint is a production mutation. Before calling it:

1. obtain explicit authorization;
2. query state using the intended user identity;
3. confirm the exact owner and stale states;
4. send valid authenticated parameters only through a secure mechanism;
5. re-query global records, snapshots, and robot projections afterward;
6. handle an orphan local fragment through the normal Team delete flow when appropriate.

Never embed tokens in committed files or expose them in command output.
