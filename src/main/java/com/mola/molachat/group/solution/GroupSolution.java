package com.mola.molachat.group.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.enums.ChatterTagEnum;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.AppBaseException;
import com.mola.molachat.common.exception.service.GroupServiceException;
import com.mola.molachat.common.utils.BeanUtilsPlug;
import com.mola.molachat.common.utils.CopyUtils;
import com.mola.molachat.common.utils.IdUtils;
import com.mola.molachat.common.utils.SegmentLock;
import com.mola.molachat.group.data.GroupFactoryInterface;
import com.mola.molachat.group.model.Group;
import com.mola.molachat.group.model.GroupForm;
import com.mola.molachat.group.service.GroupService;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-01-01 17:26
 **/
@Component
@Slf4j
public class GroupSolution {

    @Resource
    private SessionService sessionService;


    @Resource
    private GroupFactoryInterface groupFactoryInterface;


    @Resource
    private SegmentLock segmentLock;

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private GroupService groupService;

    public Group create(GroupForm groupForm, boolean isOpenSession) {
        Group newGroup = new Group();
        BeanUtilsPlug.copyPropertiesReturnTarget(groupForm, newGroup);
        SessionDTO groupSession = null;
        if (isOpenSession) { // 同时生成会话
            groupSession = createGroupSession(
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


    public SessionDTO createGroupSession(Set<String> chatterIds, String creatorId) {
        // 1、每一个游客最多创建1个群组，组内不超过3个用户
        Chatter creator = chatterFactory.select(creatorId);
        Assert.notNull(creator, "群组创建者不能为空，group creator can not be null");
        Assert.isTrue(null != chatterIds && chatterIds.size() > 0,
                "群组成员个数必须存在且大于0，members size need to be exist and over than zero");
        Assert.isTrue(chatterIds.contains(creatorId), "成员必须包含创建者，members must contains creator!");
        if (ChatterTagEnum.VISITOR.getCode().equals(creator.getTag())) {
            // 1 最多创建1个群组 不能多于3个用户
            if (groupService.listByOwner(creatorId).size() > 0 || chatterIds.size() > 3) {
                throw new GroupServiceException(ServiceErrorEnum.VISITOR_CREATE_GROUP_ERROR);
            }
        }
        Session groupSessionInner = createGroupSessionInner(chatterIds, creatorId);
        return (SessionDTO) BeanUtilsPlug.copyPropertiesReturnTarget(groupSessionInner, new SessionDTO());
    }


    private Session createGroupSessionInner(Set<String> chatterIds, String creatorId) {
        Session groupSession = new Session();
        groupSession.setSessionId(IdUtils.getSessionId());
        List<Chatter> chatters = chatterIds.stream()
                .map(id -> chatterFactory.select(id))
                .filter(chatter -> null != chatter)
                .collect(Collectors.toList());
        groupSession.setChatterSet(new HashSet<>(chatters));
        sessionFactory.create(groupSession);
        return groupSession;
    }
}
