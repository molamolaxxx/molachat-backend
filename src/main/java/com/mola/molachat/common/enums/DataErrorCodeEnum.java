package com.mola.molachat.common.enums;

import lombok.Getter;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:21
 * @Version 1.0
 */
@Getter
public enum DataErrorCodeEnum {

    CHATTER_OVER_FLOW(11, "chatter数目达到了上限"),
    INSERT_CHATTER_ERROR(12, "插入chatter出错"),
    UPDATE_CHATTER_ERROR(13, "更新chatter出错"),
    REMOVE_DELETE_ERROR(14, "移除chatter出错"),

    CREATE_SESSION_ERROR(21, "创建session出错"),
    SESSION_NOT_EXIST(22, "session不存在"),
    REMOVE_SESSION_ERROR(23, "移除session出错"),

    CREATE_SERVER_ERROR(24, "创建socket服务器出错"),
    SERVER_NOT_EXIST(25, "server不存在"),
    REMOVE_SERVER_ERROR(26, "移除socket服务器出错"),

    UPLOAD_FILE_EXCEED(113, "发送文件过大"),

    ;

    private Integer code;

    private String msg;

    DataErrorCodeEnum(Integer code , String msg){
        this.code = code;
        this.msg = msg;
    }
}
