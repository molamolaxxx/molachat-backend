package com.mola.molachat.common.enums;

import lombok.Getter;

/**
 * @Author: molamola
 * @Date: 19-8-6 下午4:39
 * @Version 1.0
 * service错误码
 */
@Getter
public enum  ServiceErrorEnum {

    CHATTER_NAME_DUPLICATE(101, "chatter昵称重复"),
    CHATTER_IP_DUPLICATE(102, "chatterIP重复"),
    CHATTER_NOT_FOUND(103,"未找到chatter"),
    UPDATE_CHATTER_ERROR(104, "更新chatter出错"),

    SERVICE_REMOVE_CHATTER_ERROR(103, "service在移除chatter时出错"),

    SESSION_CREATE_ERROR(110, "创建session时出错"),
    SESSIONS_CLOSE_ERROR(111, "按照用户关闭所有session时出错"),
    SESSION_NOT_FOUND(112, "未找到session"),
    SEND_MESSAGE_ERROR(113, "发送消息失败"),

    SERVER_CREATE_ERROR(121, "创建服务器出错"),
    SERVER_REMOVE_ERROR(122, "移除服务器出错"),
    SERVER_NOT_FOUND(123, "未找到对应服务器"),

    VISITOR_CREATE_GROUP_ERROR(201, "访客不能创建多个群组，不能高于三个用户")
    ;

    private Integer code;

    private String msg;

    ServiceErrorEnum(Integer code , String msg){
        this.code = code;
        this.msg = msg;
    }
}
