# MolaChat architecture map

## Runtime and build

- Maven coordinates: `com.mola:molachat:0.0.1-SNAPSHOT`.
- Runtime baseline: Java 8, Spring Boot 2.1.6, packaged as a jar.
- Default HTTP context path: `/chat`; the active profile comes from `application.yml`.
- UI: Thymeleaf template `src/main/resources/templates/index.html` plus vanilla JavaScript and CSS under `src/main/resources/static/`.
- Persistence is selected by `self-conf.cache-type`: in-memory cache, Redis, or LevelDB, with conditional factory implementations.
- WebSocket implementation is selected by `app.server-type`: Tomcat or Spring implementations exist.
- ACP/cmd-proxy integration is controlled by application configuration and the `cmd-proxy-client` dependency.

Never expose values from application files, keystores, tokens, API keys, JWT secrets, or production credentials in output. Inspect configuration keys and relevant non-secret values only.

## Package map

| Package | Responsibility | Common entry points |
| --- | --- | --- |
| `chatter` | users, presence, identity, heartbeat | `ChatterController`, `ChatterService` |
| `session` | sessions, messages, files, streaming state | `SessionService`, `MessageSolution`, file controllers |
| `server` | WebSocket servers and action dispatch | `ActionStrategyContext`, `SendMessageHandler`, `TomcatChatServer` |
| `robot` | robot creation, event buses, ACP/MCP/cmd-proxy | creators, handlers, `RobotSolution`, callback solutions |
| `group` | conventional group chat | `GroupController`, `GroupService`, `GroupSolution` |
| `team` | Fast Team projection and distributed lifecycle | `TeamController`, `TeamGatewaySolution`, event solutions |
| `common` | configuration, response types, storage utilities, AOP | config classes, `ServerResponse`, conditions |

## Typical message path

Browser message → WebSocket server → `ActionStrategyContext` → `SendMessageHandler` → session/message service → robot event bus/handler when the peer is a robot → `MessageSolution`/WebSocket response → browser rendering.

For ordinary ACP, inspect `AcpExecHandler`, cmd-proxy client calls, `CmdProxyCallbackSolution`, `AcpSessionChangedSolution`, and frontend `session.js`/`message.js`.

For Fast Team, inspect HTTP endpoints in `TeamController`, orchestration in `TeamGatewaySolution`, local projections in `TeamSolution` and `TeamRobotProjectionSolution`, outbound RPC in `TeamCommandTransport`, callback dispatch in `CmdProxyCallbackSolution`, event deduplication/routing in `TeamEventSolution`, and the specialized message/TalkTo/session event solutions.

## Frontend map

- `static/js/chat/session.js`: session selection, session state, ACP session changes.
- `static/js/chat/message.js`: message and stream rendering.
- `static/js/chat/chatter.js` and `user.js`: contacts/user state.
- `static/js/chat/group.js` and `multichat.js`: normal group chat.
- `static/js/chat/team.js`: Fast Team creation, list, state, messaging, and deletion UI.
- `static/js/app/remote.js`: HTTP helpers and remote interaction.
- `static/js/app/lifecycle.js`: page lifecycle behavior.
- `static/css/styles.css`: shared layout and component styling.
- `templates/index.html`: DOM structure and script load order; update cache-busting query strings when the deployment strategy requires it.

Because scripts use shared globals and load order, search definitions and all call sites before renaming or moving functions.

## Extension patterns

- Add a robot with a `RobotCreator`, event bus, and `IRobotEventHandler` implementation, then configure its app key.
- Add a robot command by extending `BaseCmdRobotHandler` and registering it in the relevant event bus.
- Add a WebSocket action through `ActionCode`, a `WSRequestActionHandler`, and `ActionStrategyContext` registration.
- Add persistence behavior through the existing factory interface and conditional implementation pattern.

Confirm patterns against current source; this repository is actively evolving and documents may lag code.
