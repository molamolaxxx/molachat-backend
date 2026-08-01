# Fast Team 需求可行性分析与功能点列表（MolaChat 侧）

## 1. 结论

需求总体可行，建议按“cmdproxy 管理 Team 定义与 ACPClient 生命周期，MolaChat 管理可重建投影、消息关联和页面模式”的边界实施。

现有代码可复用：

- `index.html` 与 `history.js` 已有底部弹框、列表、行尾删除和 SweetAlert 二次确认交互。
- `CmdSender` 已支持 MolaChat 调用 cmdproxy 命令；`CmdProxyCallbackSolution` 已支持 cmdproxy 回调同步 ACP robot 和流式消息。
- `AcpRobotSyncSolution` 已能创建/删除 ACP robot、关闭关联会话，并用 `visibleChatterIds` 将 robot 限定给指定 MolaChat 用户。
- 前端 `initChatter` 已集中负责联系人列表重绘与失效会话回退。

但不能直接把现有 ACP 同步逻辑原样用于 Team。当前 robot ID 是 `acp-{robotName}`，同一原始 robot 加入多个 Team 时会碰撞；消息路由目前主要依赖 MolaChat `sessionId/groupId`，还缺少明确的 `teamId/teamMemberId/acpClientId`。这是实施前必须先改造的核心。

建议可行性等级：**高（需先完成实例身份和生命周期协议）**。

## 2. 推荐职责边界

### cmdproxy

- Team ACP 运行时的权威数据源。
- 创建、查询运行状态、确保运行时存在、删除 Team 运行时。
- 为每个成员创建独立于主会话的 ACPClient。
- 建立仅在 Team 内有效的临时通讯录和 `talkTo` 路由。
- 维护 Team、TeamMember、ACPClient 的生命周期和状态。
- 删除时幂等地停止进程、注销通讯录、释放 ACPClient。

### MolaChat 后端

- 保存不含凭据的 Team 投影、消息关联和设备 UI 状态，并通过 cmdproxy `list/get` 对账。
- 对浏览器提供同源 `/team` API，并验证当前 `chatterId/token`。
- 使用 `CmdSender` 调用 cmdproxy，避免浏览器直接依赖 cmdproxy 地址、协议及鉴权。
- 将 Team member 映射成 MolaChat `RobotChatter`，处理会话与流式消息路由。
- 只向 Team 所属用户暴露 Team robot。

### MolaChat 前端

- 展示 Teams 操作弹框和发起 Team 表单。
- 保存当前页面模式（主会话或某个 Team），但不把 Team 列表作为权威数据持久化在浏览器。
- Team 模式只渲染该 Team 成员，并自动打开第一个成员会话。
- 切换/删除后根据服务端结果刷新，而不是乐观假定 cmdproxy 已完成。

## 3. 数据与身份方案

推荐最小模型：

```text
Team
  teamId             全局不可变 UUID
  ownerChatterId     MolaChat 用户 ID
  name               同一 owner 下唯一，1~40 字符
  status             CREATING | READY | DELETING | FAILED
  createdAt
  members[]

TeamMember
  teamMemberId       全局不可变 UUID
  teamId
  sourceRobotId      被选择的原 ACP 会话/robot 标识
  sourceGroupId      来源普通 ACP robot 的传输通道
  acpClientId        cmdproxy 新建实例标识
  molaRobotId        推荐 team-acp-{teamMemberId}
  displayName
  avatar
  status
```

关键约束：

- `molaRobotId` 不能继续由 robot 名称生成，推荐 `team-acp-{teamMemberId}`。
- UI 显示名允许重复，程序路由只使用不可变 ID。
- 每次发送 ACP 消息至少能解析到 `teamId + teamMemberId/acpClientId + groupId`。
- `visibleChatterIds={ownerChatterId}` 只能解决“谁能看见”，不能区分该用户的主模式与多个 Team；还需给 `RobotChatter/ChatterDTO` 增加 `teamId`（以及建议的 `teamMemberId`）。
- Team robot 使用独立 `robotGroup`（推荐 `team-acp`），避免现有普通 ACP 全量同步误删 Team robot。

## 4. 功能点、待确认问题与默认最佳方案

### F1. 左下角 Teams 按钮与 Teams 操作弹框

功能：

