package com.mola.molachat.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午3:13
 * @Version 1.0
 * 获取全局的spring容器
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MyApplicationContextAware implements ApplicationContextAware {

    private ApplicationContext myApplicationContext;

    static class Singleton{
        private static MyApplicationContextAware myApplicationContextAware = new MyApplicationContextAware();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Singleton.myApplicationContextAware.myApplicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext(){
        ApplicationContext myApplicationContext = Singleton.myApplicationContextAware.myApplicationContext;
        Assert.notNull(myApplicationContext, "myApplicationContext is null");
        return myApplicationContext;
    }
}
