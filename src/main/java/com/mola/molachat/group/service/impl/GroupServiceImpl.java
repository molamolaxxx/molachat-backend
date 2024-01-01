package com.mola.molachat.group.service.impl;

import com.mola.molachat.group.data.GroupFactoryInterface;
import com.mola.molachat.group.data.impl.GroupFactory;
import com.mola.molachat.group.model.Group;
import com.mola.molachat.group.service.GroupService;
import com.mola.molachat.common.utils.SegmentLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-20 16:32
 **/
@Service
@Slf4j
public class GroupServiceImpl implements GroupService {

    @Resource
    private GroupFactoryInterface groupFactoryInterface;

    @Resource
    private SegmentLock segmentLock;

    @Override
    public List<Group> listAll() {
        return groupFactoryInterface.list();
    }

    /**
     * 缓存需要条件查找的list
     */
    public Map<String, List<Group>> memberGroupListCache = new ConcurrentHashMap<>();
    public Map<String, List<Group>> ownerGroupListCache = new ConcurrentHashMap<>();
    public Map<String, Group> sessionId2GroupCache = new ConcurrentHashMap<>();

    @Override
    public List<Group> listByOwner(String chatterId) {
        Assert.hasText(chatterId, "[GroupServiceImpl] chatterId can not be null!");
        List<Group> ownerGroups = ownerGroupListCache.get(chatterId);
        if (CollectionUtils.isEmpty(ownerGroups)) {
            ownerGroups = groupFactoryInterface.list(group -> group.getCreatorId().equals(chatterId));
            ownerGroupListCache.put(chatterId, ownerGroups);
        }
        return ownerGroups;
    }

    @Override
    public List<Group> listByMemberId(String memberChatterId) {
        Assert.hasText(memberChatterId, "[GroupServiceImpl] memberChatterId can not be null!");
        List<Group> groupList = getBaseGroup();
        List<Group> memberGroups = ownerGroupListCache.get(memberChatterId);
        if (CollectionUtils.isEmpty(memberGroups)) {
            memberGroups = groupFactoryInterface.list(group -> group.getMemberIds().contains(memberChatterId));
            memberGroupListCache.put(memberChatterId, memberGroups);
        }
        groupList.addAll(memberGroups);
        return groupList;
    }

    @Override
    public Group selectBySessionId(String sessionId) {
        Assert.hasText(sessionId, "[GroupServiceImpl] sessionId can not be null!");
        Group cache = sessionId2GroupCache.get(sessionId);
        if (null == cache) {
            List<Group> data = groupFactoryInterface.list(group ->
                    group.getSessionId().equals(sessionId));
            Assert.isTrue(data.size() <= 1, "[GroupServiceImpl] same session more than one group!");
            if (data.size() == 1) {
                sessionId2GroupCache.put(sessionId, data.get(0));
                return data.get(0);
            }
        }
        return cache;
    }

    /**
     * 公共群组加入默认列表
     * @return
     */
    private List<Group> getBaseGroup() {
        Group group = groupFactoryInterface.selectOne(GroupFactory.COMMON_GROUP_ID);
        return Arrays.asList(group);
    }
}
