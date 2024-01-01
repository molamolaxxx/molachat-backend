package com.mola.molachat.session.model;

import lombok.Data;

import java.util.Date;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午2:51
 * @Version 1.0
 * 聊天信息
 */
@Data
public class Message {

    /**
     * id
     */
    protected String id;

    /**
     * 发送方chatterID
     */
    protected String chatterId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 时间
     */
    protected Date createTime;

    /**
     * 是否是群聊消息
     */
    private boolean isCommon = false;

    /**
     * 会话id
     */
    private String sessionId;

}