- 在左下固定操作菜单增加 `teams` 按钮。
- 点击后打开底部 `teams-modal`。
- 列出当前用户的 Team，标记当前 Team；每行可切换，行尾可删除。
- 底部提供“发起 Team”和“切回主会话”。

待确认：

1. “左下方”是现有浮动菜单中的一个子按钮，还是页面永久可见的独立按钮？
2. Team 行需要展示哪些摘要：成员头像、成员数、状态、创建时间？
3. 主会话模式下“切回主会话”是否隐藏或置灰？

默认推荐：

- 作为现有 `#menu` 的一个子按钮加入，图标用 `groups`，保持当前页面交互一致。
我的建议：除了#menu的子按钮，可以把tool-contacts替换成groups功能按钮，因为tool-contacts现在的功能只是隐藏侧边栏，用处不太大
- 弹框复用历史用户 bottom-sheet 的宽度、定位与列表语言，但使用独立 DOM/class/JS，避免耦合 `history.js`。
- Team 行展示名称、最多 3 个成员头像、成员数和状态；当前 Team 有状态点。
- 主会话下“切回主会话”置灰并显示“当前为主会话”。

### F2. 发起 Team：选择成员

功能：

- 点击“发起 Team”进入成员多选。
- 候选项来自当前用户可用的 ACP 会话 robot。
- 至少选择若干成员后继续填写 Team 名称并确认。

待确认：

1. “多个 acp 会话的 robot”选择的是 robot 配置，还是某个 robot 的具体历史 ACP 会话？
2. 新 ACPClient 是复制所选会话上下文，还是只复制 robot 配置并创建空白会话？
3. 同一个来源 robot/会话能否在一个 Team 中被选择多次？
4. 最少和最多成员数是多少？
5. 离线、忙碌或恢复失败的 ACP 会话是否允许选择？

默认推荐：

- 候选单位定义为当前用户可见的“普通 ACP robot”，提交 `sourceRobotId + sourceGroupId`；复制 provider、workDir、模型、MCP 和必要权限配置，但强制创建全新的 Team session，不加载主 robot 的最近历史。这样可以避免主会话历史冲突和误杀主 ACP 进程。
- 同一来源 robot 在同一 Team 内不可重复，但允许同时存在于多个 Team。
- 最少 2 人、首版最多 6 人；限制由 cmdproxy 配置并在查询能力时返回，前端不写死权威值。
- BUSY robot 可以选择，因为 Team 会新建独立 client；ERROR、disabled、onlySubAgent 默认不可选择。
- 多选列表视觉参考历史用户列表，选中态使用 checkbox/边框；不要复用“历史用户切换二次确认卡片”的业务逻辑。

### F3. Team 名称与创建确认

功能：

- 用户填写队伍名称。
- 确认后调用 MolaChat 后端创建接口；按钮进入 loading，防重复提交。

待确认：

1. Team 名是否允许同一用户重名？
2. 名称长度和字符限制？
3. 创建部分成功时，保留可用成员还是整体回滚？
4. ACPClient 创建可能较慢，页面同步等待还是异步显示进度？

默认推荐：

- 同一用户下名称不重复，trim 后 1~40 字符，允许中英文、数字、空格、`-`、`_`。
- 请求携带 `requestId`，服务端幂等创建。
- 采用异步状态模型：接口在短时间内尽量返回 READY；超时则返回 CREATING，前端显示进度并短轮询 Team 详情。
- 首版采用全有或全无：任一成员创建失败，cmdproxy 回收本次已创建 ACPClient，Team 标记 FAILED 并返回结构化错误。这样不会出现通讯录不完整的“半队伍”。

### F4. cmdproxy 创建 Team ACPClient 与临时通讯录

功能：

- 每个 Team member 新建专属 ACPClient，与原 robot 的 ACPClient 隔离。
- Team 内成员拥有互相 `talkTo` 的临时通讯录。

待确认：

1. `talkTo` 目标名重名时如何解析？
2. 通讯录是否允许成员联系 Team 外 agent？
3. Team ACPClient 是否需要持久化并在 cmdproxy 重启后恢复？
4. 原 ACP 会话继续运行时，Team 实例是否共享文件、技能、权限和工作目录？
5. Team 内消息是否需要在 MolaChat 展示审计记录？

默认推荐：

