package com.mola.molachat.aspect.exec;

import org.aopalliance.intercept.MethodInvocation;

import java.lang.annotation.Annotation;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午4:06
 * 静态注解advice执行器
 * @Version 1.0
 */
public interface AnnotationAdviceExecutor {

    /**
     * 方法around
     * @param invocation
     * @return
     */
    Object invoke(MethodInvocation invocation) throws Exception ;

    /**
     * 绑定的注解
     * @return
     */
    Class<? extends Annotation> bindAnnotation();
}
