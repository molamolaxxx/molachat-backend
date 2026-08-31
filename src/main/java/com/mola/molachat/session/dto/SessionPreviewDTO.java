package com.mola.molachat.session.dto;

import com.mola.molachat.chatter.model.Chatter;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Session projection that keeps the message list lightweight for switching chats.
 */
@Data
public class SessionPreviewDTO {

    private String sessionId;

    private Set<Chatter> chatterSet;

    private Date createTime;

    private List<MessagePreviewDTO> messageList;
}
