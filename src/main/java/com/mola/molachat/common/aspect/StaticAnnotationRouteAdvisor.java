package com.mola.molachat.common.aspect;

import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.common.annotation.RefreshChatterList;
import com.mola.molachat.common.aspect.exec.AnnotationAdviceExecutor;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-07-23 18:41
 **/
@Component
public class StaticAnnotationRouteAdvisor extends AbstractPointcutAdvisor implements BeanPostProcessor {

    @Resource
    private ApplicationContext applicationContext;

    private static AnnotationRouteInterceptor INTERCEPTOR = new AnnotationRouteInterceptor();

    /**
     * key：注解名称
     * value：执行器list
     */
    private static Map<String, AnnotationAdviceExecutor> EXECUTOR_MAP;

    private static Set<Class<? extends Annotation>> BIND_ANNOTATION_CLAZZ_SET = new HashSet(){{
        add(RefreshChatterList.class);
        add(AddPoint.class);
    }};

    private final StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    for (Annotation annotation : method.getAnnotations()) {
                        if (BIND_ANNOTATION_CLAZZ_SET.contains(annotation.annotationType())) {
                            return true;
                        }
                    }
                    return false;
                }
            };

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return INTERCEPTOR;
    }

    static class AnnotationRouteInterceptor implements MethodInterceptor {

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            for (Annotation annotation : method.getAnnotations()) {
                String key = annotation.annotationType().getName();
                if (EXECUTOR_MAP.containsKey(key)) {
                    return EXECUTOR_MAP.get(key).invoke(invocation);
                }
            }
            throw new RuntimeException("no advice executor found!");
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (null != EXECUTOR_MAP) {
            return bean;
        }
        Map<String, AnnotationAdviceExecutor> beansOfType = applicationContext.getBeansOfType(AnnotationAdviceExecutor.class);
        if (null == beansOfType) {
            return bean;
        }
        if (null == EXECUTOR_MAP) {
            EXECUTOR_MAP = new HashMap<>(beansOfType.size());
        }
        for (AnnotationAdviceExecutor executor : beansOfType.values()) {
            if (null == executor.bindAnnotation()) {
                continue;
            }
            EXECUTOR_MAP.put(executor.bindAnnotation().getName(), executor);
        }
        return bean;
    }
}
