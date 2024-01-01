package com.mola.molachat.server.action;

import com.mola.molachat.common.annotation.Handler;
import com.mola.molachat.server.websocket.Action;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 策略上下文
 * @date : 2021-05-17 00:32
 **/
@Handler
@Slf4j
public class ActionStrategyContext {

    private final Map<Integer, WSRequestActionHandler> actionHandlerMap = new HashMap<>(8);

    @Resource
    private ApplicationContext applicationContext;

    @PostConstruct
    public void postConstruct() {
        log.info("start init action handler context");
        Map<String, WSRequestActionHandler> beansOfType = applicationContext.getBeansOfType(WSRequestActionHandler.class);
        beansOfType.forEach((s, handler) -> {
            actionHandlerMap.put(handler.actonCode(), handler);
            log.info("注入：{}", handler.getClass().getName());
        });
    }

    public void postHandleAction(Action action) {
        WSRequestActionHandler handler = actionHandlerMap.get(action.getCode());
        if (null == handler) {
            log.error("can not find WSRequestActionHandler by any action code !");
            return;
        }
        try {
            handler.handle(action);
        } catch (Exception e) {
            log.error("postHandleAction error" + e.getMessage(), e);
        }
    }
}
