package com.mola.molachat.enumeration;

import lombok.Getter;

/**
 * @Author: molamola
 * @Date: 19-9-1 下午12:52
 * @Version 1.0
 * chatter标签
 */
@Getter
public enum ChatterTagEnum {

    VISITOR(1,"游客"),
    GUEST(2,"获得邀请码的用户"),
    ROBOT(3,"机器人"),
    ;

    private Integer code;

    private String msg;

    ChatterTagEnum(Integer code , String msg){
        this.code = code;
        this.msg = msg;
    }
}
