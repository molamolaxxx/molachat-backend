# Fast Team 本机与 Remote ACP 混选 MVP

> 状态：MolaChat / cmd-proxy 基于 2026-08-08 最新工作树重新收敛，待实施
>
> 最终产品范围：纯本机 Team 继续允许；混选 Team 支持本机 + 多个 remote cmd-proxy；remote-only 禁止
>
> cmd-proxy 对应基线：`/home/mola/IdeaProjects/cmd-proxy/docs/fast-team-remote-mixed-mvp.md`

> 2026-08-08 最终开工确认：remote cmd-proxy 使用 B 侧 standing allowlist
> 授权 owner A；每个 owner 在 MolaChat 持久化唯一 home cmd-proxy，首次多候选时由用户
> 显式选择；remote 只发布 instance-scoped Team candidates，不给 A 同步普通 ACP；
> B 不逐次审批但提供运行可见性与撤销；普通 ACP 投影只接受 owner home 实例的快照。

## 1. 最终需求

Fast Team 创建规则如下：

1. 纯本机 ACP Team 继续允许，成员数为 `1~6`，沿用现有 V1 单实例链路。
2. 混选 Team 必须至少包含一个可信本机 ACP 和一个 remote ACP，因此实际至少 2 人。
3. remote ACP 可以来自一个或多个不同 cmd-proxy，不限制 remote 实例数；全队仍最多 6 人。
4. 只选择 remote ACP 的请求必须由前后端共同拒绝。
5. 同一来源 ACP 在同一 Team 中不得重复。
6. “本机”由 MolaChat 根据 owner 的可信主实例绑定和 discovery 判定，前端不能提交 `isLocal=true` 自报。
7. 前端不展示原始 `cmdProxyInstanceId/transportGroup`，但这些字段继续用于后端 placement 校验和运行路由。
8. 每个 owner 只有一个持久化 `homeCmdProxyInstanceId`；首次仅有一个 V1 实例时自动选择，
   首次有多个实例时必须由用户显式选择，存在活跃 Team 时禁止静默切换。
9. remote participant 必须由其 cmd-proxy 的 standing allowlist 授权当前 owner；裸
   `chatterId`、可见 RPC provider 和普通 `visibleChatterIds` 均不能作为授权证明。

此前“整支 Team 放到单个 remote 实例、owner 单活 placement、cmd-proxy 零改动”的方案已经废弃；它不能满足混选。本轮也不采用旧 V2 的完整分布式控制面。

## 2. 最小架构

### 2.1 权威边界

- MolaChat 是混选 Team 的全局定义、成员 placement、全局状态和补偿进度权威。
- 每个参与 cmd-proxy 是本实例 fragment、ACPClient、session、inbox 和资源生命周期权威。
- 纯本机 V1 Team 仍由单个 cmd-proxy 完整权威管理，不迁移到混选模型。

### 2.2 设备发现、授权与普通 ACP 隔离

- cmd-proxy 的普通 `chatterIds/visibleChatterIds` 继续只定义普通 ACP 投影范围。
- B 通过每个 ACP 配置的 `teamSharedWithChatterIds` standing allowlist 授权 owner A，grant 绑定
  `granteeOwnerChatterId + remoteCmdProxyInstanceId`，并由 B 在候选查询、create 和运行命令时校验。
- B 通过独立 `remoteTeamMemberSources` discovery 发布 A 可用的候选；不得为了让 A 看见
  remote candidates 而把 A 加入 B 的普通 `visibleChatterIds`。
- MolaChat 对每个 owner 只允许其 home 实例的普通 robots 快照创建、更新或删除普通 ACP
  投影；非 home callback 只更新 instance-scoped Team registry，不能删除 home 投影。
- remote candidate 的运行身份至少包含
  `cmdProxyInstanceId + sourceGroupId + sourceRobotId`，不得复用普通 `acp-{robotName}` 作为全局身份。
- standing allowlist 不要求逐次审批；B 必须展示被借用的 owner/Team/member 与运行/清理
  状态，并允许撤销。撤销后拒绝新 create/send/session/talkTo，允许当前 turn 收尾，且
  cancel/delete 始终允许。

MVP 假设只有一个 MolaChat 协调进程。MolaChat 复用现有 `KeyValueFactoryInterface` 持久化轻量 `MixedTeamRecord` 和 owner home binding，并以 team/owner JVM 锁串行状态修改；本轮不建设专用 CAS Store、多节点选主或租约。

### 2.3 MixedTeamRecord

MolaChat 按 `teamId` 保存最少全局记录：

