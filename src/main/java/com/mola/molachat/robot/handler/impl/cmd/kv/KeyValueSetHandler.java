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

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 18:49
 **/
@Component
public class KeyValueSetHandler extends BaseCmdRobotHandler {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public String getCommand() {
        return "kvset";
    }

    @Override
    public String getDesc() {
        return "字典变量赋值 'kvset $k $v', 共享kv 'kvset -s $k $v'";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            String[] splitRes = StringUtils.split(baseEvent.getCommandInput(), " ");
            if (null == splitRes || splitRes.length < 2) {
                return "命令格式错误";
            }
            String chatterId = baseEvent.getMessageReceiveEvent().getMessage().getChatterId();
            Chatter chatter = chatterFactory.select(chatterId);
            Assert.notNull(chatter, "chatter is null");
            String key = null;
            String value = null;
            boolean share = false;
            if (Objects.equals(splitRes[0], "-s")) {
                if (splitRes.length < 3) {
                    return "命令格式错误";
                }
                key = splitRes[1];
                if (!permissionCheck(key, chatter.getId())) {
                    return "无权限操作";
                }
                value = String.join(" ", Arrays.copyOfRange(splitRes, 2, splitRes.length));
                share = true;
            } else {
                key = splitRes[0];
                if (!permissionCheck(key, chatter.getId())) {
                    return "无权限操作";
                }
                value = String.join(" ", Arrays.copyOfRange(splitRes, 1, splitRes.length));
            }
            keyValueFactory.save(KeyValue.builder()
                    .owner(chatter.getId())
                    .desc("系统变量")
                    .share(share)
                    .key(key)
                    .value(value).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "设置key成功";
    }

    private boolean permissionCheck(String key, String operator) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        if (Objects.isNull(keyValue) || keyValue.isShare()) {
            return true;
        }
        // 操作权限判断
        if (!Objects.equals(keyValue.getOwner(), operator)) {
            return false;
        }
        return true;
    }


    @Override
    public Integer order() {
        return 0;
    }
}
