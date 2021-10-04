package com.mola.molachat.form;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-21 17:20
 **/
@Data
public class GroupForm {

    /**
     * id
     */
    private String id;

    /**
     * 创建者id
     */
    @NotNull
    private String creatorId;

    /**
     * 群组名
     */
    @NotNull
    private String name;

    /**
     * 群组描述
     */
    private String desc;

    /**
     * 群成员id
     */
    private Set<String> memberIds;
}