```json
{
  "schemaVersion": "mixed-1",
  "teamId": "uuid",
  "ownerChatterId": "owner",
  "name": "研发小队",
  "state": "CREATING|READY|RECOVERING|DELETING|FAILED|PENDING_CLEANUP|DELETED",
  "requestId": "uuid",
  "payloadHash": "sha256",
  "homeInstanceId": "local-instance",
  "participants": [
    {
      "instanceId": "local-instance",
      "transportGroup": "team-acp-local-instance",
      "state": "CREATING|READY|FAILED|DELETING|DELETED",
      "memberIds": ["member-1"]
    },
    {
      "instanceId": "remote-instance-a",
      "transportGroup": "team-acp-remote-instance-a",
      "state": "CREATING|READY|FAILED|DELETING|DELETED",
      "memberIds": ["member-2"]
    }
  ],
  "members": [
    {
      "teamMemberId": "member-1",
      "acpClientId": "team-acp-member-1",
      "participantInstanceId": "local-instance",
      "sourceRobotId": "acp-codex",
      "sourceGroupId": "source-group",
      "displayName": "Codex",
      "order": 0,
      "state": "STARTING|READY|BUSY|ERROR|CLOSED"
    }
  ],
  "lastError": null
}
```

该记录必须在第一个 participant RPC 前落盘，使 MolaChat 重启后仍能继续聚合、补偿或删除。

### 2.4 cmd-proxy 本地 fragment

不新增 FragmentStore 或第二套 runtime。每个参与实例继续使用现有 `TeamDefinition/TeamManager/TeamStore/TeamClientRegistry`，并保存相同 `teamId`；其中 `TeamDefinition.members` 只包含本实例实际启动的成员。

每个本地定义额外持久化全队最小 roster：

```text
teamMemberId, acpClientId, displayName, remark, order
```

roster 不保存其他实例的 sourceGroupId、凭据、workDir、skills、MCP 或完整配置。roster 中不属于本地 `members` 的条目只作为 remote contact。

## 3. 最小协议变化

### 3.1 Discovery capability

cmd-proxy discovery 增加可选 capability：

```json
{"mixedTeamFragment":true,"mixedTeamTalkToDeliver":true}
```

只有本机和所有被选 remote participant 都声明能力时，MolaChat 才允许创建混选 Team。旧实例仍可参与纯本机 V1 Team。

remote grant 不混入普通 `teamMemberSources`，而使用：

```json
{
  "remoteTeamMemberSources": [{
    "granteeOwnerChatterId": "owner-a",
    "participantInstanceId": "remote-instance-b",
    "sourceGroupId": "instance-scoped-group",
    "sourceRobotId": "acp-codex",
    "displayName": "Codex"
  }]
}
```

### 3.2 扩展 acpTeamCreate

继续复用现有 `acpTeamCreate`，增加：

- `mixedPlacement=true`
- 目标实例的 local `members`
- 全队一致的 `roster`
- 每个 participant 稳定且可重试的 fragment requestId

每个 cmd-proxy 只解析并启动本实例来源成员，同时校验 local members 全部存在于 roster、ID/顺序唯一、roster 总人数为 `1~6`。没有 mixed 字段的请求保持 V1 行为。

### 3.3 跨实例 talkTo

新增 Team event 类型 `TALK_TO_ROUTE_REQUEST` 和目标命令 `acpTeamTalkToDeliver`。

- 目标在当前 fragment：继续由现有 `TeamTalkToDispatcher` 直投或进入本地 inbox。
- 目标只存在于全队 roster：发送 `TALK_TO_ROUTE_REQUEST` 给 MolaChat。
- MolaChat 根据 MixedTeamRecord 校验 sender/target placement，再调用目标 participant 的 `acpTeamTalkToDeliver`。
- 目标实例再次校验 Team、roster、目标本地归属、TTL/depth，并复用本地 deliver/inbox。
- 网关不可用或校验失败时明确拒绝，禁止回退普通 crossTalkTo 或按显示名寻找 robot。

目标投递以 `teamId + messageId` 做 MVP 进程内去重；跨重启 exactly-once 和同步 ACK 不进入本轮。

## 4. 端到端流程

### 4.1 Create：直接创建与删除补偿

