package com.mola.molachat.entity;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-20 16:03
 **/
@Data
public class VideoSession {

    // 通信的发起方
    private Chatter request;

    // 通信的接收方
    private Chatter accept;
}
