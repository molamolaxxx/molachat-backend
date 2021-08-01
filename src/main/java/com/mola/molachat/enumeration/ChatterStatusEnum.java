package com.mola.molachat.enumeration;

import lombok.Getter;

/**
 * @Author: molamola
 * @Date: 19-9-1 下午12:52
 * @Version 1.0
 * chatter状态
 */
@Getter
public enum  ChatterStatusEnum {

    ONLINE(1,"在线"),
    OFFLINE(-1,"离线"),
    DISCONNECT(0, "掉线"),
    LOGICAL_DELETE(2, "逻辑删除")
    ;

    private Integer code;

    private String msg;

    ChatterStatusEnum(Integer code , String msg){
        this.code = code;
        this.msg = msg;
    }
}
