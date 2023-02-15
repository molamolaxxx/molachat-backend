package com.mola.molachat.utils;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;

import java.util.function.Supplier;

/**
 * @Author: molamola
 * @Date: 19-7-1 下午5:13
 * @Version 1.0
 * bean复制补丁
 */
public class BeanUtilsPlug {

    /**
     * 复制bean返回目标对象
     * @param source
     * @param target
     * @return
     */
    public static Object copyPropertiesReturnTarget(Object source ,Object target){
        BeanUtils.copyProperties(source,target);
        return target;
    }

    /**
     * 复制bean返回原对象
     * @param source
     * @param target
     * @return
     */
    public static Object copyPropertiesReturnSource(Object source ,Object target){
        BeanUtils.copyProperties(source,target);
        return source;
    }


    public static <T> T registerBean(String name, Class<T> clazz, Supplier<T> factoryMethod, ApplicationContext applicationContext) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz, factoryMethod);
        BeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();
        BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return applicationContext.getBean(name, clazz);
    }
}