- 通讯录内部以 `teamMemberId` 路由，显示名只用于 UI；同名时 UI 自动加来源后缀。
- 临时通讯录严格 Team 隔离，默认不能联系 Team 外 agent。
- Team 元数据持久化；ACPClient 可在重启后按保存的来源会话和配置惰性恢复。
- 工作目录、技能与权限继承来源 ACP 会话配置，但运行上下文和消息历史复制后独立。
- 首版记录结构化 `talkTo` 事件日志（时间、发送方、接收方、状态），正文是否展示可另行开关。

### F5. 创建成功后自动进入 Team 模式

功能：

- 创建成功后关闭弹框。
- 左侧联系人只显示 Team 内成员。
- 自动打开第一个 robot 会话。

待确认：

1. “第一个”按用户勾选顺序还是服务端成员顺序？
2. 进入 Team 模式后是否显示用户本人、公共会话和普通联系人？
3. 打开第一个 robot 是恢复已有 MolaChat 会话还是总是新建？
4. 刷新页面后是否保留 Team 模式？

默认推荐：

- 服务端保存 `memberOrder`，默认按用户勾选顺序；第一个 READY 成员作为自动打开对象。
- 左侧只显示 Team member，不显示本人、公共会话和普通联系人。
- 复用 MolaChat 现有点联系人建会话逻辑；同一用户与同一 Team member 已有会话时恢复，不重复创建。
- `activeTeamId` 只按当前浏览器用户保存在 `localStorage`；刷新时必须向服务端校验 Team 仍存在且 READY，无效则回主模式。
- 模式过滤集中放在联系人数据进入 `initChatter` 前，避免在多个 DOM 操作点零散隐藏。

### F6. 切换 Team

功能：

- Teams 弹框列出用户的多个 Team。
- 点击 Team 后切换模式并打开该 Team 第一个成员。

待确认：

1. 切回某 Team 时是否恢复上次活跃成员，还是总是第一个？
2. 当前存在流式回复时是否允许立即切换？
3. Team 为 CREATING/FAILED/DELETING 时如何处理？

默认推荐：

- 首次进入打开第一个成员；之后记录每个 Team 的 `lastActiveMemberId`，再进入优先恢复，成员失效时回退第一个 READY 成员。
- 切换只改变 MolaChat 展示，不停止后台 ACP 输出；联系人未读/streaming 状态仍保留。
- 只有 READY Team 可进入；CREATING 显示进度，FAILED 提供错误与删除入口，DELETING 禁止操作。

### F7. 切回主会话

功能：

- 点击底部按钮退出 Team 模式，恢复原联系人列表和主会话体验。

待确认：

1. “切回原样”是否要恢复进入 Team 前的联系人会话？
2. Team ACPClient 是否继续存活？

默认推荐：

- 保存 `lastMainActiveChatterId`，切回时优先恢复；目标不存在则回联系人列表，不随意打开其他会话。
- 仅切换显示模式，不销毁 Team 或 ACPClient；删除 Team 才触发生命周期清理。

### F8. 删除 Team

功能：

- 点击 Team 行尾删除 icon。
- 二次确认后调用删除接口。
- cmdproxy 删除 Team、通讯录和所有 Team ACPClient；MolaChat 删除对应 Team robot 及其会话。

待确认：

1. 删除是永久删除还是可恢复的归档？
2. ACPClient 正在输出或 talkTo 时是否强制终止？
3. MolaChat 聊天记录是否同时永久删除？
4. 删除当前 Team 后进入主会话还是切到其他 Team？

默认推荐：

- 首版永久删除，SweetAlert 明确提示不可恢复和成员数量。
- 删除接口幂等；cmdproxy 先将 Team 标记 DELETING，拒绝新消息，再取消任务、注销通讯录、关闭 ACPClient，最后删除/墓碑化 Team。
- MolaChat 删除 Team robot 并关闭其 Session；若未来需要审计，应另做导出/归档，不保留不可访问的孤儿会话。
- 删除当前 Team 成功后回主会话；删除失败则保留当前 UI 并展示可重试错误。
- 前端点击删除必须 `stopPropagation()`，避免同时触发切换。

### F9. 鉴权、隔离与资源限制

待确认：

1. 每个用户最多多少 Team、每个 Team 最多多少成员？
2. cmdproxy 如何可信获取 owner，而不是信任浏览器提交的 `ownerChatterId`？
3. 多设备同时切换是否需要同步 Team 模式？

默认推荐：

