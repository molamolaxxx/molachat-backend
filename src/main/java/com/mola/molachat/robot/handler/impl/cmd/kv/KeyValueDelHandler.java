package com.mola.molachat.robot.handler.impl.cmd.kv;

import com.mola.molachat.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

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
