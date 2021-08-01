package com.mola.molachat.robot.bus;

import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.event.base.BaseEventBusRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:33
 **/
@Component
@Slf4j
public class RobotEventBusRegistry extends BaseEventBusRegistry<RobotEventBus> {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public void register(String chatterId, RobotEventBus eventBus) {
        Assert.notNull(eventBus, "总线不能为空");
        Assert.isTrue(!StringUtils.isEmpty(chatterId), "chatterId不能为空");
        // 2、查询是否存在robot
        Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter || !(chatter instanceof RobotChatter)) {
            log.error("[RobotEventBusRegistry$register]chatter不存在或不为机器人, chatterId = {}", chatterId);
            return;
        }
        super.register(chatterId, eventBus);
    }

    /**
     * 获取总线
     * @param chatterId
     * @return
     */
    @Override
    public RobotEventBus getEventBus(String chatterId) {
        Assert.isTrue(!StringUtils.isEmpty(chatterId), "chatterId不能为空");
        RobotEventBus eventBus = super.getEventBus(chatterId);
        Assert.notNull(eventBus, "未找到总线");
        return eventBus;
    }
}
