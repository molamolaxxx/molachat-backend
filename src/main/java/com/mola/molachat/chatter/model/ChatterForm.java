package com.mola.molachat.chatter.model;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @Author: molamola
 * @Date: 19-8-14 下午5:20
 * @Version 1.0
 */
@Data
public class ChatterForm {

    /**
     * id唯一
     */
    @NotNull(message = "chatterId不可为空")
    private String id;

    @NotNull(message = "token不可为空")
    private String token;

    /**
     * 昵称
     */
    private String name;

    /**
     * 头像url
     */
    private String imgUrl;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 标签
     */
    private Integer tag;

    /**
     * 机器api
     */
    private String apiKey;

    /**
     * 处理线的bean名称，用于区分不同功能的机器人
     */
    private String eventBusBeanName;
}