- MolaChat 使用现有 `chatterId + token` 校验用户；后端从已验证身份填充 owner，不接受浏览器指定任意 owner。
- 默认每用户最多 10 个 READY/CREATING Team、每 Team 2~8 成员、全局 ACPClient 数另设硬限制。
- Team 模式是设备级 UI 状态，不跨设备同步；Team 数据和成员状态跨设备一致。
- 所有查询、详情、删除均校验 `team.ownerChatterId == currentChatterId`。

### F10. 异常恢复与一致性

待确认：

1. cmdproxy 或 MolaChat 重启后的 Team 恢复目标是什么？
2. MolaChat robot 同步失败时谁负责补偿？
3. 网络超时后用户重试如何避免重复创建？

默认推荐：

- 创建/删除都使用 `requestId` 和幂等结果缓存。
- cmdproxy 是 Team 定义、成员身份、状态机和 ACP 运行时的单一权威源，并持久化 runtime/session journal 与删除墓碑；MolaChat 重连后通过 `acpTeamList/Get` 对账本地投影。
- 回调只作实时加速，不能作为唯一一致性机制。
- Team robot 同步以 `teamId` 为作用域，绝不能让普通 `acpSyncRobots` 删除 `team-acp` robot。
- 创建和删除暴露状态及成员级错误，便于前端展示与运维排查。

## 5. 推荐 API 草案（MolaChat 对前端）

```text
GET    /team?chatterId=&token=
GET    /team/candidates?chatterId=&token=
POST   /team
GET    /team/{teamId}?chatterId=&token=
DELETE /team/{teamId}?chatterId=&token=&requestId=
```

创建请求：

```json
{
  "chatterId": "user-id",
  "token": "jwt",
  "requestId": "uuid",
  "name": "fast team",
  "members": [
    {
      "sourceRobotId": "acp-open-code",
      "sourceGroupId": "source-group-id"
    }
  ]
}
```

统一 Team 响应至少包含：

```json
{
  "teamId": "uuid",
  "name": "fast team",
  "status": "READY",
  "members": [
    {
      "teamMemberId": "uuid",
      "molaRobotId": "team-acp-uuid",
      "displayName": "Open Code",
      "avatar": "...",
      "status": "READY",
      "order": 0
    }
  ]
}
```

MolaChat 到 cmdproxy 的命令名建议统一为：

```text
acpTeamCreate
acpTeamGet
acpTeamList
acpTeamSend
acpTeamCancel
acpTeamDelete
```

“切换 Team”本质是 MolaChat UI 视图切换，默认不需要 cmdproxy 的 mutation 接口；进入时只需拉取/校验 Team 状态。

## 6. MolaChat 预计改动点

- `templates/index.html`
  - 增加 Teams 菜单按钮、Teams bottom-sheet、发起 Team 表单/弹框容器。
  - 引入独立 `js/chat/team.js`。
- `static/css/styles.css`
  - 增加 Team 列表、多选成员、状态、删除确认样式。
- `static/js/chat/team.js`（新增）
  - Team 查询、渲染、创建、切换、主模式恢复、删除。
  - 管理设备级 `activeTeamId`、`lastMainActiveChatterId`、各 Team 最后活跃成员。
- `static/js/chat/chatter.js`
  - 在联系人渲染入口支持按当前模式过滤，尽量不新增与 DOM 重复的缓存状态。
- 新增 `team/controller`、`team/solution`、DTO
  - JWT 校验、参数校验、`CmdSender` 调用、错误映射、Team robot 对账。
- `RobotChatter` / `ChatterDTO`
  - 增加 `teamId`、`teamMemberId`，或等价的明确作用域字段。
- `AcpRobotSyncSolution`
  - 普通 ACP 与 Team ACP 分组同步；Team robot ID 改为实例 ID。
- `CmdProxyCallbackSolution` / `AcpExecHandler`
  - 流式消息和命令调用携带 Team member 路由信息，避免仅凭名称或宽泛 `acp` 前缀寻找 robot。

## 7. 分阶段落地与验证

### 阶段 A：协议和身份

- 双方冻结 Team/Member/ACPClient ID、状态机、错误码和幂等语义。
- 用命令行/单测验证同一来源 robot 可同时存在于两个 Team 且路由不串线。

### 阶段 B：cmdproxy 生命周期 + MolaChat 后端

