package com.mola.molachat.server.tomcat;

import com.mola.molachat.common.MyApplicationContextAware;
import com.mola.molachat.server.encoder.ServerEncoder;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.session.TomcatSessionWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 12:36
 **/
@ServerEndpoint(value = "/server/{chatterId}", encoders = {ServerEncoder.class})
// 基于tomcat的原型模式
//@Component // 注入到容器只是为了让spring拿到class对象，真正实例化还是交给tomcat了，每个连接打开都会产生一个新的对象
// 具体实例化的逻辑都在ServerEndpointExporter中
// 与spring websocket不同，这个是由tomcat支持的
@Slf4j
@Data
public class TomcatChatServer{

    private ChatServer chatServer;

    private void initDependencyInjection(){
        // 基于tomcat的原型模式，service无法注入到chatserver
        if (null == chatServer) {
            chatServer = MyApplicationContextAware.getApplicationContext().getBean(ChatServer.class);
        }
    }

    /**
     * 登录之后，开始连接
     * @param session
     * @param chatterId
     * @throws IOException
     */
    @OnOpen
    public void onOpen(Session session , @PathParam("chatterId") String chatterId) throws Exception {
        initDependencyInjection();
        chatServer.onOpen(new TomcatSessionWrapper(session), chatterId);
    }

    /**
     * 关闭连接
     */
    @OnClose
    public void onClose() throws IOException, EncodeException{
        chatServer.onClose();
    }

    /**
     * 收到客户端消息,message定义动作与数据,目前动作只可能为传递消息
     * 前端发来的动作，包括
     * 1.发送消息
     * 2.创建会话
     * 3.心跳
     */
    @OnMessage
    public void onMessage(String actionJSON) throws Exception{
        chatServer.onMessage(actionJSON);
    }

    @OnError
    public void onError(Session session, Throwable error) throws EncodeException, IOException {
        chatServer.onError(error);
    }

}
