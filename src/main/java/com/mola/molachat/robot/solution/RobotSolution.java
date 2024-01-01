package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.action.FileMessageSendAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.RobotEventBus;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.SessionSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
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
    private SessionSolution sessionSolution;

    @Resource
    private ChatterService chatterService;


    @Resource
    private RobotEventBus robotEventBus;

    @Resource
    private ApplicationContext applicationContext;

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
        sessionSolution.insertMessage(session.getSessionId(), msg);
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
                return;
            }
            MessageReceiveEvent messageReceiveEvent = new MessageReceiveEvent();
            messageReceiveEvent.setMessage(message);
            messageReceiveEvent.setRobotChatter(robot);
            messageReceiveEvent.setSessionId(sessionId);
            // 先取自定义的eventbus，没有就用默认的
            RobotEventBus eventBus = robotEventBus;
            if (StringUtils.isNotBlank(robot.getEventBusBeanName())) {
                RobotEventBus customEventBus = applicationContext.getBean(robot.getEventBusBeanName(), RobotEventBus.class);
                if (null != customEventBus)  {
                    eventBus =  customEventBus;
                } else {
                    log.error("未找到自定义eventbus:{}, 使用默认eventbus", robot.getEventBusBeanName());
                }
            }
            BaseAction action = eventBus.handler(messageReceiveEvent);
            Message messageByAction = getMessageByAction(action, sessionId);
            if (null == messageByAction) {
                return;
            }
            // 1、查询session，没有则创建
            SessionDTO session = sessionService.findSession(sessionId);
            // 2、向session发送消息
            sessionSolution.insertMessage(session.getSessionId(), messageByAction);
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
                msg.setChatterId(robot.getId());
                msg.setSessionId(sessionId);
                return msg;
            }
            return null;
        }
    }
}
