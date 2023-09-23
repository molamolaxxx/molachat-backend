package com.mola.molachat.robot.handler.impl.cmd;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.data.LevelDBClient;
import com.mola.molachat.data.OtherDataInterface;
import com.mola.molachat.data.SessionFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Session;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
public class Transport2LevelDBHandler extends BaseCmdRobotHandler {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private OtherDataInterface otherDataInterface;

    @Resource
    private LevelDBClient levelDBClient;

    @Override
    public String getCommand() {
        return "transport2LevelDb";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            // chatter
            for (Chatter chatter : chatterFactory.list()) {
                levelDBClient.put("chatter:" + chatter.getId(), JSONObject.toJSONString(chatter));
            }
            // session
            for (Session session : sessionFactory.list()) {
                levelDBClient.put("session:" + session.getSessionId(), JSONObject.toJSONString(session));
            }

            // otherdata
            levelDBClient.put("gpt3ChildTokens", JSONObject.toJSONString(otherDataInterface.getGpt3ChildTokens()));
        } catch (Exception e) {
            return "执行失败, " + e.getMessage();
        }
        return "执行成功";
    }

    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public String getDesc() {
        return "redis迁移到leveldb";
    }
}
