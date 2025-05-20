package com.mola.molachat.robot.action;

import com.mola.molachat.common.event.action.BaseAction;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:32
 **/
@Data
public class MessageSendAction extends BaseAction {

    /**
     * 返回结果
     */
    private String responsesText;

    public static MessageSendAction skip() {
        MessageSendAction action = new MessageSendAction();
        action.setSkip(true);
        return action;
    }

    public static MessageSendAction withResp(String resp) {
        MessageSendAction action = new MessageSendAction();
        action.setResponsesText(resp);
        return action;
    }
}
