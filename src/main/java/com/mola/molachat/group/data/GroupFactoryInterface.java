package com.mola.molachat.group.data;

import com.mola.molachat.group.model.Group;

import java.util.List;
import java.util.function.Predicate;

public interface GroupFactoryInterface {

    /**
     * 创建
     * @param newGroup
     * @return
     */
    Group create(Group newGroup);

    /**
     * 列表
     * @return
     */
    List<Group> list(Predicate<Group> predicate);

    /**
     * 列表
     * @return
     */
    List<Group> list();

    /**
     * 查找
     * @param groupId
     * @return
     */
    Group selectOne(String groupId);

    /**
     * 删除
     */
    boolean delete(String groupId);

    /**
     * 更新一个chatter
     */
    Group update(Group group);
}
