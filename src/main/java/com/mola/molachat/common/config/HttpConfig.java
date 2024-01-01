package com.mola.molachat.common.config;

import com.mola.molachat.common.utils.HttpUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-02 17:57
 **/
@Configuration
public class HttpConfig implements InitializingBean {

    @Resource
    private AppConfig appConfig;

    @Override
    public void afterPropertiesSet() throws Exception {
        HttpUtil.PROXY.init(appConfig.getHttpProxy());
        HttpUtil.INSTANCE.init(null);
    }
}
