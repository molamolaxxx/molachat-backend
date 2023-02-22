package com.mola.molachat.server;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.websocket.Action;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.entity.Message;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.VideoStateEnum;
import com.mola.molachat.exception.service.ServerServiceException;
import com.mola.molachat.handler.action.ActionStrategyContext;
import com.mola.molachat.server.session.SessionWrapper;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.ServerService;
import com.mola.molachat.service.SessionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:11
 * @Version 1.0
 * websocket服务器 , 请求为chatterId
 */

@Component
@Slf4j
@Data
@Scope("prototype")
public class ChatServer {

    @Resource
    private ServerService serverService;

    @Resource
    private SessionService sessionService;

    @Resource
    private ChatterService chatterService;

    @Resource
    private ActionStrategyContext actionStrategy;

    /**
     * 不同ws对应的session包装器（策略）
     */
    private SessionWrapper session;

    //一个server对应一个chatter
    private String chatterId;

    //心跳包,存放最后一次心跳的时间,默认十秒一次
    private Long lastHeartBeat;

    /**
     * 相同chatterId连接了多少个客户端
     */
    private AtomicInteger connectClientCount = new AtomicInteger(0);

    /**
     * 登录之后，开始连接
     * @param session
     * @param chatterId
     * @throws IOException
     */
    public void onOpen(SessionWrapper session, String chatterId) throws Exception {
        this.session = session;
        log.info("chatterId:"+chatterId+"开始连接");
        this.chatterId = chatterId;
        this.lastHeartBeat = System.currentTimeMillis();

        //1.添加服务器
        try {
            //如果存在服务器：重连状态只是更换session,不存在则创建
            if (null == serverService.selectByChatterId(chatterId)){
                serverService.create(this);
            }
        } catch (ServerServiceException e) {
            log.error("ChatServer onOpen error!" + chatterId, e);
            //发送异常信息
            this.session.sendToClient(WSResponse
                    .exception("exception", e.getCode()+":"+e.getMessage()));
            throw new RuntimeException(e);
        }
        //2.如果对应chatter的消息队列里有消息，则消费送出
        BlockingQueue<Message> queue = chatterService.getQueueById(chatterId);
        while (queue.size() != 0){
            Message message = queue.poll(100, TimeUnit.MILLISECONDS);
            this.session.sendToClient(WSResponse.message("send content!", message));
        }
        connectClientCount.getAndIncrement();
    }

    /**
     * 关闭连接
     */
    public void onClose() throws IOException, EncodeException{
        log.info("chatterId:"+chatterId+"断开连接");
        if (connectClientCount.get() > 0) {
            connectClientCount.decrementAndGet();
        }

        //1.移除服务器对象
        serverService.remove(this);

        //2.将chatter对象设置成离线
        chatterService.setChatterStatus(chatterId, ChatterStatusEnum.OFFLINE.getCode());

        //3.删除关联的video-session
        sessionService.deleteVideoSession(chatterId);

        //4、将video状态改为未占用
        chatterService.changeVideoState(chatterId, VideoStateEnum.FREE.getCode());

        log.info("成功移除server对象");
    }

    /**
     * 收到客户端消息,message定义动作与数据,目前动作只可能为传递消息
     * 前端发来的动作，包括
     * 1.发送消息
     * 2.创建会话
     * 3.心跳
     *
     * todo 异常处理
     */
    public void onMessage(String actionJSON) throws Exception{
        // 处理策略
        actionStrategy.postHandleAction(buildAction(actionJSON));
    }

    /**
     * 根据前端post的json构建action对象，这一方法可以抽象到handler中
     * @param actionJson
     * @return
     */
    public Action buildAction(String actionJson) {
        //解析前端发送的action
        JSONObject jsonObject = JSONObject.parseObject(actionJson);
        Action action = new Action();
        action.setCode((Integer) jsonObject.get("code"));
        action.setMsg((String) jsonObject.get("msg"));
        action.setData(jsonObject.get("data"));
        action.setSessionWrapper(session);
        action.setChatterId(chatterId);
        return action;
    }

    public void onError(Throwable error) throws EncodeException, IOException{
        log.error("chatterId:"+chatterId+"发生错误");
        // 此时端点已经失效
//        this.session.getBasicRemote()
//                .sendObject(WSResponse.exception("server-error", "服务器出现错误"));
        error.printStackTrace();
    }

}