1. MolaChat 用最新 discovery 校验全部候选和 capability，并由后端判定 LOCAL/MIXED。
2. 纯本机选择继续走 V1；混选必须包含至少一个可信本机成员和至少一个 remote 成员，remote-only 直接拒绝。
3. MolaChat 生成全局 teamId/teamMemberId，按 instanceId 分组，在任何 RPC 前持久化 `CREATING` MixedTeamRecord 和 payloadHash。
4. 并行向本机和 N 个 remote participant 发送幂等 `acpTeamCreate`：每次只传该实例 local members，但传相同 roster。
5. 所有 fragment READY 后，全局 Team 才进入 READY并创建统一 Team robot 投影。
6. 任一 fragment 创建失败时，对全部已接受 fragment 调用现有幂等 `acpTeamDelete`。
7. 全部补偿完成后全局 FAILED；存在离线 fragment 无法清理时进入 PENDING_CLEANUP并禁止 send。

这是不可省的轻量补偿 saga，但不增加 prepare/commit/abort、prepare lease 或两阶段提交。

### 4.2 Member 命令

混选 Team 的所有成员命令按以下路径路由：

```text
teamId + teamMemberId
  -> MixedTeamRecord participantInstanceId + transportGroup
  -> 现有 acpTeamSend/Cancel/NewSession/ListSessions/
     RestoreSession/GetStatus/GetContextUsage/MemoryDream
  -> participant TeamClientRegistry(teamId, teamMemberId)
```

只有全局 Team 和目标 participant 都 READY 时才接受新命令。显示名、robotName 和 sourceGroupId 不参与运行路由。

### 4.3 List/get 与恢复

- 纯本机 V1 Team 继续从可信本机实例执行现有 `acpTeamList/Get`。
- 混选 Team 以 MixedTeamRecord 为列表基线，按已知 participants 定向 `acpTeamGet` 刷新 fragment 状态，不扫描未知实例猜测 placement。
- 任一 participant 不可达时 Team 保持可见并进入 RECOVERING；MVP 在 RECOVERING 期间统一禁止所有成员的新 prompt。
- MolaChat 在 CREATING/DELETING/PENDING_CLEANUP 状态重启后，根据持久 participants 继续 get、补偿或删除。

### 4.4 Event

MolaChat 从 callback 注册闭包取得真实 participantInstanceId，不信任 payload 自报来源，然后校验 Team、participant 和 member placement。

`eventId` 全局去重；`eventSeq/teamVersion` 按 participant 分开比较，不能继续把多个 fragment 的版本当作同一 teamId 的单序列。成员消息投影到对应 Team robot，fragment 状态再聚合为全局状态。

RPC callback 入口不得同步进入 Mixed Team 状态机。MolaChat 按 participant transport 将事件复制到有界单线程队列后立即返回 callback，保证同一 participant 有序；队列满或组件关闭必须显式拒绝并告警，不能静默丢弃。cmd-proxy 的事件投递同样不得阻塞 create/delete/talkTo 命令响应。

owner 锁只保护全局记录的本地读取、校验和状态合并。`acpTeamCreate`、`acpTeamDelete`、失败补偿以及 `acpTeamTalkToDeliver` 等跨 RPC 调用必须在 owner 锁外执行，避免形成“协调端持锁等待命令响应、participant 等待 callback、callback 重入同一 owner 锁”的循环等待。

### 4.5 Delete

1. MolaChat 先把 MixedTeamRecord 持久化为 DELETING，立即拒绝 send/session/talkTo。
2. 对所有 participants 使用稳定 requestId 并行调用现有 `acpTeamDelete`。
3. 全部删除成功后置 DELETED并清理统一投影和全局记录。
4. 任一 participant 不可达时进入 PENDING_CLEANUP，保留 placement 并允许用户重试。

局部删除失败时不得宣告全局 DELETED，也不得丢弃 participant placement。

### 4.6 Starweave owner 协调桥

Starweave 使用 `starweave-{instanceId}` 作为独立 owner，不注册或冒充普通 MolaChat
用户。cmd-proxy 通过当前实例的 Team transport 发起来源、列表、创建、删除和成员命令；
MolaChat 必须从实际 callback 注册闭包取得 instanceId/transportGroup，并验证 owner 精确
等于该 transport 派生的 Starweave owner，不能信任请求自报身份。

来源列表可合并 home 实例的 Starweave 来源、明确共享给该 Starweave owner 的同实例
MolaChat 来源，以及其他 participant 发布的授权来源。mixed Team 仍必须至少包含一个 home
成员，所有成员命令按持久化 placement 路由。participant 事件先经过 mixed Team 与来源
transport 校验，再通过有界异步队列回投 home Starweave；协调器失联期间不得把本地
fragment 降级成可独立修改的普通 Team。

## 5. 最少代码范围

### 5.1 MolaChat

