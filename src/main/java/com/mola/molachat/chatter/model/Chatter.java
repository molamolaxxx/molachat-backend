package com.mola.molachat.chatter.model;

import com.mola.molachat.group.data.impl.GroupFactory;
import com.mola.molachat.chatter.enums.ChatterTagEnum;
import lombok.Data;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午2:45
 * @Version 1.0
 * 实体：聊天者
 */
@Data
public class Chatter {

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
     * status,离线or在线
     */
    private Integer status;

    /**
     * 用户标签
     */
    private Integer tag = ChatterTagEnum.VISITOR.getCode();

    /**
     * createTime
     */
    private Date createTime;

    /**
     * 头像url
     */
    private String imgUrl;

    /**
     * 个性签名
     */
    private String signature;

    //存放最近在线的时间
    private Long lastOnline = System.currentTimeMillis();

    /**
     * 活跃度评分
     */
    private Integer point = 0;

    /**
     * 视频请求状态
     * 0: 未占用
     * 1: 请求中
     * 2: 已占用
     */
    private AtomicInteger videoState = new AtomicInteger(0);

    /**
     * 当前用户所在群组，用于过滤用户
     */
    private String currentGroup = GroupFactory.COMMON_GROUP_ID;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chatter chatter = (Chatter) o;
        return id.equals(chatter.getId()) && name.equals(chatter.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
