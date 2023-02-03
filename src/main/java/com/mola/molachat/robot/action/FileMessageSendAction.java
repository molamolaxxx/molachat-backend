package com.mola.molachat.robot.action;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:32
 **/
@Data
public class FileMessageSendAction extends MessageSendAction {

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件连接
     */
    private String url;
}