- `TeamGatewaySolution.java`：LOCAL/MIXED 分流、create/delete 补偿编排、成员 placement 路由。
- 新增 `MixedTeamStore.java`、`MixedTeamRecord.java`：封装现有 KeyValue JSON。
- `TeamEventSolution.java` 或独立 `MixedTeamEventSolution.java`：participant 事件校验、分片序列、全局状态聚合及 talkTo route。
- `TeamDTO.java`、`TeamMemberDTO.java`：内部 placement 与 participant 状态。
- `TeamCreateRequest.java`、`TeamCreateMemberRequest.java`：沿用实例/来源字段，增加后端 LOCAL/MIXED 规则校验，不接受前端自报 local。
- `TeamAcpExecSolution.java`：混选成员命令按 teamId/memberId 路由。
- `team.js`：允许跨 placement 多选；混选时强制本机 + remote；remote-only 禁止；隐藏 raw 路由标识。

HTTP 路径保持不变，不新增 remote 来源注册 API。测试集中扩展 TeamGateway、TeamEvent、TeamAcpExec 和创建校验测试。

### 5.2 cmd-proxy

- 扩展 `TeamCreateCommand/TeamDefinition/TeamManager` 支持 mixedPlacement 和 roster。
- 新增全队 contact DTO 和 `TeamTalkToDeliverCommand`。
- 扩展 `TeamTransportProtocol/Descriptor` capability 与新命令。
- 扩展 `TeamEventType/TeamTalkToContextInjector/TeamTalkToDispatcher` 支持 remote route。
- 注册并接入 `acpTeamTalkToDeliver`。

不新增第二套 AcpClient、TeamClientRegistry、TeamStore 或 FragmentStore。

## 6. 明确延后

- 专用全局 Store/CAS、多 MolaChat 节点选主和租约。
- prepare/commit/abort 两阶段创建与 reconcile 命令。
- fragmentId、instanceEpoch、coordinatorVersion、rosterDigest fencing。
- remote chatter 注册、pairing code、candidateRef 和来源管理 UI。
- participant 离线时其余成员继续工作；MVP 全队 RECOVERING。
- 自动后台 reaper、离线事件补拉和 eventSeq 缺口修复。
- talkTo 同步 ACK、跨重启 exactly-once 和全局 inbox。
- 动态成员、placement 迁移、自动 failover 和配对码式授权管理。

不能延后：全局 placement 持久化、首次 RPC 前落盘、幂等 fragment create、失败补偿、delete 屏障、事件来源校验、talkTo 目标二次校验以及 remote-only 后端拒绝。

## 7. 验收

1. 纯本机 `1~6` 人 Team 的 create/send/session/talkTo/delete/restart 保持 V1 行为。
2. 本机 1 人 + remote 1 人创建成功，各实例只启动自己的 member，全 fragment READY 后全局才 READY。
3. 本机成员 + 两个不同 remote 实例成员创建成功，所有 member 命令命中正确 transport，同名 robot 不串线。
4. remote-only、伪造 local、只选本机却请求 MIXED、篡改 instance/source route 均被后端拒绝。
5. 任一 fragment create 失败时，已接受 fragment 被补偿删除；无法清理时全局 PENDING_CLEANUP且不可 send。
6. MolaChat 在 CREATING 后重启，可从 KV 恢复 participants并继续聚合或补偿。
7. send/cancel/session/status/context/dream 按 teamId+memberId 精确路由；任一 participant 离线时全队 RECOVERING。
8. 本地 talkTo 直投；本机到 remote、remote 到本机、remote A 到 remote B 均经 MolaChat 投递到正确目标，BUSY 进入目标本地 inbox。
9. 伪造 sender/target、错误 callback transport、跨 Team target、超 TTL/depth 的 talkTo 被拒绝且不回退普通 crossTalkTo。
10. delete 对全部 participants 建立屏障；局部离线保持 PENDING_CLEANUP，重连重试后资源回到基线。
11. 不同 participant 的相同 eventSeq/teamVersion 不互相覆盖，消息和状态按 placement 正确聚合。
12. cmd-proxy 和 MolaChat 的 V1 Fast Team、普通 ACP、普通 talkTo/schedule/memory 回归通过。
13. create/delete/talkTo 的 callback 在命令响应前同步到达时，命令仍及时返回，事件最终按 transport 顺序应用，同 owner 的 home/list 不会因 callback 重入而失联。

## 8. 与旧方案的关系

- `fast-team-remote-acp-mvp-plan.md` 的“整队 remote / owner 单活 placement”方案已废弃，仅保留决策历史。
- `fast-team-cross-chatter-design.md` 仍作为完整 V2 增强设计参考。
- 本文是当前唯一实施基线：保留全局 placement、本地 fragment、失败补偿三项必要语义，同时延后旧 V2 的完整强一致和高可用机制。
