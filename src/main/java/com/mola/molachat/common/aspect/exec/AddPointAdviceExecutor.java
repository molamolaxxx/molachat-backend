package com.mola.molachat.common.aspect.exec;

import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.utils.AopUtils;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-07-23 18:58
 **/
@Component
public class AddPointAdviceExecutor implements AnnotationAdviceExecutor {

    @Resource
    private ChatterService chatterService;

    @Override
    public Object invoke(MethodInvocation invocation) {
        Object obj = null;
        try {
            obj = invocation.proceed();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
        Method method = invocation.getMethod();
        AddPoint annotation = method.getAnnotation(AddPoint.class);
        if (null != annotation) {
            ChatterPointEnum action = annotation.action();
            String key = annotation.key();
            if (key.length() != 0) {
                // 获取el表达式中的
                Object o = AopUtils.finalKeyResolving(key, method, invocation.getArguments(), false);
                if (o instanceof String) {
                    String id = (String) o;
                    chatterService.addPoint(id, action.getPoint());
                }
            }
        }
        return obj;
    }

    @Override
    public Class<? extends Annotation> bindAnnotation() {
        return AddPoint.class;
    }
}
