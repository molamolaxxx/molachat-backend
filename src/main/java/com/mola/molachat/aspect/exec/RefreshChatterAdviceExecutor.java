package com.mola.molachat.aspect.exec;

import com.mola.molachat.annotation.RefreshChatterList;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.data.impl.cache.GroupFactory;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-07-23 19:12
 **/
@Component
@Slf4j
public class RefreshChatterAdviceExecutor implements AnnotationAdviceExecutor {

    @Resource
    private ServerService serverService;

    @Resource
    private ChatterService chatterService;

    @Override
    public Object invoke(MethodInvocation invocation) throws Exception {

        Object obj = null;
        try {
            obj = invocation.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        Method method = invocation.getMethod();

        RefreshChatterList annotation = method.getAnnotation(RefreshChatterList.class);
        if (null != annotation){
            log.info("chatterList变动，发送");
            //向客户端发送更新消息
            List<ChatterDTO> chatterList = chatterService.list()
                    .stream()
                    // 过滤逻辑删除的chatter
                    .filter(chatterDTO -> chatterDTO.getStatus() != ChatterStatusEnum.LOGICAL_DELETE.getCode())
                    .collect(Collectors.toList());
            // 按照状态排序
            chatterList.sort((o1, o2) -> {
                Integer s1 = o1.getStatus();
                Integer s2 = o2.getStatus();
                return s1 > s2 ? -1 : 1;
            });
            for (ChatterDTO chatterDTO : chatterList) {
                if (chatterDTO.isRobot()) {
                    chatterDTO.setApiKey(null);
                }
            }
            for (ChatServer server : serverService.list()){
                // 按group成员，对chatterList进行过滤
                ChatterDTO chatter = chatterService.selectById(server.getChatterId());
                // 公共群组，全部输出
                if (StringUtils.isEmpty(chatter.getCurrentGroup()) ||
                        GroupFactory.COMMON_GROUP_ID.equals(chatter.getCurrentGroup())) {
                    server.getSession().sendToClient(WSResponse.list("ok", chatterList));
                }
            }
        }
        return obj;
    }

    @Override
    public Class<? extends Annotation> bindAnnotation() {
        return RefreshChatterList.class;
    }
}
