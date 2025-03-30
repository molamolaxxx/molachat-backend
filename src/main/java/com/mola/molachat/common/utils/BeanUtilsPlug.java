package com.mola.molachat.common.utils;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
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

    public static void copyNonNullProperties(Object source, Object target) {
        // 获取源对象所有属性
        PropertyDescriptor[] sourceDescriptors = BeanUtils.getPropertyDescriptors(source.getClass());

        for (PropertyDescriptor sourceDescriptor : sourceDescriptors) {
            try {
                // 跳过无读方法或不可读的属性
                if (sourceDescriptor.getReadMethod() == null) continue;
                Object value = sourceDescriptor.getReadMethod().invoke(source);

                // 仅当值非null时，拷贝到目标对象
                if (value != null) {
                    // 获取目标对象对应的属性
                    PropertyDescriptor targetDescriptor = BeanUtils.getPropertyDescriptor(target.getClass(), sourceDescriptor.getName());
                    if (targetDescriptor != null && targetDescriptor.getWriteMethod() != null) {
                        targetDescriptor.getWriteMethod().invoke(target, value);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("拷贝属性失败", e);
            }
        }
    }
}
