package com.mola.molachat.session.solution;

import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.server.websocket.video.VideoWSResponse;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.chatter.enums.ChatterTagEnum;
import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.service.GroupServiceException;
import com.mola.molachat.common.exception.service.SessionServiceException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.group.service.GroupService;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.robot.solution.RobotSolution;
import com.mola.molachat.common.utils.BeanUtilsPlug;
import com.mola.molachat.common.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-01-01 19:10
 **/
@Component
@Slf4j
public class SessionSolution {

    @Resource
    private ServerService serverService;

    @Resource
    private RobotSolution robotSolution;

    @Resource
    private ChatterService chatterService;

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private GroupService groupService;

    @AddPoint(action = ChatterPointEnum.SEND_MESSAGE, key = "#message.chatterId")
    public Message insertMessage(String sessionId, Message message) throws SessionServiceException {
        //1.查询是否存在对应session
        Session session = sessionFactory.selectById(sessionId);
        if (null == session){
            throw new SessionServiceException(ServiceErrorEnum.SESSION_NOT_FOUND);
        }

        //2.向session中插入message
        sessionFactory.insertMessage(session.getSessionId(), message);

        //3.向socket服务器发送消息,找到session内除发送者的所有ws服务器对象
        for (String chatterId : session.getChatterSet().stream().map(e -> e.getId()).collect(Collectors.toList())){
            Chatter chatter = chatterFactory.select(chatterId);
            // 如果为机器人，进行插槽调用，利用handler解析message
            if (chatter instanceof RobotChatter) {
                robotSolution.onReceiveMessage(message, sessionId, (RobotChatter)chatter);
                continue;
            }
            //构建response,向不同客户端发送
            try {
                ChatServer server = serverService.selectByChatterId(chatterId);
                if (null != server) {
                    // todo 改成生产者消费者模型，异步执行
                    server.getSession().sendToClient(WSResponse.message("send content!", message));
                } else {
                    // 将消息存入消息队列
                    chatterService.offerMessageIntoQueue(message,chatterId);
                }
            } catch (Exception e) {
                throw new SessionServiceException(ServiceErrorEnum.SEND_MESSAGE_ERROR, e.getMessage());
            }
        }
        return message;
    }

    public void deleteVideoSession(String chatterId) {
        String needToAlert = sessionFactory.removeVideoSession(chatterId);
        // 发消息,挂断视频
        if (null != needToAlert) {
            serverService.sendResponse(needToAlert, VideoWSResponse
                    .requestVideoOff("挂断视频", null));
        }
    }


    public SessionDTO createGroupSession(Set<String> chatterIds, String creatorId) {
        // 1、每一个游客最多创建1个群组，组内不超过3个用户
        Chatter creator = chatterFactory.select(creatorId);
        Assert.notNull(creator, "群组创建者不能为空，group creator can not be null");
        Assert.isTrue(null != chatterIds && chatterIds.size() > 0,
                "群组成员个数必须存在且大于0，members size need to be exist and over than zero");
        Assert.isTrue(chatterIds.contains(creatorId), "成员必须包含创建者，members must contains creator!");
        if (ChatterTagEnum.VISITOR.getCode().equals(creator.getTag())) {
            // 1 最多创建1个群组 不能多于3个用户
            if (groupService.listByOwner(creatorId).size() > 0 || chatterIds.size() > 3) {
                throw new GroupServiceException(ServiceErrorEnum.VISITOR_CREATE_GROUP_ERROR);
            }
        }
        Session groupSessionInner = createGroupSessionInner(chatterIds, creatorId);
        return (SessionDTO) BeanUtilsPlug.copyPropertiesReturnTarget(groupSessionInner, new SessionDTO());
    }


    private Session createGroupSessionInner(Set<String> chatterIds, String creatorId) {
        Session groupSession = new Session();
        groupSession.setSessionId(IdUtils.getSessionId());
        List<Chatter> chatters = chatterIds.stream()
                .map(id -> chatterFactory.select(id))
                .filter(chatter -> null != chatter)
                .collect(Collectors.toList());
        groupSession.setChatterSet(new HashSet<>(chatters));
        sessionFactory.create(groupSession);
        return groupSession;
    }
}
