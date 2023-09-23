package com.mola.molachat.robot.handler.impl.cmd.kv;

import com.mola.molachat.data.KeyValueFactoryInterface;
import com.mola.molachat.entity.KeyValue;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 18:49
 **/
@Component
public class KeyValueListHandler extends BaseCmdRobotHandler {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Override
    public String getCommand() {
        return "kvlist";
    }

    @Override
    public String getDesc() {
        return "字典列表查看 'kvlist'";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            List<KeyValue> list = keyValueFactory.list();
            if (CollectionUtils.isEmpty(list)) {
                return "kv列表为空";
            }
            return String.join("\n", list.stream().map(KeyValue::toString)
                    .toArray(CharSequence[]::new));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Integer order() {
        return 0;
    }
}
