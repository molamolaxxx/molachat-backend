package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.common.utils.MapUtils;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.model.ResponseCode;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.robot.action.FileMessageSendAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.RobotEventBus;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-01-01 17:31
 **/
@Component
@Slf4j
public class RobotSolution implements InitializingBean {


    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SessionService sessionService;


    @Resource
    private MessageSolution messageSolution;

    @Resource
    private ChatterService chatterService;


    @Resource
    private RobotEventBus robotEventBus;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private OcrSolution ocrSolution;

    /**
     * 业务线程池
     */
    private ThreadPoolExecutor bizProcessThreadPool;

    /**
     * 阻塞队列
     */
    private BlockingQueue<Runnable> blockingQueue;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.blockingQueue = new LinkedBlockingDeque<>(1024);
        this.bizProcessThreadPool = new ThreadPoolExecutor(5,20
                ,3000, TimeUnit.MILLISECONDS, blockingQueue, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("robot-service-thread-%d", this.threadIndex.incrementAndGet()));
            }
        });
    }

    public void onReceiveMessage(Message message, String sessionId, RobotChatter robot) {
        OnReceiveMessageRunnableTask task = new OnReceiveMessageRunnableTask(message, sessionId, robot);
        if (blockingQueue.size() > 1000) {
            task.run();
            return;
        }
        if (ChatterStatusEnum.OFFLINE.getCode().equals(robot.getStatus())) {
            log.info("机器人已经下线, message = " + JSONObject.toJSONString(message));
            // 将消息存入消息队列
            chatterService.offerMessageIntoQueue(message, robot.getId());
            return;
        }
        bizProcessThreadPool.submit(task);
    }

    public Boolean isRobot(String chatterId) {
        if (StringUtils.isEmpty(chatterId)) {
            return false;
        }
        final Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter) {
            return false;
        }
        return chatter instanceof RobotChatter;
    }

    public RobotChatter getRobot(String appKey) {
        if (StringUtils.isEmpty(appKey)) {
            return null;
        }
        final Chatter chatter = chatterFactory.select(appKey);
        if (!(chatter instanceof RobotChatter)) {
            return null;
        }
        return (RobotChatter) chatter;
    }


    public void pushMessage(String appKey, String toChatterId, String content) {
        // 查询发送方
        RobotChatter robot = this.getRobot(appKey);
        Assert.notNull(robot, "sender is not exist");
        // 查询接收方
        ChatterDTO receiver = chatterService.selectById(toChatterId);
        Assert.notNull(receiver, "receiver is not exist");
        // 消息构建
        Message msg = new Message();
        msg.setContent(content);
        msg.setChatterId(robot.getId());

        // 1、查询session，没有则创建
        SessionDTO session = sessionService.findOrCreateSession(appKey, toChatterId);
        msg.setSessionId(session.getSessionId());
        // 2、向session发送消息
        messageSolution.insertMessage(session.getSessionId(), msg);
    }

    /**
     * 获取ACP上下文用量百分比，非ACP robot返回null
     */
    public Double fetchContextUsage(String sessionId) {
        log.info("fetchContextUsage start, sessionId={}", sessionId);
        SessionDTO session = sessionService.findSession(sessionId);
        if (session == null) {
            log.warn("fetchContextUsage session not found, sessionId={}", sessionId);
            return null;
        }
        RobotChatter robot = null;
        for (Chatter chatter : session.getChatterSet()) {
            Chatter real = chatterFactory.select(chatter.getId());
            if (real instanceof RobotChatter) {
                robot = (RobotChatter) real;
                break;
            }
        }
        if (robot == null || !"acp".equals(robot.getRobotGroup())) {
            log.info("fetchContextUsage skip, robot={}, robotGroup={}, sessionId={}",
                    robot, robot != null ? robot.getRobotGroup() : "null", sessionId);
            return null;
        }
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            String paramJson = JSONObject.toJSONString(paramMap);
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send("acpGetContextUsage", sessionId, new String[]{paramJson});
            log.info("fetchContextUsage response, sessionId={}, response={}", sessionId, JSON.toJSON(response));
            if (response != null && response.getData() != null) {
                String result = response.getData().getResultMap().get("result");
                if (result != null) {
                    double pct = Double.parseDouble(result);
                    return pct >= 0 ? pct : null;
                }
            }
        } catch (Exception e) {
            log.error("fetchContextUsage 获取上下文用量失败, sessionId={}", sessionId, e);
        }
        return null;
    }

    public String fetchCmdMarkdown(String robotId, String sessionId) {
        Chatter robot = chatterFactory.select(robotId);
        if (!(robot instanceof RobotChatter)) {
            throw new IllegalArgumentException("robotId is not robot's id, " + robotId);
        }
        RobotChatter robotChatter = (RobotChatter) robot;
        // 先取自定义的eventbus，没有就用默认的
        RobotEventBus eventBus = robotEventBus;
        if (StringUtils.isNotBlank(robotChatter.getEventBusBeanName())) {
            eventBus = applicationContext.getBean(robotChatter.getEventBusBeanName(), RobotEventBus.class);
        }
        // 获取对应的命令描述
        List<CmdDescription> allCmdDescriptions = eventBus.getAllCmdDescriptions(robotId, sessionId);
        // 远程指令
        Map<String, String> remoteCmdDescMap = CmdSender.INSTANCE.fetchDescriptionMap(sessionId);
        if (MapUtils.isNotEmpty(remoteCmdDescMap)) {
            remoteCmdDescMap.forEach((cmd, desc) -> {
                String[] split = desc.split("#script:");
                if (split.length != 2) {
                    return;
                }
                allCmdDescriptions.add(
                        CmdDescription.builder()
                                .cmdName(cmd)
                                .cmdDesc(split[0])
                                .executeScript(split[1]).build()
                );
            });
        }
        if (CollectionUtils.isEmpty(allCmdDescriptions)) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        result.append("| 命令   | 描述   | 操作                                         |\n")
                .append("| ----- | ----- | -------------------------------------------- |\n");
        for (CmdDescription allCmdDescription : allCmdDescriptions) {
            result.append(allCmdDescription.renderLine());
            result.append("\n");
        }
        return result.toString();
    }

    class OnReceiveMessageRunnableTask implements Runnable {
        private Message message;
        private String sessionId;
        private RobotChatter robot;

        public OnReceiveMessageRunnableTask(Message message, String sessionId, RobotChatter robot) {
            this.message = message;
            this.sessionId = sessionId;
            this.robot = robot;
        }

        @Override
        public void run() {
            if (null != message && message.getChatterId().equals(robot.getId())) {
                return;
            }
            if ("common-session".equals(sessionId)) {
                return;
            }
            if (message instanceof FileMessage) {
                FileMessage fm = (FileMessage) message;
                ServerResponse<String> result = ocrSolution.ocr(fm);
                if (result.getStatus() == ResponseCode.SUCCESS.getCode()) {
                    FileMessage toUpdate = new FileMessage();
                    toUpdate.setId(fm.getId());
                    toUpdate.setOcrResultCache(result.getData());
                    messageSolution.updateMessage(sessionId, toUpdate);
                }
                return;
            }
            MessageReceiveEvent messageReceiveEvent = new MessageReceiveEvent();
            messageReceiveEvent.setMessage(message);
            messageReceiveEvent.setRobotChatter(robot);
            messageReceiveEvent.setSessionId(sessionId);
            // 先取自定义的eventbus，没有就用默认的
            RobotEventBus eventBus = robotEventBus;
            if (StringUtils.isNotBlank(robot.getEventBusBeanName())) {
                eventBus = applicationContext.getBean(robot.getEventBusBeanName(), RobotEventBus.class);
            }
            BaseAction action = eventBus.handler(messageReceiveEvent);
            Message messageByAction = getMessageByAction(action, sessionId);
            if (null == messageByAction) {
                return;
            }
            // 1、查询session，没有则创建
            SessionDTO session = sessionService.findSession(sessionId);
            // 2、向session发送消息
            messageSolution.insertMessage(session.getSessionId(), messageByAction);
        }

        private Message getMessageByAction(BaseAction action, String sessionId) {
            // 文件消息构建
            if (action instanceof FileMessageSendAction) {
                //创建message
                FileMessageSendAction fileMessageSendAction = (FileMessageSendAction) action;
                FileMessage fileMessage = new FileMessage();
                fileMessage.setFileName(fileMessageSendAction.getFileName());
                fileMessage.setFileStorage("1024");
                fileMessage.setUrl(fileMessageSendAction.getUrl());
                fileMessage.setSnapshotUrl(fileMessageSendAction.getUrl());
                fileMessage.setSessionId(sessionId);
                fileMessage.setChatterId(robot.getId());
                // 判断是否是群聊
//                if (sessionId.equals("common-session")) {
//                    fileMessage.setCommon(true);
//                }
                return fileMessage;
            }
            // 普通消息构建
            if (action instanceof MessageSendAction) {
                Message msg = new Message();
                msg.setContent(((MessageSendAction)action).getResponsesText());
                if (StringUtils.isEmpty(msg.getContent())) {
                    return null;
                }
                msg.setChatterId(robot.getId());
                msg.setSessionId(sessionId);
                return msg;
            }
            return null;
        }
    }
}
