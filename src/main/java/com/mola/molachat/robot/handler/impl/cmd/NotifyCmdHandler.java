package com.mola.molachat.robot.handler.impl.cmd;

import com.google.common.collect.Sets;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.SessionSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-04-17 14:40
 **/
@Component
@Slf4j
public class NotifyCmdHandler extends BaseCmdRobotHandler {

    @Resource
    private SessionSolution sessionSolution;

    private final Set<NotifyTask> notifyTaskSet = Sets.newConcurrentHashSet();

    @PostConstruct
    public void startTaskRunner() {
        NotifyTaskRunner runner = new NotifyTaskRunner();
        runner.start();
    }

    @Override
    public String getCommand() {
        return "notify";
    }

    @Override
    public String getDesc() {
        return "定时提醒，使用方式: notify 提醒内容 1m(延时 支持s/m/h) 1(次数，可省略), 举例：notify 下班 30m";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            Message message = baseEvent.getMessageReceiveEvent().getMessage();
            NotifyTask notifyTask = NotifyTask.buildFromCmd(
                    baseEvent.getCommandInput(),
                    baseEvent.getMessageReceiveEvent().getRobotChatter().getId(),
                    message.getSessionId()
            );
            if (notifyTask == null) {
                return "任务生成失败，格式错误";
            }
            if (StringUtils.isNotBlank(notifyTask.validate())) {
                return notifyTask.validate();
            }
            notifyTaskSet.add(notifyTask);
        } catch (Exception e) {
            log.error("NotifyCmdHandler 提醒任务生成异常, event = {}", baseEvent, e);
            return "任务生成异常";
        }

        return "任务生成成功";
    }

    public class NotifyTaskRunner extends Thread {

        @Override
        public void run() {
            while (true) {
                for (NotifyTask notifyTask : notifyTaskSet) {
                    try {
                        // 判断是否到时间
                        if (System.currentTimeMillis() - notifyTask.lastNotifyTime <
                                notifyTask.timeUnit.toMillis(notifyTask.delayTime)) {
                            continue;
                        }

                        // 发送提醒
                        if (notifyTask.count > 1) {
                            notifyTask.count = notifyTask.count - 1;
                            notifyTask.lastNotifyTime = System.currentTimeMillis();
                        } else {
                            notifyTaskSet.remove(notifyTask);
                        }
                        sendNotify(notifyTask);
                    } catch (Exception e) {
                        log.error("NotifyCmdHandler error in loop, remove", e);
                        notifyTaskSet.remove(notifyTask);
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
            }
        }

        public void sendNotify(NotifyTask notifyTask) {
            Message msg = new Message();
            msg.setContent("【提醒】" + notifyTask.content);
            msg.setChatterId(notifyTask.robotId);
            msg.setSessionId(notifyTask.sessionId);
            sessionSolution.insertMessage(notifyTask.sessionId, msg);
        }
    }

    public static class NotifyTask {

        private static final Map<String, TimeUnit> SUPPORT_TIME_UNIT = new HashMap<>();
        static {
            SUPPORT_TIME_UNIT.put("s", TimeUnit.SECONDS);
            SUPPORT_TIME_UNIT.put("m", TimeUnit.MINUTES);
            SUPPORT_TIME_UNIT.put("h", TimeUnit.HOURS);
        }
        private String id;
        private String content;
        private int delayTime;
        private TimeUnit timeUnit;
        private int count;
        private long lastNotifyTime;
        private String robotId;
        private String sessionId;

        public static NotifyTask buildFromCmd(String cmd, String robotId, String sessionId) {
            String[] splitRes = StringUtils.split(cmd, " ");
            if (splitRes == null || splitRes.length < 2 || splitRes.length > 3) {
                return null;
            }
            NotifyTask notifyTask = new NotifyTask();
            notifyTask.content = splitRes[0];
            boolean fillRes = notifyTask.fillDelayTimeInfo(splitRes[1]);
            if (!fillRes) {
                return null;
            }
            if (splitRes.length == 3) {
                notifyTask.count = Integer.parseInt(splitRes[2]);
            } else {
                notifyTask.count = 1;
            }
            notifyTask.id = UUID.randomUUID().toString();
            notifyTask.lastNotifyTime = System.currentTimeMillis();
            notifyTask.robotId = robotId;
            notifyTask.sessionId = sessionId;
            return notifyTask;
        }

        public String validate() {
            if (count > 100) {
                return "提醒次数太频繁";
            }
            return "";
        }

        private boolean fillDelayTimeInfo(String timeDesc) {
            if (StringUtils.isBlank(timeDesc)) {
                return false;
            }
            for (String unit : SUPPORT_TIME_UNIT.keySet()) {
                if (timeDesc.endsWith(unit)) {
                    this.timeUnit = SUPPORT_TIME_UNIT.get(unit);
                    try {
                        this.delayTime = Integer.parseInt(timeDesc.substring(0, timeDesc.length() - 1));
                    } catch (Exception e) {
                        log.error("NotifyCmdHandler 时间解析失败", e);
                        return false;
                    }
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Integer order() {
        return Integer.MAX_VALUE;
    }
}
