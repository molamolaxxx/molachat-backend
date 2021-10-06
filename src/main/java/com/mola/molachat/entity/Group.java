package com.mola.molachat.entity;

import lombok.Data;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 群组
 * @date : 2021-05-16 02:15
 **/
@Data
public class Group {

    private String id;

    /**
     * 创建者id
     */
    private String creatorId;

    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 群组名
     */
    private String name;

    /**
     * 群组描述
     */
    private String desc;

    /**
     * 进入群组的提示语
     */
    private String hint;

    /**
     * 群组头像
     */
    private String imgUrl;

    /**
     * 群组状态
     */
    private Integer status;

    /**
     * 群成员id
     */
    private Set<String> memberIds;

    /**
     * 时间
     */
    private Date createTime;
    private Date updateTime;

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
