### 跨 Chatter Talk_To 方案文档（MolaChat 侧）

#### 背景

cmd-proxy 的 talk_to 机制目前仅支持同一 chatter（同一 cmd-proxy 实例）内的 robot 之间通信。现需支持跨 chatter 调用，MolaChat 作为中心网关负责消息路由。

#### RPC 架构约束

- MolaChat 是 **consumer**（使用 `CmdSender`），只能主动调用 cmd-proxy
- cmd-proxy 是 **provider**（使用 `CmdReceiver`），主动连接 MolaChat 注册自己
- 通信方向：MolaChat -> cmd-proxy 通过 `CmdSender.send(cmdName, cmdGroup, args)`
- cmd-proxy -> MolaChat 通过 `CmdReceiver.callback(cmdName, group, response)` 单向推送
- MolaChat 通过 `CmdSender.registerCallback(cmdName, group, lambda)` 接收推送

#### 架构总览

```
发送方 cmd-proxy                    MolaChat 网关                     接收方 cmd-proxy
     |                                  |                                  |
     |-- CmdReceiver.callback           |                                  |
     |   ('crossTalkTo', 'crossTalkTo', |                                  |
     |    response{resultMap})      ---->|                                  |
     |                                  |-- registerCallback               |
     |                                  |   ('crossTalkTo','crossTalkTo')  |
     |                                  |   收到请求                        |
     |                                  |-- 校验 + 算 targetGroupId         |
     |                                  |                                  |
     |                                  |-- CmdSender.send                 |
     |                                  |   ('crossTalkToDeliver',          |
     |                                  |    targetGroupId, args)      ---->|
     |                                  |                                  |-- CmdReceiver 注册的
     |                                  |                                  |   'crossTalkToDeliver'
     |                                  |                                  |   handler 处理
     |                                  |                                  |-- 投递到 inbox
     |   (火烧即忘，无同步返回)          |                                  |
```

#### cmd-proxy 侧注册说明

发送方 cmd-proxy 需要：
1. `CmdReceiver.register("crossTalkTo", "crossTalkTo", dummyHandler)` — 建立 callback 通道连接，handler 可为空
2. `CmdReceiver.register("crossTalkToDeliver", cmdGroupList, realHandler)` — 接收 MolaChat 转发的消息

两个方向用不同 cmdName 区分：
- cmd-proxy -> MolaChat（callback 通道）：cmdName=`"crossTalkTo"`, group=`"crossTalkTo"`
- MolaChat -> cmd-proxy（send 通道）：cmdName=`"crossTalkToDeliver"`, group=targetGroupId

#### 投递结果策略：火烧即忘（方案 B）

发送方 cmd-proxy 通过 `CmdReceiver.callback` 单向推送，不等待返回值。

理由：
1. talk_to 本身是异步语义，LLM 不需要同步知道投递结果
2. 简化实现，减少一次往返
3. 发送方统一告诉 LLM "消息已提交网关，等待投递"
4. 严重错误（目标不存在等）在 MolaChat 侧记录日志

#### 一、命令定义

##### 1.1 回调命令：crossTalkTo（cmd-proxy -> MolaChat）

发送方 cmd-proxy 通过 `CmdReceiver.callback` 推送到 MolaChat。

回调 response 的 resultMap：

| key | 类型 | 说明 |
|-----|------|------|
| targetChatterId | String | 目标 chatter ID |
| targetRobotName | String | 目标 robot 名称 |
| senderChatterId | String | 发送方 chatter ID |
| senderRobotName | String | 发送方 robot 名称 |
| content | String | 消息内容 |
| depth | String | 递归深度（字符串形式的数字） |

回调 group：固定值 `"crossTalkTo"`（已确认精确匹配机制）。

##### 1.2 转发命令：crossTalkToDeliver（MolaChat -> 接收方 cmd-proxy）

MolaChat 通过 `CmdSender.send` 调用目标 cmd-proxy。

参数（String[] args，单元素 JSON）：

```json
{
  "senderChatterId": "发送方 chatter ID",
  "senderRobotName": "发送方 robot 名称",
  "content": "消息内容",
  "depth": 3
}
```

路由 key（cmdGroup）：目标 groupId（MolaChat 计算得出）。

#### 二、MolaChat 侧实现

##### 2.1 新增常量

在 `CmdProxyConstant.java` 中新增：

```java
public static final String CROSS_TALK_TO = "crossTalkTo";
public static final String CROSS_TALK_TO_DELIVER = "crossTalkToDeliver";
```

##### 2.2 新增 Solution 类

新建 `CrossTalkToSolution.java`，放在 `com.mola.molachat.robot.solution` 包下：

