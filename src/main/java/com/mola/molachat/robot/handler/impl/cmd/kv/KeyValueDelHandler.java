package com.mola.molachat.robot.handler.impl.cmd.kv;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 18:49
 **/
@Component
public class KeyValueDelHandler extends BaseCmdRobotHandler {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public String getCommand() {
        return "kvdel";
    }

    @Override
    public String getDesc() {
        return "字典变量删除 'kvdel $k'";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            if (StringUtils.isBlank(baseEvent.getCommandInput())) {
                return "命令格式错误";
            }

            String key = baseEvent.getCommandInput();
            KeyValue keyValue = keyValueFactory.selectOne(key);
            if (Objects.isNull(keyValue)) {
                return "key不存在";
            }

            String chatterId = baseEvent.getMessageReceiveEvent().getMessage().getChatterId();
            Chatter chatter = chatterFactory.select(chatterId);
            if (!Objects.equals(keyValue.getOwner(), chatter.getId())) {
                return "无操作权限";
            }
            keyValueFactory.remove(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "删除key成功";
    }


    @Override
    public Integer order() {
        return 0;
    }
}