- 完成 candidates/list/create/get/delete。
- 验证部分创建失败回滚、重复请求、删除进行中、重启对账。

### 阶段 C：最小前端闭环

- Teams 弹框、创建、多选、名称、自动进入、切换、回主会话、删除。
- 先只覆盖桌面端一个交互闭环并实际点击验证，再补移动端样式，避免批量修改现有弹框。

### 阶段 D：联调和故障测试

- 两个 Team 选择同一来源 robot。
- 多 Team 来回切换，恢复上次会话。
- Team 成员流式输出时切换和删除。
- 创建/删除接口超时后重试。
- cmdproxy 重启、MolaChat 重启、浏览器刷新。
- 删除当前 Team、删除非当前 Team、行尾删除不触发切换。
- 普通 ACP robot 不被 Team 同步误删，Team robot 不出现在主模式。

前端源码变更上线时，还需按实际变更文件同步到 Nginx 的 `molaapp` 静态目录，并用 SHA-256 校验源码与部署文件一致。

## 8. 当前必须优先确认的五个产品问题

1. 候选成员究竟是“robot 配置”还是“robot 的具体 ACP 历史会话”？
2. Team 新实例是复制历史上下文，还是从空白上下文启动？
3. Team 模式刷新后是否保持，以及重新进入是否恢复上次活跃成员？
4. 删除 Team 时聊天记录是否永久删除，是否需要审计/导出？
5. 成员数、Team 数和 ACPClient 全局资源上限是多少？

在没有额外答复时，本文各功能点下的“默认推荐”可直接作为首版产品与技术基线。

## 9. 双方联合评审后的统一基线

与 cmdproxy 开发负责人交叉评审后，双方统一采用以下架构基线：

1. **单一权威源**：cmdproxy 的 `TeamManager` 持久化 Team 定义、成员身份、状态机、运行 journal 和删除墓碑；MolaChat 保存可重建投影、消息关联和设备 UI 状态，通过 `acpTeamList/Get` 对账。
2. **每成员独立实例**：每个 Team member 都有独立 ACPClient、子进程、session、executor 和历史命名空间 `team/{teamId}/{memberId}`，不能全队共享一个 client。
3. **不复用全局运行容器**：Team 使用独立 `TeamClientRegistry` 和每队独立 `TeamTalkToDispatcher`，不能直接放入现有 singleton `AcpClientRegistry` 或按 robotName 寻址的全局 TalkTo。
4. **传输与身份分离**：`robotGroup=team-acp` 只作逻辑分类；RPC 使用实例级稳定 `transportGroup=team-acp-{cmdProxyInstanceId}`，payload 必须携带 `teamId/teamMemberId/acpClientId`。`sourceGroupId` 仅用于解析来源 robot 配置，不作为 Team member 身份或独立 RPC group。
5. **新会话而非复制历史**：首版复制来源 robot 的运行配置快照，但强制 `session/new`，禁止按 robotName 自动恢复最近 session，防止 Team 与主会话争用历史甚至误杀主 ACP 进程。
6. **统一事件信封**：cmdproxy 通过 `acpTeamEvent` 返回 `eventId/eventSeq/teamId/memberId/type/data/timestamp`，MolaChat 去重、排序并持久化必要事件。
7. **删除屏障**：Team 先进入 DELETING 并拒绝 send/talkTo；随后按 `session/cancel → session/end → destroy → destroyForcibly` 逐级清理，并移除 registry、executor、inbox、去重状态和临时通讯录。
8. **Phase 0 先行**：先完成 client identity/history 隔离、优雅关闭、Team registry/dispatcher 和并发状态机，再开发 Teams 弹框，避免 UI 完成后暴露串会话、跨队串话和僵尸进程问题。

关于 `groupId` 的联合约定：V1 采用实例级稳定 transportGroup，不给每个 Team 或 member 动态注册 RPC group。若未来确需 member 级 group，必须先为 `CmdReceiver` 实现可靠的 unregister、断线重注册和删除回收。

双方报告有一项 UI 建议差异：cmdproxy 侧提出 Team 聚合时间线，但原始需求明确“会话框开启第一个 robot 的会话”。因此首版仍按原需求进入第一个 READY robot 的单成员会话；聚合时间线列为后续增强，不纳入 V1。

cmdproxy 侧完整分析见：

`/home/mola/IdeaProjects/cmd-proxy/docs/fast-team-feasibility-analysis.md`
