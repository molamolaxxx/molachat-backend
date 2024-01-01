package com.mola.molachat.session.task;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-04-30 10:33
 **/
@Configuration
@EnableScheduling
@Slf4j
public class SessionScheduleTask {

    @Autowired
    private SessionFactoryInterface sessionFactory;

    @Autowired
    private ChatterFactoryInterface chatterFactory;

    /**
     * 公共会话
     */
    public final static String COMMON_SESSION = "common-session";

    @Scheduled(fixedRate = 60000*10)
    private void clearSomeUselessChatterHistory() {
        log.info("开始清理群聊session中冗余chatter历史信息");
        Session session = sessionFactory.selectById(COMMON_SESSION);
        if (null == session) {
            return;
        }
        Set<Chatter> oldChatterSet = session.getChatterSet();
        synchronized (oldChatterSet) {
            Map<String, Chatter> oldChatterMap = new HashMap<>();
            Set<Chatter> newChatterSet = new HashSet<>();
            for (Chatter chatter : oldChatterSet) {
                if (chatter instanceof RobotChatter) {
                    newChatterSet.add(chatter);
                }
                oldChatterMap.put(chatter.getId(), chatter);
            }

            Set<String> ids = new HashSet<>();
            for (Message message : session.getMessageList()) {
                String chatterIdInMessage = message.getChatterId();
                // 首先从线上chatter中取
                Chatter chatterInMessage = chatterFactory.select(chatterIdInMessage);
                if (null == chatterInMessage) {
                    // 再从历史chatter中取
                    chatterInMessage = oldChatterMap.get(chatterIdInMessage);
                }
                // 如果还是null，则声明失效
                if (null == chatterInMessage) {
                    chatterInMessage = new Chatter();
                    chatterInMessage.setId(chatterIdInMessage);
                    chatterInMessage.setName("该用户已失效");
                    chatterInMessage.setImgUrl("img/mola.png");
                }
                newChatterSet.add(chatterInMessage);
            }

            session.setChatterSet(newChatterSet);
        }
    }

    /**
     * 清除chatter不存在的会话
     */
    @Scheduled(initialDelay = 30000*10, fixedRate = 60000*10)
    private void clearSessions() {
        // 1、获取所有chatter的id，聚合成set
        Set<String> chatterIdSet = chatterFactory.list().stream()
                .map(e -> e.getId())
                .collect(Collectors.toSet());

        // 2、遍历session
        for (Session session : sessionFactory.list()) {
            if (COMMON_SESSION.equals(session.getSessionId())) {
                continue;
            }
            // 会话中的chatter是否存在
            boolean isContains = true;
            for (Chatter chatter : session.getChatterSet()) {
                if (!chatterIdSet.contains(chatter.getId())) {
                    isContains = false;
                    break;
                }
            }
            // 不包含，直接删除会话
            if (!isContains) {
                sessionFactory.remove(session);
            }
        }
    }
}