```java
@Service
@Slf4j
public class CrossTalkToSolution implements InitializingBean {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        registerCrossTalkToCallback();
    }

    private void registerCrossTalkToCallback() {
        CmdSender.INSTANCE.registerCallback(
            CmdProxyConstant.CROSS_TALK_TO,
            CmdProxyConstant.CROSS_TALK_TO,
            (res) -> {
                try {
                    String targetChatterId = res.getResultMap().get("targetChatterId");
                    String targetRobotName = res.getResultMap().get("targetRobotName");
                    String senderChatterId = res.getResultMap().get("senderChatterId");
                    String senderRobotName = res.getResultMap().get("senderRobotName");
                    String content = res.getResultMap().get("content");
                    int depth = Integer.parseInt(
                        res.getResultMap().getOrDefault("depth", "1"));

                    route(targetChatterId, targetRobotName,
                          senderChatterId, senderRobotName,
                          content, depth);
                } catch (Exception e) {
                    log.error("crossTalkTo处理失败", e);
                }
                return Unit.INSTANCE;
            }
        );
    }
}
```

##### 2.3 路由逻辑

```java
private void route(String targetChatterId, String targetRobotName,
                   String senderChatterId, String senderRobotName,
                   String content, int depth) {
    // 1. 校验 depth
    if (depth > 5) {
        log.warn("crossTalkTo depth exceeded: sender={}:{}, target={}:{}, depth={}",
            senderChatterId, senderRobotName, targetChatterId, targetRobotName, depth)   return;
    }

    // 2. 校验目标 chatter 存在
    Chatter targetChatter = chatterFactory.select(targetChatterId);
    if (targetChatter == null) {
        log.warn("crossTalkTo target chatter not found: {}", targetChatterId);
        return;
    }

    // 3. 校验目标 robot 存在
    String targetRobotId = computeAcpId(targetRobotName);
    Chatter targetRobot = chatterFactory.select(targetRobotId);
    if (targetRobot == null) {
        log.warn("crossTalkTo target robot not found: {} (acpId={})",
            targetRobotName, targetRobotId);
        return;
    }

    // 4. 计算目标 groupId
    String targetGroupId = computeGroupId(targetChatterId, targetRobotId);

    // 5. 转发到目标 cmd-proxy
    JSONObject deliverParam = new JSONObject();
    deliverParam.put("senderChatterId", senderChatterId);
    deliverParam.put("senderRobotName", senderRobotName);
    deliverParam.put("content", content);
    deliverParam.put("depth", depth);

    CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.send(
        CmdProxyConstant.CROSS_TALK_TO_DELIVER,
        targetGroupId,
        new String[]{deliverParam.toJSONString()}
    );

    // 6. 记录结果日志
    if (response == null || response.getData() == null) {
        log.warn("crossTalkTo deliver failed, target offline: chatterId={}, groupId={}",
            targetChatterId, targetGroupId);
    } else {
        log.info("crossTalkTo delivered: {}:{} -> {}:{}",
            senderChatterId, senderRobotName, targetChatterId, targetRobotName);
    }
}

/**
 * 计算 ACP robot 的 chatterId
 * 规则：'acp-' + robotName.replace(' ', '_').replace('\u3000', '_')
 */
private String computeAcpId(String robotName) {
    return "acp-" + robotName.replace(" ", "_").replace("\u3000", "_");
}

/**
 * 计算 groupId（与 cmd-proxy 侧一致）
 * 规则：字典序排序后直接拼接，无分隔符
 */
private String computeGroupId(String chatterId, String robotId) {
    List<String> ids = Arrays.asList(chatterId, robotId);
    Collections.sort(ids);
    return String.join("", ids);
}
```

#### 三、registerCallback 的 group 匹配机制（已确认）

匹配规则：`registerCallback(cmdName, cmdGroup, lambda)` 以 `"$cmdName$cmdGroup"` 为 key 精确匹配。

已确认方案：
- cmd-proxy 侧：`CmdReceiver.register("crossTalkTo", "crossTalkTo", dummyHandler)` 建立连接
- cmd-proxy 发送：`CmdReceiver.callback("crossTalkTo", "crossTalkTo", response)` 推送请求
- MolaChat 接收：`CmdSender.registerCallback("crossTalkTo", "crossTalkTo", lambda)` 接收所有 cmd-proxy 的跨 chatter 请求
- 发送方身份通过 resultMap 中的 senderChatterId + senderRobotName 传递

#### 四、实现步骤

1. ~~确认待确认事项~~ 已确认
2. ~~确认 registerCallback group 匹配机制~~ 已确认（精确匹配，用固定 group "crossTalkTo"）
3. 新增 `CmdProxyConstant` 常量
4. 新增 `CrossTalkToSolution`，注册回调处理器
5. 联调：与 cmd-proxy 侧联合测试

#### 五、安全考虑

- depth 硬限制为 5，防止无限循环
- 校验目标 chatter 和 robot 的存在性
- 全角空格替换：`\u3000` -> `_`
- 日志记录所有跨 chatter 投递（发送方、接收方、时间戳、成功/失败），便于审计
- 未来可扩展：chatter 间通信白名单/黑名单
