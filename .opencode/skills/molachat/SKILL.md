---
name: molachat
description: "MolaChat 是一个基于 Spring Boot 的实时聊天应用，支持 WebSocket 消息、AI 机器人集成（ChatGPT、DeepSeek、MCP、ACP、Stable Diffusion）、WebRTC 视频通话、文件分享、群聊，以及多存储后端（Redis、LevelDB、内存缓存）。当需要修改 molachat 源码、调试聊天/机器人/会话/服务端问题、修改前端 JS 或理解项目架构时使用。"
---

# MolaChat 项目 Skill

Spring Boot 2.1.6 + Java 8 实时聊天应用。上下文路径：`/chat`，开发端口：`8550`。

## 关键约束

- JDK 8，不可使用高版本 API
- 包根路径：`com.mola.molachat`
- 子包：`chatter`、`session`、`server`、`robot`、`group`、`common`
- 存储后端：`leveldb`（默认）、`redis`、`cache` —— 通过 `self-conf.cache-type` 控制
- WebSocket：`tomcat`（默认）或 `spring`
- 前端：`src/main/resources/static/`，Thymeleaf 模板

## 常见开发任务

### 新增机器人类型
1. 创建 `XxxCreator` 实现 `RobotCreator`（通过 `appKey` 匹配）
2. 创建 `XxxEventBus` 继承 `RobotEventBus`
3. 实现 Handler，继承 `IRobotEventHandler`
4. 将 appKey 添加到 `app.robot-list`

### 新增命令
1. 继承 `BaseCmdRobotHandler`
2. 实现 `getCommand()`、`getDesc()`、`executeCommand()`
3. 注册到对应的 EventBus

### 新增 WebSocket Action
1. 在 `ActionCode` 枚举中添加编码
2. 创建 Handler 实现 `WSRequestActionHandler`
3. 注册到 `ActionStrategyContext`

### 切换存储后端
将 `self-conf.cache-type` 设为 `redis`、`leveldb` 或 `cache`，每种都有对应的条件化 Factory 实现。

## 构建与运行

```bash
./mvnw clean package -DskipTests
java -jar target/molachat-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## 参考文档

- `references/architecture.md` —— 详细架构、设计模式、机器人系统、WebSocket 协议、配置类
- `references/frontend.md` —— 前端目录结构与 JS 模块说明
