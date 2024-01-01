package com.mola.molachat.server.action;

import com.mola.molachat.server.websocket.Action;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-17 00:28
 **/
public interface WSRequestActionHandler {

    /**
     * @return 操作码
     */
    Integer actonCode();

    /**
     * 处理请求动作
     * @param action
     */
    void handle(Action action) throws Exception;
}
