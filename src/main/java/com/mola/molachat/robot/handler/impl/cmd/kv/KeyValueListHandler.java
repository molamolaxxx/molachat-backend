package com.mola.molachat.robot.handler.impl.cmd.kv;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

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

    @Resource
    private ChatterFactoryInterface chatterFactory;

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
            String chatterId = baseEvent.getMessageReceiveEvent().getMessage().getChatterId();
            Chatter chatter = chatterFactory.select(chatterId);
            Assert.notNull(chatter, "chatter is null");
            String result = String.join("\n", list.stream()
                    .filter(kv -> kv.isShare() || Objects.equals(chatter.getId(), kv.getOwner())).map(KeyValue::toString)
                    .toArray(CharSequence[]::new));
            return StringUtils.isBlank(result) ? "无可查看的kv列表" : result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Integer order() {
        return 0;
    }
}
