package com.mola.molachat.service;

import com.mola.molachat.entity.Group;
import com.mola.molachat.form.GroupForm;

import java.util.List;

public interface GroupService {

    /**
     * 列表
     * @return
     */
    List<Group> listAll();

    /**
     * 按所有者列出
     * @param chatterId
     * @return
     */
    List<Group> listByOwner(String chatterId);

    /**
     * 按参与者列出
     * @param memberChatterId
     * @return
     */
    List<Group> listByMemberId(String memberChatterId);

    /**
     * @param sessionId
     * @return
     */
    Group selectBySessionId(String sessionId);

    /**
     * 新建群组
     * @param groupForm
     * @param isOpenSession
     * @return
     */
    Group create(GroupForm groupForm, boolean isOpenSession);

    /**
     * 删除群组
     * @param groupId
     * @return
     */
    boolean delete(String groupId, String creatorId);

    /**
     * 更新
     * @param groupForm
     * @return
     */
    Group update(GroupForm groupForm);
}
