package com.mola.molachat.robot.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.enums.ChatterTagEnum;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.annotation.RefreshChatterList;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import com.mola.molachat.session.service.SessionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Description: ACP机器人同步，外部调用创建/删除ACP机器人
 */
@Component
@Slf4j
public class AcpRobotSyncSolution {

    private static final String ACP_ROBOT_ID_PREFIX = "acp-";
    private static final String ACP_GROUP = CmdProxyConstant.ACP;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SessionService sessionService;

    /**
     * 同步ACP机器人列表
     * @param robotParams 外部传入的机器人列表
     * @param visibleChatterIds 有权限查看这些机器人的用户id集合
     */
    @RefreshChatterList
    public void sync(List<AcpRobotParam> robotParams, Set<String> visibleChatterIds) {
        if (robotParams == null) {
            robotParams = Collections.emptyList();
        }
        if (visibleChatterIds == null) {
            visibleChatterIds = Collections.emptySet();
        }

        // 1. 查询当前已有的ACP组机器人（按visibleChatterIds匹配）
        Set<String> finalVisibleChatterIds = visibleChatterIds;
        List<RobotChatter> existingAcpRobots = chatterFactory.list().stream()
                .filter(c -> c instanceof RobotChatter)
                .map(c -> (RobotChatter) c)
                .filter(r -> ACP_GROUP.equals(r.getRobotGroup()))
                .filter(r -> matchVisibleChatterIds(r.getVisibleChatterIds(), finalVisibleChatterIds))
                .collect(Collectors.toList());

        // 2. 计算入参机器人id集合
        Set<String> targetRobotIds = robotParams.stream()
                .map(p -> buildRobotId(p.getName()))
                .collect(Collectors.toSet());

        // 3. 删除不在入参列表中的机器人
        for (RobotChatter existing : existingAcpRobots) {
            if (!targetRobotIds.contains(existing.getId())) {
                deleteRobotAndSessions(existing);
            }
        }

        // 4. 创建不存在的机器人
        Set<String> existingIds = existingAcpRobots.stream()
                .map(RobotChatter::getId)
                .collect(Collectors.toSet());

        for (AcpRobotParam param : robotParams) {
            String robotId = buildRobotId(param.getName());
            if (!existingIds.contains(robotId)) {
                createAcpRobot(robotId, param.getName(), param.getSignature(), param.avatar, visibleChatterIds);
            }
        }

        log.info("ACP机器人同步完成, targetIds={}, visibleChatterIds={}", targetRobotIds, visibleChatterIds);
    }

    private void createAcpRobot(String robotId, String name, String signature, String avatar, Set<String> visibleChatterIds) {
        RobotChatter robot = new RobotChatter();
        robot.setId(robotId);
        robot.setName(name);
        robot.setSignature(signature != null ? signature : "Agent Context Protocol");
        robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
        robot.setTag(ChatterTagEnum.ROBOT.getCode());
        robot.setImgUrl(StringUtils.defaultIfBlank(avatar, "img/kiro.png"));
        robot.setIp("127.0.0.1");
        robot.setAppKey(robotId);
        robot.setEventBusBeanName("acpEventBus");
        robot.setRobotGroup(ACP_GROUP);
        robot.setVisibleChatterIds(visibleChatterIds);
        chatterFactory.create(robot);
        log.info("创建ACP机器人: id={}, name={}", robotId, name);
    }

    private void deleteRobotAndSessions(RobotChatter robot) {
        // 删除机器人关联的所有session
        sessionService.closeSessions(robot.getId());
        // 删除机器人
        chatterFactory.remove(robot);
        log.info("删除ACP机器人及关联会话: id={}, name={}", robot.getId(), robot.getName());
    }

    /**
     * 生成机器人id，空格（含中文全角空格）替换为下划线
     */
    private String buildRobotId(String name) {
        return ACP_ROBOT_ID_PREFIX + name.replaceAll("[\\s\u3000]+", "_");
    }

    /**
     * 判断两个visibleChatterIds是否匹配
     */
    private boolean matchVisibleChatterIds(Set<String> existing, Set<String> target) {
        if (CollectionUtils.isEmpty(existing) && CollectionUtils.isEmpty(target)) {
            return true;
        }
        if (CollectionUtils.isEmpty(existing) || CollectionUtils.isEmpty(target)) {
            return false;
        }
        return existing.equals(target);
    }

    @Data
    public static class AcpRobotParam {
        private String name;
        private String signature;
        private String avatar;
    }
}
