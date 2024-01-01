package com.mola.molachat.chatter.dto;

import com.mola.molachat.chatter.enums.ChatterTagEnum;
import lombok.Data;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: molamola
 * @Date: 19-8-6 下午4:19
 * @Version 1.0
 */
@Data
public class ChatterDTO {

    /**
     * id唯一
     */
    private String id;

    /**
     * 昵称
     */
    private String name;

    /**
     * ip
     */
    private String ip;

    /**
     * status
     */
    private Integer status;

    /**
     * 用户标签
     * @see ChatterTagEnum
     */
    private Integer tag;

    /**
     * createTime
     */
    private Date createTime;

    /**
     * 头像url
     */
    private String imgUrl;

    /**
     * 签名
     */
    private String signature;

    /**
     * 是否是机器人
     */
    private boolean isRobot = false;

    /**
     * 机器人apikey
     */
    private String apiKey;

    /**
     * 处理线的bean名称，用于区分不同功能的机器人
     */
    private String eventBusBeanName;

    /**
     * token
     */
    private String token;

    //存放最近在线的时间
    private Long lastOnline = System.currentTimeMillis();

    /**
     * 活跃度评分
     */
    private Integer point = 0;

    /**
     * 当前用户所在群组，用于过滤用户
     */
    private String currentGroup;

    /**
     * 视频请求状态
     * 0: 未占用
     * 1: 请求中
     * 2: 已占用
     */
    private AtomicInteger videoState = new AtomicInteger(0);

    /**
     * 唯一appkey，用于外部api调用
     */
    private String appKey;

}
