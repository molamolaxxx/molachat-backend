package com.mola.molachat.session.model;

import com.mola.molachat.server.session.SessionWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-02-15 14:00
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamMessageConnect {

    /**
     * 一次连接的唯一id标示
     */
    private String streamId;

    /**
     * 发送方chatter id
     */
    private String senderId;

    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 消息内容
     */
    private StringBuilder messageContent;

    /**
     * 第一次发送的消息
     */
    private StreamMessage firstSendMessage;

    /**
     * 初始化过的server
     */
    private Set<SessionWrapper> initedSessions;

    /**
     * 连接已关闭
     */
    private volatile boolean closed;
}
