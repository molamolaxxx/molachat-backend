package com.mola.molachat.chatter.model;

import lombok.Data;

import java.util.UUID;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-05 19:36
 **/
@Data
public class RobotChatter extends Chatter {
    /**
     * 唯一appkey，用于外部api调用
     */
    private String appKey = UUID.randomUUID().toString();

    /**
     * 图灵api
     */
    private String apiKey;

    /**
     * 处理线的bean名称，用于区分不同功能的机器人
     */
    private String eventBusBeanName;
}
