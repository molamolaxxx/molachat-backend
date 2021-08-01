package com.mola.molachat.entity.dto;

import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-7 上午11:02
 * @Version 1.0
 */
@Data
public class SessionDTO {

    /**
     * 会话的id
     */
    private String sessionId;

    /**
     * 会话中的聊天者
     */
    private Set<Chatter> chatterSet;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * content
     */
    private List<Message> messageList;
}
