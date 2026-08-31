package com.mola.molachat.session.solution;

import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.dto.MessagePreviewDTO;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.dto.SessionPreviewDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds lightweight history responses without mutating persisted messages.
 */
@Component
public class SessionPreviewSolution {

    static final int CONTENT_PREVIEW_LENGTH = 200;

    @Resource
    private SessionService sessionService;

    public SessionPreviewDTO toPreview(SessionDTO session) {
        if (session == null) {
            return null;
        }
        SessionPreviewDTO preview = new SessionPreviewDTO();
        preview.setSessionId(session.getSessionId());
        preview.setChatterSet(session.getChatterSet());
        preview.setCreateTime(session.getCreateTime());
        preview.setMessageList(toMessagePreviews(session.getMessageList(), session.getSessionId()));
        return preview;
    }

    public String findMessageContent(String sessionId, String messageId, String chatterId) {
        SessionDTO session = sessionService.findSession(sessionId);
        if (session == null || !containsChatter(session, chatterId)
                || session.getMessageList() == null) {
            return null;
        }
        List<Message> messages = session.getMessageList();
        synchronized (messages) {
            for (Message message : messages) {
                if (Objects.equals(messageId, message.getId())) {
                    return message.getContent();
                }
            }
        }
        return null;
    }

    private boolean containsChatter(SessionDTO session, String chatterId) {
        if (session.getChatterSet() == null) {
            return false;
        }
        for (Chatter chatter : session.getChatterSet()) {
            if (Objects.equals(chatterId, chatter.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<MessagePreviewDTO> toMessagePreviews(List<Message> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<MessagePreviewDTO> previews = new ArrayList<>(messages.size());
        synchronized (messages) {
            for (Message message : messages) {
                previews.add(toMessagePreview(message, sessionId));
            }
        }
        return previews;
    }

    private MessagePreviewDTO toMessagePreview(Message message, String sessionId) {
        MessagePreviewDTO preview = new MessagePreviewDTO();
        preview.setId(message.getId());
        preview.setChatterId(message.getChatterId());
        preview.setCreateTime(message.getCreateTime());
        preview.setCommon(message.isCommon());
        preview.setSessionId(message.getSessionId() == null ? sessionId : message.getSessionId());
        preview.setShowStyleProps(message.getShowStyleProps());

        String content = message.getContent();
        // Old persisted messages without an id cannot be fetched individually, so keep them complete.
        boolean canLoadFullContent = message.getId() != null && !message.getId().isEmpty();
        boolean truncated = canLoadFullContent && content != null
                && content.length() > CONTENT_PREVIEW_LENGTH;
        preview.setContentTruncated(truncated);
        preview.setContent(truncated ? content.substring(0, CONTENT_PREVIEW_LENGTH) : content);

        if (message instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage) message;
            preview.setUrl(fileMessage.getUrl());
            preview.setSnapshotUrl(fileMessage.getSnapshotUrl());
            preview.setFileName(fileMessage.getFileName());
            preview.setFileStorage(fileMessage.getFileStorage());
        }
        return preview;
    }
}
