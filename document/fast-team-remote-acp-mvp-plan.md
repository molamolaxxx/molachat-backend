# Fast Team 选择 Remote ACP 实例 MVP 方案（已废弃）

> 状态：已废弃，不得作为实施基线
>
> 废弃原因：该方案只支持把整支 Team placement 到单个 remote 实例，不能满足用户明确要求的“本机 ACP + 多个 remote cmd-proxy ACP 混选”，并错误允许 remote-only 语义。
>
> 当前唯一基线：[`fast-team-remote-mixed-mvp.md`](fast-team-remote-mixed-mvp.md)

> 本文以下内容仅保留决策历史；其中 owner 单活 placement、整队 remote、cmd-proxy 零改动等结论全部撤回。

## 1. MVP 结论

本次不实现“一支 Team 的成员分布在多个 cmd-proxy”。

一支 Team 的所有成员必须来自同一个 `cmdProxyInstanceId + transportGroup`。这个实例可以是本机，也可以是 remote 实例；Team 定义、成员进程、session、history、talkTo、事件和删除仍全部由该实例现有 Fast Team runtime 承载。

同一个 owner 在任一时刻只允许一个 Fast Team placement：该 owner 的所有 Team 都由同一个 cmd-proxy 承载。owner 尚无 Team 时可以选择任一本机或 remote 实例；已有 Team 未全部删除前只能继续选择该实例；全部删除后可以重新选择并覆盖 owner binding。

这能直接复用当前 V1 的单实例 Team 权威和命令协议，不引入全局 Team Store、fragment、saga、instanceEpoch、跨实例 talkTo 网关或多实例 Team 状态聚合。

## 2. 最新代码已有基础

MolaChat 当前已经具备：

1. 从多个 `acpSyncRobots` 回调按 `cmdProxyInstanceId` 保存 discovery。
2. `GET /team/candidates` 汇总多个实例对当前 owner 可见的 `teamMemberSources`。
3. 候选携带内部 `cmdProxyInstanceId/transportGroup/sourceRobotId/sourceGroupId`，创建时按最新 discovery 重新校验。
4. 创建请求按所选 transport 调用现有 `acpTeamCreate`。
5. 前后端均拒绝同一 Team 混合 placement。
6. 当前工作区已将 Team 成员数放宽为 `1~6`，因此仅有一个 remote ACP 的实例也可以形成独立 Team。

当前缺口不是 remote 候选发现或 remote create，而是创建前后的 owner placement 门禁：create 已能发往用户选择的 remote transport，但没有先确认该 owner 是否已在其他实例保存 Team，成功后也没有把现有 owner binding 更新为所选实例。这可能导致后续 list/get/send/delete 仍命中旧实例。

## 3. 最小实现

### 3.1 MolaChat 后端

集中修改 `TeamGatewaySolution`：

1. 在现有 `ownerResolutionLocks` 内串行完成“探测已有 placement -> 校验用户选择 -> `acpTeamCreate` -> 成功后 bind”，避免两个并发 create 分别落到不同实例。
2. create 前对当前 owner 可见、active、ready 且支持 `acpTeamList` 的实例做探测：0 个实例有 Team 时允许任意选择；1 个实例有 Team 时只能选择该实例；多于 1 个实例有 Team 时继续 fail closed。
3. 选择与现有 placement 不同的实例时复用现有 `CMD_PROXY_INSTANCE_CONFLICT`（HTTP 409）并返回明确中文提示；创建失败不修改 binding，不为一个错误码扩展 Controller/API。
4. create 被 remote transport 接受后调用现有 `bind(ownerChatterId, selectedRegistration)`，使后续 list/get/send/delete 继续复用现有 owner 单路由。
5. owner binding 同时是离线保护锁：若绑定实例 stale、unreachable 或尚未恢复 discovery，不能因“探测不到 Team”而允许在其他实例创建，必须返回 `TEAM_NOT_READY` 并要求先恢复旧实例。
6. 只有绑定实例的 `acpTeamList` 成功返回空，或最后一支 Team delete 已成功进入终态且本地投影确认无其他 Team 时，才能清除 owner binding。删除后的空列表确认失败则保留 binding，宁可暂时不能切换，也不能形成双 placement。
7. 全实例 placement 探测期间，任一可能承载该 owner 旧 Team 的已知实例不可达时整体 fail closed；不能只依据其余成功实例得出“0 个或 1 个实例有 Team”的结论。
8. 保留 `MIXED_TEAM_PLACEMENT` 和来源二次校验，不信任前端提交的实例、transport 或 source 字段。

不新增持久化模型、HTTP API 或 Team 命令路由；继续复用现有 `fast-team.instance-binding.{ownerChatterId}` KeyValue 和 `resolveRegistration` 恢复逻辑。

