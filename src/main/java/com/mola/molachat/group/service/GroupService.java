package com.mola.molachat.group.service;

import com.mola.molachat.group.model.Group;

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
}
