package com.mola.molachat.aspect;

import com.mola.molachat.annotation.AddPoint;
import com.mola.molachat.enumeration.ChatterPointEnum;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.utils.AopUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-01 12:53
 **/
//@Component
@Aspect
@Slf4j
@Deprecated
public class AddPointAspect {

    @Autowired
    private ChatterService chatterService;

    @Pointcut("@annotation(com.mola.molachat.annotation.AddPoint)")
    public void pointcut() {}

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) {

        Signature signature = joinPoint.getSignature();
        Object obj = null;
        try {
            obj = joinPoint.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        AddPoint annotation = method.getAnnotation(AddPoint.class);
        if (null != annotation) {
            ChatterPointEnum action = annotation.action();
            String key = annotation.key();
            if (key.length() != 0) {
                // 获取el表达式中的
                Object o = AopUtils.finalKeyResolving(key, method, joinPoint.getArgs(), false);
                if (o instanceof String) {
                    String id = (String) o;
                    chatterService.addPoint(id, action.getPoint());
                }
            }
        }
        return obj;
    }
}
