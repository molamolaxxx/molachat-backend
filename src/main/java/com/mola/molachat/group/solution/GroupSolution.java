package com.mola.molachat.group.solution;

import com.mola.molachat.group.data.GroupFactoryInterface;
import com.mola.molachat.group.model.Group;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.common.exception.AppBaseException;
import com.mola.molachat.group.model.GroupForm;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.SessionSolution;
import com.mola.molachat.common.utils.BeanUtilsPlug;
import com.mola.molachat.common.utils.CopyUtils;
import com.mola.molachat.common.utils.SegmentLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;

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
    private SessionSolution sessionSolution;


    @Resource
    private SessionService sessionService;


    @Resource
    private GroupFactoryInterface groupFactoryInterface;


    @Resource
    private SegmentLock segmentLock;

    public Group create(GroupForm groupForm, boolean isOpenSession) {
        Group newGroup = new Group();
        BeanUtilsPlug.copyPropertiesReturnTarget(groupForm, newGroup);
        SessionDTO groupSession = null;
        if (isOpenSession) { // 同时生成会话
            groupSession = sessionSolution.createGroupSession(
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
}
