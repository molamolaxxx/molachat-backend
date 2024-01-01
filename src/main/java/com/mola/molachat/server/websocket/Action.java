package com.mola.molachat.server.websocket;

import com.mola.molachat.server.session.SessionWrapper;
import lombok.Data;
import lombok.ToString;
import org.springframework.lang.Nullable;

/**
 * @Author: molamola
 * @Date: 19-8-10 下午11:31
 * @Version 1.0
 * 前端发来的请求动作
 */
@Data
@ToString
public class Action<T> {

    private Integer code;

    private String msg;

    private String chatterId;

    @Nullable
    private SessionWrapper sessionWrapper;

    private T data;

}
