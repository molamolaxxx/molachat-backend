package com.mola.molachat.config;

import com.mola.molachat.service.http.HttpService;
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
        HttpService.INSTANCE.init(appConfig.getHttpProxy());
    }
}
