package com.mola.molachat.aspect;

import com.mola.molachat.annotation.RefreshChatterList;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.data.impl.cache.GroupFactory;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午4:06
 * @Version 1.0
 */
//@Component
@Aspect
@Slf4j
@Deprecated
public class RefreshChattersAspect {

    @Resource
    private ServerService serverService;

    @Resource
    private ChatterService chatterService;

    @Pointcut("@annotation(com.mola.molachat.annotation.RefreshChatterList)")
    public void pointCut(){}

    @Around("pointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Exception{

        Object obj = null;
        try {
            obj = joinPoint.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

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
}
