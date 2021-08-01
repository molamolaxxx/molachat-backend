package com.mola.molachat.enumeration;

import lombok.Getter;

@Getter
public enum VideoStateEnum {
    FREE(0, "视频线路未占用"),
    READY(1,"正在准备通信"),
    OCCUPY(2, "视频线路占用")
    ;
    private Integer code;

    private String msg;

    VideoStateEnum(Integer code , String msg){
        this.code = code;
        this.msg = msg;
    }
}
