# MolaChat 架构参考

## 项目坐标

- Group ID：`com.mola`，Artifact：`molachat`，Version：`0.0.1-SNAPSHOT`
- Actuator 端口：`9002`

## 包结构（`com.mola.molachat`）

| 包 | 职责 |
|---|------|
| `chatter` | 用户（Chatter）增删改查、状态管理、积分系统、心跳 |
| `session` | 会话生命周期、消息插入/更新、视频会话、文件上传/下载 |
| `server` | WebSocket 服务端（Tomcat/Spring）、Action 分发、连接管理 |
| `robot` | AI 机器人框架：事件总线 → 处理链 → Action 响应 |
| `group` | 群聊创建、成员管理、群会话 |
| `common` | 配置、工具类、AOP 切面、异常、LevelDB 客户端、Redis 工具、事件总线 |

## 设计模式

- **事件总线 + 处理链**：`RobotEventBus` 将 `BaseRobotEvent` 分发给有序的 `IRobotEventHandler` 实现，返回 `BaseAction`
- **工厂模式**：`ChatterFactory`、`SessionFactory`、`ServerFactory`、`GroupFactory`，按 Redis/LevelDB/Cache 条件化实现
- **策略模式**：`ActionStrategyContext` 将 WebSocket Action（`SendMessage`、`CreateSession`、`HeartBeat`、`VideoRequest/Response`）路由到对应 Handler
- **创建者模式**：`RobotCreator` 实现类（`ChatGptRobotCreator`、`McpCreator`、`AcpCreator` 等）通过匹配 `appKey` 创建机器人实例

## 机器人 / AI 系统

通过 `app.robot-list` 配置：`chatGpt,stableDiffusion,toolRobot,deepSeek,mcp,agent,acp`

| 机器人类型 | Handler | 说明 |
|-----------|---------|------|
| ChatGPT | `ChatGptRobotHandler` | 通过 `ChatGptSolution` 实现流式/非流式 GPT 调用，支持模型选择 |
| DeepSeek | 同框架 | DeepSeek 模型集成 |
| MCP | `McpExecHandler` + `McpProcess` | MCP 协议：与 LLM 循环交互，执行 Shell 命令（需确认），流式输出 |
| ACP | `AcpExecHandler` | ACP 命令执行，支持图片 OCR、文件解析 |
| Stable Diffusion | `ImageGenerateChatHandler` | 通过 `ImageGenerateSolution` 生成图片 |
| Tool Robot | `BaseCmdRobotHandler` 子类 | 内置命令：翻译、Base64 编解码、日期解析、OCR、通知、KV 存储、GPT 预设 |

### 命令系统

命令通过 `IRobotEventHandler.cmdDescriptions()` 注册：
- `/help`、`/translate`、`/encode64`、`/decode64`
- `/dateparse`、`/dateformat`、`/notify`
- `/ocr`、`/eval`、`/stop`
- `/kv-set`、`/kv-get`、`/kv-list`、`/kv-del`
- `/gpt-preset-save`、`/gpt-preset`
- `/group-fetch`

### MCP 流程（`McpProcess`）

核心 AI Agent 循环：
1. 构建系统提示词（包含命令描述和项目上下文）
2. 调用 LLM 获取下一步动作（命令或文本回复）
3. 从 LLM 输出中解析 `<next_cmd>`、`<next_targets>`、`<next_summary>`
4. 通过 `DangerousCmdChecker` 安全校验和 `CmdConfirmManager` 用户确认后执行 Shell 命令
5. 通过 `MessageSolution` 将结果流式推送到聊天
6. 循环直到 LLM 发出完成信号

## WebSocket 通信

- **Tomcat**：`TomcatChatServer`，使用 `@ServerEndpoint`
- **Spring**：`SpringWebSocketChatServer`，使用 `WebSocketHandler`
- Action 编码：`SEND_MESSAGE(1)`、`CREATE_SESSION(2)`、`HEART_BEAT(3)`、`VIDEO_REQUEST(4)`、`VIDEO_RESPONSE(5)`
- 响应：`WSResponse`，包含消息、列表、异常、流式、视频事件、命令确认等编码

## 配置类

- `AppConfig`：版本、robotList、robotApiKey、levelDB 路径、httpProxy、cmdProxy 开关
- `SelfConfig`：超时、最大客户端数、最大消息数、上传路径、缓存类型
- `RedisConfig`、`LevelDBConfig`：存储后端配置
- `TomcatConfig`：文件上传大小限制
- `GlobalCorsConfig`：CORS 过滤器
- `RobotConfig`：从 `app.robot-list` 初始化机器人
