package com.mola.molachat.group.data.impl;

import com.mola.molachat.group.data.GroupFactoryInterface;
import com.mola.molachat.group.model.Group;
import com.mola.molachat.common.utils.IdUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 群组管理工厂
 * @date : 2021-05-20 13:01
 **/
@Component
public class GroupFactory implements GroupFactoryInterface {

    public static final String COMMON_GROUP_ID = "common-group";
    /**
     * groupId -> group
     */
    private static Map<String, Group> groupMap;

    public GroupFactory(){
        groupMap = new ConcurrentHashMap<>();
        // 创建默认群组
        Group commonGroup = createDefaultCommonGroup();
        create(commonGroup);
    }

    private static Group createDefaultCommonGroup() {
        Group group = new Group();
        group.setId(COMMON_GROUP_ID);
        group.setName("公共群组");
        group.setSessionId("common-session");
        group.setCreatorId("admin");
        group.setDesc("欢迎各位的到来");
        group.setImgUrl("img/mola.png");
        group.setMemberIds(new HashSet<>());
        return group;
    }

    @Override
    public Group create(Group newGroup) {
        //赋值id
        if (newGroup.getId() == null) {
            newGroup.setId(IdUtils.getGroupId());
        }
        newGroup.setCreateTime(new Date());
        newGroup.setUpdateTime(new Date());
        groupMap.put(newGroup.getId(), newGroup);
        return newGroup;
    }

    @Override
    public List<Group> list(Predicate<Group> predicate) {
        List<Group> all = groupMap.entrySet().stream().map(entry -> entry.getValue())
                .collect(Collectors.toList());
        if (null == predicate) {
            return all;
        }
        return all.stream().filter(predicate).collect(Collectors.toList());
    }

    @Override
    public List<Group> list() {
        return list(null);
    }

    @Override
    public Group selectOne(String groupId) {
        return groupMap.get(groupId);
    }

    @Override
    public boolean delete(String groupId) {
        return groupMap.remove(groupId) == null;
    }

    @Override
    public Group update(Group group) {
        return groupMap.put(group.getId(), group);
    }
}
