package com.mola.molachat.utils;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-06-14 09:16
 **/
public class EnvUtils {
    public static String getEnvProperty(ConditionContext conditionContext, String key) {
        String property = conditionContext.getBeanFactory().getBean(Environment.class)
                .getProperty(key);
        return property;
    }
}
