package com.mola.molachat.condition;

import com.mola.molachat.common.ConditionClassCache;
import com.mola.molachat.data.impl.cache.ChatterFactory;
import com.mola.molachat.data.impl.cache.OtherData;
import com.mola.molachat.data.impl.cache.ServerFactory;
import com.mola.molachat.data.impl.cache.SessionFactory;
import com.mola.molachat.utils.EnvUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 基本条件
 * @date : 2020-06-14 00:22
 **/
@Component
public class BaseExistCondition implements Condition {

    static final String CACHE_TYPE_KEY = "self-conf.cache-type";

    @Override
    public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        String cacheType = EnvUtils.getEnvProperty(conditionContext, CACHE_TYPE_KEY);
        if (ConditionClassCache.getCache(this.getClass()).equals(cacheType)) {
            return true;
        }
        return false;
    }

    // 如果条件失败：如redis无法连接，则加载cache作为factory
    protected void loadDefaultCondition(ConditionContext conditionContext) {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)
                conditionContext.getBeanFactory();
        // 动态载入bean
        createDefaultDataSupport(beanFactory, "chatterFactory",
                ChatterFactory.class,
                bdb -> {
                    bdb.addPropertyReference("selfConfig","selfConfig");
                    return bdb;
                });
        createDefaultDataSupport(beanFactory, "sessionFactory",
                SessionFactory.class,
                bdb -> {
                    bdb.addPropertyReference("selfConfig","selfConfig");
                    bdb.addPropertyReference("chatterFactory","chatterFactory");
                    return bdb;
                });
        createDefaultDataSupport(beanFactory, "serverFactory",
                ServerFactory.class, bdb -> bdb);

        createDefaultDataSupport(beanFactory, "otherData",
                OtherData.class, bdb -> bdb);
    }

    /**
     * 动态初始化bean名
     * @param beanName
     */
    private void createDefaultDataSupport(DefaultListableBeanFactory beanFactory,
                                          String beanName,
                                          Class target,
                                          Function<BeanDefinitionBuilder, BeanDefinitionBuilder> function) {
        if (!beanFactory.containsBean(beanName)) {
            // 通过BeanDefinitionBuilder创建bean定义
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(target);
            // 设置属性,不同factory不同属性，使用callback进行回调
            beanDefinitionBuilder = function.apply(beanDefinitionBuilder);
            beanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
        }
    }
}
