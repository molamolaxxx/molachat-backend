package com.mola.molachat.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午3:13
 * @Version 1.0
 * 获取全局的spring容器
 */
@Component
@Slf4j
public class MyApplicationContextAware implements ApplicationContextAware{

    private static ApplicationContext applicationContextStatic;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        applicationContextStatic = applicationContext;
    }

    public static ApplicationContext getApplicationContext(){
        return applicationContextStatic;
    }
}