### 3.2 MolaChat 前端

集中修改 `team.js`：

1. 第一次勾选候选后，临时禁用其他 placement 的候选；全部取消后恢复，避免最后提交时才提示冲突。
2. 保留 `1~6` 成员校验。
3. 不展示 `cmdProxyInstanceId`、`transportGroup` 等技术标识；候选仍按现有名称、头像、备注展示。
4. placement 冲突使用简短中文提示：“当前已有 Team 位于另一运行实例，请先删除现有 Team 后再切换”；旧 placement 不可用时提示先恢复原运行实例；均不展示内部实例标识串。

### 3.3 cmd-proxy

预期不修改业务协议和 runtime。继续复用现有：

- 实例级稳定 `cmdProxyInstanceId/transportGroup` discovery；
- `teamMemberSources` 的 owner 可见性和来源快照；
- `acpTeamCreate/List/Get/Delete/Send/...`；
- Team event、talkTo、重启恢复和资源清理。

cmd-proxy 侧只需确认：remote 部署对同一 MolaChat owner 发布的 `visibleChatterIds` 与每个 `teamMemberSource.ownerChatterId` 一致，并验证完整回归。

## 4. 明确不做

- 同一 Team 混选本机和 remote 实例成员。
- 同一 owner 同时在多个实例保存不同 Team。
- remote chatter 注册、配对码、来源 Tab 和授权管理页面。
- MolaChat 全局 TeamDefinition、fragment、两阶段创建/删除或 saga 补偿。
- 跨实例 Team talkTo、事件聚合和部分实例故障降级。
- teamId 到实例的持久路由、跨实例 Team list 聚合。
- 运行中的 Team placement 迁移或自动 failover；切换实例需先删除该 owner 的全部 Team。

## 5. 验收

### 自动测试

1. 无 Team/绑定时，同时发现 local 和 remote，候选都可返回。
2. 无现有 Team 时选择 remote 的 1 个成员创建成功，命令只发往 remote transport，成功后 owner binding 更新为 remote。
3. remote 已有 Team 时允许继续在 remote 创建；local 已有 Team 时选择 remote 返回 `CMD_PROXY_INSTANCE_CONFLICT` 和明确中文提示，且不发送 create。
4. 同一创建请求混合 local/remote 成员，前后端均拒绝。
5. 创建失败不改 owner binding；重试仍按实际已有 Team 判定 placement。
6. 两个实例都已存在该 owner 的历史 Team 时返回 `CMD_PROXY_INSTANCE_CONFLICT`，不聚合、不覆盖。
7. 两个并发、不同 placement 的 create 最多一个成功。
8. 最后一支 Team 删除终态且确认无其他 Team 时清除 binding，下次 create 可以从 local 重绑到 remote；删除后对账不确定时保留 binding。
9. MolaChat 重启后，通过唯一保存 Team 的实例恢复 owner binding，remote Team 可继续发送和删除。
10. owner binding 指向 stale/unreachable remote 时禁止在 local 创建；remote 恢复并成功返回空列表，或最后一支 Team 删除终态得到可信确认后，才允许清 binding 和切换。
11. 未绑定场景的全实例探测只要存在不可达的已知实例，也禁止创建；全部相关实例完成权威对账后才执行 0/1/>1 判定。

### 双端实测

1. local 与 remote cmd-proxy 同时在线，页面可选择 remote ACP 创建单成员 Team。
2. remote Team 可发送消息、切换 session、执行 team talkTo（成员数大于 1 时）、删除。
3. remote cmd-proxy 重启后 Team 恢复，MolaChat 不误路由到 local。
4. remote 离线时候选/Team 显示不可用，local 普通 ACP 不受影响。
5. 删除 remote 实例上的最后一支 Team 后，再次创建时可以选择 local；cmd-proxy 资源清理行为保持现有契约。

## 6. 实施顺序

1. 双方确认 MVP 边界和 cmd-proxy 无协议改动。
2. 先补 `TeamGatewaySolutionTest` 的 placement 探测、锁定、重绑和并发 create 用例，再改后端。
3. 修改 `team.js` 的同 placement 勾选门禁并做浏览器实测。
4. 执行 MolaChat 定向测试、cmd-proxy Fast Team 回归和双端重启联调。
5. 验收通过后再同步部署静态资源。

预计业务代码改动文件只有：

- `src/main/java/com/mola/molachat/team/solution/TeamGatewaySolution.java`
- `src/main/resources/static/js/chat/team.js`
- `src/test/java/com/mola/molachat/team/solution/TeamGatewaySolutionTest.java`
