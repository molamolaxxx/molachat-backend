package com.mola.molachat.handler.action;

import com.mola.molachat.annotation.Handler;
import com.mola.molachat.common.constant.SessionConstant;
import com.mola.molachat.common.websocket.Action;
import com.mola.molachat.common.websocket.ActionCode;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.entity.Group;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.server.session.SessionWrapper;
import com.mola.molachat.service.GroupService;
import com.mola.molachat.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: session创建处理器
 * @date : 2021-05-17 00:41
 **/
@Handler
@Slf4j
public class CreateSessionHandler implements WSRequestActionHandler{

    @Resource
    private SessionService sessionService;

    @Resource
    private GroupService groupService;

    @Override
    public Integer actonCode() {
        return ActionCode.CREATE_SESSION;
    }

    @Override
    public void handle(Action action) throws Exception {
        log.info("action:创建/找到session");
        try {
            //按照分号获取id
            String ids = (String) action.getData();
            String chatterId = action.getChatterId();
            SessionWrapper session = action.getSessionWrapper();

            // 如果该session为群聊session
            if (SessionConstant.COMMON_SESSION_ID.equals(ids)) {
                SessionDTO sessionDTO = sessionService.findCommonAndGroupSession(chatterId, SessionConstant.COMMON_SESSION_ID);
                session.sendToClient(WSResponse.createSession("ok", sessionDTO));
                return;
            }

            // 如果为group-session
            Group group = groupService.selectBySessionId(ids);
            if (null != group && group.getMemberIds().contains(chatterId)) {
                SessionDTO groupSession = sessionService.findSession(ids);
                if (null != groupSession) {
                    groupSession = sessionService.findCommonAndGroupSession(chatterId, groupSession.getSessionId());
                    session.sendToClient(WSResponse.createSession("ok", groupSession));
                    return;
                }
            }

            String[] idSplit = ids.split(";");
            Assert.isTrue(idSplit.length == 2, "会话参数长度错误");
            //查找是否已经存在session,没有的话创建session
            SessionDTO sessionDTO = sessionService.findOrCreateSession(idSplit[0], idSplit[1]);

            //返回session信息
            session.sendToClient(WSResponse.createSession("ok", sessionDTO));
        } catch (Exception e) {
            log.error("com.mola.molachat.handler.action.CreateSessionHandler.handle error ", e);
            action.getSessionWrapper().sendToClient(WSResponse.exception("会话创建失败", e.getMessage()));
        }

    }
}
