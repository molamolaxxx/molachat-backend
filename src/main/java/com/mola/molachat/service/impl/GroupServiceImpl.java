package com.mola.molachat.service.impl;

import com.mola.molachat.data.GroupFactoryInterface;
import com.mola.molachat.data.impl.cache.GroupFactory;
import com.mola.molachat.entity.Group;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.exception.AppBaseException;
import com.mola.molachat.form.GroupForm;
import com.mola.molachat.service.GroupService;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.utils.BeanUtilsPlug;
import com.mola.molachat.utils.CopyUtils;
import com.mola.molachat.utils.SegmentLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
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
    private SessionService sessionService;

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

    @Override
    public Group create(GroupForm groupForm, boolean isOpenSession) {
        Group newGroup = new Group();
        BeanUtilsPlug.copyPropertiesReturnTarget(groupForm, newGroup);
        SessionDTO groupSession = null;
        if (isOpenSession) { // 同时生成会话
            groupSession = sessionService.createGroupSession(
                    groupForm.getMemberIds(), groupForm.getCreatorId());
            newGroup.setSessionId(groupSession.getSessionId());
        }
        try {
            groupFactoryInterface.create(newGroup);
        } catch (Throwable throwable) {
            log.error("创建group出错，回滚");
            if (isOpenSession) {
                sessionService.deleteById(groupSession.getSessionId());
            }
            throw new AppBaseException("创建group出错，回滚");
        }

        return newGroup;
    }

    @Override
    public boolean delete(String groupId, String creatorId) {
        Group group = groupFactoryInterface.selectOne(groupId);
        Assert.notNull(group, "需要删除的group为空");
        boolean res = true;
        if (!group.getCreatorId().equals(creatorId)) {
            log.error("creatorId和group创建者不一致,chatterId = {}", creatorId);
            return false;
        }

        // 会话销毁
        res &= sessionService.deleteById(group.getSessionId());
        res &= groupFactoryInterface.delete(groupId);
        return res;
    }

    @Override
    public Group update(GroupForm groupForm) {
        Assert.hasText(groupForm.getId(), "更新group时不能为空");
        // 元数据更新
        Group toUpdate = groupFactoryInterface.selectOne(groupForm.getId());
        CopyUtils.copyProperties(groupForm, toUpdate);
        groupFactoryInterface.update(toUpdate);
        // session更新
        try {
            segmentLock.lock(toUpdate);
            SessionDTO session = sessionService.findSession(toUpdate.getSessionId());
            session.getChatterSet().forEach(
                    chatter -> {
                        if (!toUpdate.getMemberIds().contains(chatter.getId())) {
                            sessionService.removeChatterFromSession(chatter, session);
                        }
                    }
            );

        } finally {
            segmentLock.unlock(toUpdate);
        }
        return toUpdate;
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
