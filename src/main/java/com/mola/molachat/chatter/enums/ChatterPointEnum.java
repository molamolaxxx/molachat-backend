package com.mola.molachat.chatter.enums;

import lombok.Getter;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: chatter评分枚举
 * @date : 2020-05-01 12:12
 **/
@Getter
public enum ChatterPointEnum {

    HEARTBEAT(1,"心跳一次增加1"),
    UPDATE(5,"更新一次增加5"),
    INIT(300, "初始化增加300"),
    SEND_MESSAGE(2, "发一条消息增加2分"),
    CREATE_SERVER(5,"创建一次ws端点"),
    INTO_COMMON(5, "进入一次公共群聊区域"),
    SEND_FILE(10, "发送一个文件加10分")
    ;

    private Integer point;

    private String msg;

    ChatterPointEnum(Integer point , String msg){
        this.point = point;
        this.msg = msg;
    }
}
